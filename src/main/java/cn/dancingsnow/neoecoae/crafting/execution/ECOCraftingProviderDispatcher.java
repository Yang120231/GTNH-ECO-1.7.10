package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters;
import cn.dancingsnow.neoecoae.platform.minecraft.TickClock;

/**
 * Tries providers in the already-selected order. This is the boundary between task scheduling and provider-level
 * dispatch. Every selected lane plans and commits through execution.batch; this class only selects adapters
 * and applies the CPU ledger after acceptance. It does not own material or energy transactions.
 */
final class ECOCraftingProviderDispatcher {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae.dispatch");
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingFastPathDispatcher fastPath;
    private final ECOProcessingPatternDispatcher processing;
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;

    ECOCraftingProviderDispatcher(ECOCraftingCPULogic owner, ECOCraftingFastPathDispatcher fastPath,
        ECOCraftingEnergyTransaction energyTransaction, ECOCraftingDispatchAccounting accounting) {
        this.owner = owner;
        this.fastPath = fastPath;
        this.processing = new ECOProcessingPatternDispatcher(owner, energyTransaction, accounting);
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    void beginTick(long gameTick) {
        processing.beginTick(gameTick);
    }

    void reset() {
        processing.reset();
    }

    boolean isEligible(ICraftingProvider provider, IPatternDetails pattern, ECOCraftingDispatchBudget budget) {
        if (NativeProviderAdapters.isEnabled(provider)) return budget.canAttemptOrdinary();
        // The ordinary one-copy budget must not hide a target whose batch lane still has submissions left.
        return budget.canAttemptOrdinary() || fastPath.supportsBatch(provider)
            || ECOParallelCraftingProviders.find(provider) != null
            || processing.mayAttemptScaled(provider, pattern);
    }

    /** Smart doubling off: ordinary dispatch is capped at the host's actual parallelism (at most 16,384). */
    private long dispatchLimit() {
        if (owner == null || owner.cpu == null) return Long.MAX_VALUE;
        var host = owner.cpu.getCluster();
        var controller = host instanceof cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster
            ? ((cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster) host).getController()
            : null;
        return controller == null ? Long.MAX_VALUE
            : cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity
                .smartDoublingDispatchLimit(controller.isSmartDoublingEnabled(), owner.cpu.getCoProcessors());
    }

    Result dispatchCandidate(ECOCraftingDispatchRequest originalRequest, List<ICraftingProvider> providers,
        ECOCraftingDispatchBudget budget, IEnergyGrid energyService, ECODispatchStallDiagnostics diagnostics,
        Consumer<ICraftingProvider> markProviderAttempt, Runnable markNormalResume, ECOCraftingNormalPush normalPush) {
        long limit = dispatchLimit();
        var request = originalRequest.allowedCrafts() <= limit ? originalRequest
            : new ECOCraftingDispatchRequest(
                originalRequest.job(),
                originalRequest.candidate(),
                originalRequest.pattern(),
                originalRequest.inputs(),
                originalRequest.outputs(),
                originalRequest.remainders(),
                limit,
                originalRequest.inventory(),
                originalRequest.level());
        double singlePower = CraftingCpuHelper.calculatePatternPower(request.inputs());

        for (var provider : providers) {

            boolean blocking = NativeProviderAdapters.isEnabled(provider);
            if (!blocking && request.job().exactOrder) {
                var exact = limit == Long.MAX_VALUE
                    ? fastPath.tryExactDispatch(request, provider, singlePower, energyService, markProviderAttempt)
                    : fastPath
                        .tryExactDispatch(request, provider, singlePower, energyService, markProviderAttempt, limit);
                if (exact != null) return Result
                    .accepted(cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(exact), true);
                if (request.job().suspended) return Result.none();
            }
            boolean scaledProvider = !blocking && processing.supportsScaledDispatchCached(request, provider);
            if (scaledProvider) {
                if (processing.isScaledAttemptBudgetExhausted(request.pattern())) {
                    // Exhausted submissions pause this pattern until the next tick; the provider did not fail.
                    diagnostics.budget();
                    continue;
                }
                if (processing.isScaledFallbackDeferred(request, provider)) continue;
                if (!processing.isScaledDispatchDeferred(request, provider)) {
                    var scaledProcessingResult = processing.tryScaledDispatch(
                        request,
                        provider,
                        singlePower,
                        energyService,
                        markProviderAttempt,
                        normalPush);
                    if (scaledProcessingResult != null) {
                        return Result.accepted(scaledProcessingResult.acceptedCrafts(), true);
                    }
                    if (request.job().suspended) return Result.none();
                }
                // A scaled adapter is an optimization boundary. Its eligibility can be
                // valid while the live target rejects the current offer (stale target,
                // backpressure, or a version-specific transport mismatch). Do not let
                // that rejection suppress AE2's ordinary one-copy transaction; otherwise
                // an overloaded provider becomes completely undispatchable.
            }

            var fastResult = blocking ? null
                : fastPath.tryDispatch(request, provider, singlePower, energyService, diagnostics, markProviderAttempt);
            if (fastResult != null) {
                return Result.accepted(fastResult.acceptedCrafts(), true);
            }
            if (request.job().suspended) return Result.none();

            var parallelResult = blocking ? null
                : tryDispatchOrdinaryBatch(
                    request,
                    provider,
                    singlePower,
                    energyService,
                    budget,
                    diagnostics,
                    markProviderAttempt,
                    markNormalResume);
            if (parallelResult != null) {
                return parallelResult;
            }

            // A batch is an optional optimization. If it is unavailable, rejected, or dynamically ambiguous,
            // the same provider still receives the normal one-copy fallback.
            if (!budget.canAttemptOrdinary()) {
                diagnostics.budget();
                continue;
            }

            var plan = ECOBatchDispatchPlanning
                .plan(request, provider, 1, 1, singlePower, energyService, ECOBatchMode.SINGLE);
            if (plan == null) continue;

            if (scaledProvider && !processing.claimFallbackAttempt(request.pattern())) {
                diagnostics.budget();
                continue;
            }
            ECOBatchAdmission admission;
            try {
                admission = ECOBatchExecutor.execute(
                    plan,
                    request.inputs(),
                    request.outputs(),
                    request.remainders(),
                    request.inventory(),
                    request.level(),
                    request.job().link.getCraftingID(),
                    () -> energyTransaction.reserve(energyService, singlePower, plan.craftCount()),
                    batch -> {
                        markProviderAttempt.accept(provider);
                        budget.recordNormalProbe();
                        diagnostics.probe();
                        markNormalResume.run();
                        if (diagnostics.isActive()) clearProviderDiagnostics(provider);
                        var single = new ECOCraftingDispatchRequest(
                            request.job(),
                            request.candidate(),
                            batch.identity()
                                .originalPattern(),
                            batch.inputCounters(),
                            batch.outputCounter(),
                            batch.remainderCounter(),
                            batch.craftCount(),
                            request.inventory(),
                            request.level());
                        return normalPush.push(single, provider) ? ECOBatchAdmission.accepted(1, false)
                            : ECOBatchAdmission.rejected();
                    });
            } catch (ECOIndeterminateBatchException failure) {
                failAmbiguousDispatch(request, provider, "ordinary", failure);
                return Result.none();
            } catch (RuntimeException failure) {
                request.job()
                    .failPermanently("ORDINARY_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                diagnostics.pushRejected(request.pattern(), provider);
                continue;
            }
            try {

                accounting.apply(
                    request,
                    ECOCraftingDispatchResult.single(request.outputs(), request.remainders()),
                    budget::recordAcceptedNormalPush,
                    provider);
            } catch (RuntimeException failure) {
                request.job()
                    .failPermanently("POST_ACCEPT_ORDINARY_ACCOUNTING_FAILURE");
                throw failure;
            }
            diagnostics.progress(TickClock.currentTick());
            return Result.accepted(1L, false);
        }

        return Result.none();
    }

    /**
     * Ordinary-path batch dispatch for providers that explicitly own a parallel queue. This does not use the
     * FastPath cache or any FastPath recipe proof; the provider validates the complete scaled input contract.
     */
    @Nullable
    private Result tryDispatchOrdinaryBatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        double singlePower, IEnergyGrid energyService, ECOCraftingDispatchBudget budget,
        ECODispatchStallDiagnostics diagnostics, Consumer<ICraftingProvider> markProviderAttempt,
        Runnable markNormalResume) {
        var parallelProvider = ECOParallelCraftingProviders.find(provider);
        if (parallelProvider == null) {
            return null;
        }

        int providerCapacity;
        try {
            providerCapacity = parallelProvider.eco$getAvailableParallelSlots();
        } catch (RuntimeException unavailable) {
            return null;
        }
        var plan = ECOBatchDispatchPlanning
            .plan(request, provider, providerCapacity, Long.MAX_VALUE, singlePower, energyService, ECOBatchMode.LINEAR);
        if (plan == null) return null;
        ECOBatchAdmission admission;
        try {
            admission = ECOBatchExecutor.execute(
                plan,
                request.inputs(),
                request.outputs(),
                request.remainders(),
                request.inventory(),
                request.level(),
                request.job().link.getCraftingID(),
                () -> energyTransaction.reserve(energyService, singlePower, plan.craftCount()),
                batch -> {
                    markProviderAttempt.accept(provider);
                    budget.recordNormalProbe();
                    diagnostics.probe();
                    markNormalResume.run();
                    return parallelProvider.eco$pushPatternBatch(
                        batch.identity()
                            .originalPattern(),
                        batch.inputCounters(),
                        batch.craftCount(),
                        batch.jobId()) ? ECOBatchAdmission.accepted(batch.craftCount(), false)
                            : ECOBatchAdmission.rejected();
                });
        } catch (ECOIndeterminateBatchException failure) {
            failAmbiguousDispatch(request, provider, "parallel", failure);
            return Result.none();
        } catch (RuntimeException failure) {
            request.job()
                .failPermanently("PARALLEL_BATCH_SETTLEMENT_FAILURE");
            throw failure;
        }
        if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
            diagnostics.pushRejected(request.pattern(), provider);
            return null;
        }
        long accepted = admission.acceptedCrafts();
        var result = ECOCraftingDispatchResult.batch(
            accepted,
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), accepted),
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), accepted));
        try {
            accounting.apply(request, result, budget::recordAcceptedNormalPush, provider);
        } catch (RuntimeException failure) {
            request.job()
                .failPermanently("POST_ACCEPT_PARALLEL_ACCOUNTING_FAILURE");
            throw failure;
        }
        diagnostics.progress(TickClock.currentTick());
        return Result.accepted(accepted, false);
    }

    private static void failAmbiguousDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        String path, RuntimeException failure) {
        String reason = "AMBIGUOUS_" + path.toUpperCase(java.util.Locale.ROOT) + "_PROVIDER_OWNERSHIP";
        request.job()
            .failPermanently(reason);
        LOGGER.error(
            "ECO {} provider {} threw after dispatch began; the job is suspended without replaying inputs",
            path,
            provider.getClass()
                .getName(),
            failure);
    }

    private static void clearProviderDiagnostics(ICraftingProvider provider) {
        if (provider instanceof ECOPatternPushDiagnostics diagnostics) {
            try {
                diagnostics.neoecoae$clearPushDiagnostics();
            } catch (RuntimeException ignored) {
                // Observability must not prevent dispatch or interfere with ownership transfer.
            }
        }
    }

    @FunctionalInterface
    interface ECOCraftingNormalPush {

        boolean push(ECOCraftingDispatchRequest request, ICraftingProvider provider);
    }

    @com.github.bsideup.jabel.Desugar
    record Result(long acceptedCrafts, boolean accepted, boolean fastPath) {

        static Result accepted(long count, boolean fastPath) {
            return new Result(count, true, fastPath);
        }

        static Result none() {
            return new Result(0L, false, false);
        }
    }
}
