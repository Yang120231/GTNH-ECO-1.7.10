package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.compat.proghatches.ProgrammableHatchCrafting;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/**
 * Tries providers in the already-selected order. This is the boundary between task scheduling and provider-level
 * dispatch. Every selected lane plans and commits through execution.batch; this class only selects adapters
 * and applies the CPU ledger after acceptance. It does not own material or energy transactions.
 */
final class ECOCraftingProviderDispatcher {

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
        int coProcessors = owner == null || owner.cpu == null ? 0 : owner.cpu.getCoProcessors();
        processing.beginTick(gameTick, coProcessors);
    }

    void reset() {
        processing.reset();
    }

    boolean isEligible(ICraftingProvider provider, IPatternDetails pattern, ECOCraftingDispatchBudget budget) {
        if (NativeProviderAdapters.isEnabled(provider))
            return budget.canAttemptOrdinary() || processing.mayAttemptScaled(provider, pattern);
        // The ordinary one-copy budget must not hide a target whose batch lane still has submissions left.
        return budget.canAttemptOrdinary() || fastPath.supportsBatch(provider)
            || ECOParallelCraftingProviders.find(provider) != null
            || processing.mayAttemptScaled(provider, pattern);
    }

    /**
     * Smart doubling off: ordinary dispatch is capped at the host's actual parallelism (at most 16,384).
     * Every native medium follows the same limit; successful probes never grant a permanent capacity exemption.
     */
    long dispatchLimit() {
        if (owner == null || owner.cpu == null) return Long.MAX_VALUE;
        var host = owner.cpu.getCluster();
        var controller = host instanceof cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster
            ? ((cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster) host).getController()
            : null;
        return controller == null ? Long.MAX_VALUE
            : cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity
                .smartDoublingDispatchLimit(controller.isNetworkSmartDoublingEnabled(), owner.cpu.getCoProcessors());
    }

    private static ECOCraftingDispatchRequest withAllowedCrafts(ECOCraftingDispatchRequest request,
        long allowedCrafts) {
        if (request.allowedCrafts() == allowedCrafts) return request;
        return new ECOCraftingDispatchRequest(
            request.job(),
            request.candidate(),
            request.pattern(),
            request.inputs(),
            request.outputs(),
            request.remainders(),
            allowedCrafts,
            request.inventory(),
            request.level());
    }

    private static long liveTaskRemaining(ECOCraftingDispatchRequest request) {
        var progress = request.job().tasks.get(request.pattern());
        return progress == null ? 0L : Math.max(0L, progress.value);
    }

    Result dispatchCandidate(ECOCraftingDispatchRequest originalRequest, ECOProviderCursor.Traversal traversal,
        ECOCraftingDispatchBudget budget, IEnergyGrid energyService, Consumer<ICraftingProvider> markProviderAttempt,
        Runnable markNormalResume, ECOCraftingNormalPush normalPush) {
        Iterator<ICraftingProvider> providers = traversal;
        if (processing.isInstantCircuitRequest(originalRequest)) {
            var fallback = new ArrayList<ICraftingProvider>();
            while (providers.hasNext()) {
                var provider = providers.next();
                long accepted = processing
                    .tryInstantCircuitDispatch(originalRequest, provider, energyService, markProviderAttempt);
                if (accepted > 0) return Result.accepted(accepted, true);
                if (originalRequest.job().suspended) return Result.none();
                fallback.add(provider);
            }
            providers = fallback.iterator();
        }
        long cappedLimit = dispatchLimit();
        var request = withAllowedCrafts(originalRequest, Math.min(originalRequest.allowedCrafts(), cappedLimit));
        double singlePower = CraftingCpuHelper.calculatePatternPower(originalRequest.inputs());

        long remainingCrafts = request.allowedCrafts();
        long totalAccepted = 0L;
        while (true) {
            remainingCrafts = Math.min(remainingCrafts, liveTaskRemaining(request));
            if (remainingCrafts <= 0L || request.job().suspended
                || owner.getJob() != request.job()
                || !providers.hasNext()) break;
            var provider = providers.next();
            var liveRequest = withAllowedCrafts(request, remainingCrafts);
            boolean blocking = NativeProviderAdapters.isEnabled(provider);
            if (totalAccepted == 0L) {
                if (!blocking && liveRequest.job().exactOrder) {
                    var exact = cappedLimit == Long.MAX_VALUE
                        ? fastPath
                            .tryExactDispatch(liveRequest, provider, singlePower, energyService, markProviderAttempt)
                        : fastPath.tryExactDispatch(
                            liveRequest,
                            provider,
                            singlePower,
                            energyService,
                            markProviderAttempt,
                            cappedLimit);
                    if (exact != null) return Result.accepted(
                        cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(exact),
                        true);
                    if (liveRequest.job().suspended) return Result.none();
                }
                long counted = processing
                    .tryCountedDispatch(liveRequest, provider, singlePower, energyService, markProviderAttempt);
                if (counted > 0) {
                    liveRequest.job().singleProcessingReason = "";
                    return Result.accepted(counted, true);
                }
                if (liveRequest.job().suspended) return Result.none();
            }

            // Blocking media join the scaled lane too; eligibility still excludes known crafting locks.
            boolean scaledProvider = processing.supportsScaledDispatchCached(liveRequest, provider);
            if (scaledProvider) {
                int submissions = processing.remainingScaledAttempts(liveRequest.pattern());
                if (submissions == 0) {
                    // Exhausted submissions pause this pattern until the next tick; the provider did not fail.
                    continue;
                }
                if (processing.isScaledFallbackDeferred(liveRequest, provider)) continue;
                if (!processing.isScaledDispatchDeferred(liveRequest, provider)) {
                    var planning = ECOBatchDispatchPlanning.prepare(liveRequest, provider);
                    long dispatchable = remainingCrafts;
                    if (dispatchable > 1 && traversal.hasUninspectedProviders()) {
                        // Split the stock and power actually available now, not the unfilled order. Do not apply
                        // this provider's transport cap to the whole pool; every chunk checks its own limits.
                        var supply = planning
                            .plan(Long.MAX_VALUE, dispatchable, singlePower, energyService, ECOBatchMode.LINEAR);
                        dispatchable = supply == null ? 0L : supply.craftCount();
                    }
                    // Do not turn a legal multi-copy submission into forbidden singles, or reserve shares for
                    // providers that cannot receive a submission this tick.
                    long maxReceivers = Math
                        .min(submissions, budget.canAttemptOrdinary() ? dispatchable : dispatchable / 2);
                    long receivers = traversal
                        .sharingProviders(maxReceivers, peer -> processing.canVisitScaled(liveRequest, peer));
                    long visitShare = JdkCompat.ceilDiv(dispatchable, receivers);
                    var scaledProcessingResult = processing.tryScaledDispatch(
                        liveRequest,
                        provider,
                        planning,
                        visitShare,
                        budget,
                        singlePower,
                        energyService,
                        markProviderAttempt,
                        normalPush);
                    if (scaledProcessingResult != null && scaledProcessingResult.acceptedCrafts() > 0L) {
                        liveRequest.job().singleProcessingReason = "";
                        long accepted = scaledProcessingResult.acceptedCrafts();
                        totalAccepted += accepted;
                        remainingCrafts = dispatchable - accepted;
                        continue;
                    }
                    if (liveRequest.job().suspended || owner.getJob() != liveRequest.job()) {
                        break;
                    }
                    // Refused because the target is occupied or locked: a one-copy push would be refused too.
                    if (blocking && processing.isScaledFallbackDeferred(liveRequest, provider)) continue;
                }
                // A scaled adapter is an optimization boundary. Its eligibility can be
                // valid while the live target rejects the current offer (stale target,
                // backpressure, or a version-specific transport mismatch). Do not let
                // that rejection suppress AE2's ordinary one-copy transaction; otherwise
                // an overloaded provider becomes completely undispatchable.
            }

            if (totalAccepted > 0L) {
                continue;
            }

            var fastResult = blocking ? null
                : fastPath.tryDispatch(liveRequest, provider, singlePower, energyService, markProviderAttempt);
            if (fastResult != null) {
                return Result.accepted(fastResult.acceptedCrafts(), true);
            }
            if (liveRequest.job().suspended) return Result.none();

            var parallelResult = blocking ? null
                : tryDispatchOrdinaryBatch(
                    liveRequest,
                    provider,
                    singlePower,
                    energyService,
                    budget,
                    markProviderAttempt,
                    markNormalResume);
            if (parallelResult != null) {
                return parallelResult;
            }

            // A batch is an optional optimization. If it is unavailable, rejected, or dynamically ambiguous,
            // the same provider still receives the normal one-copy fallback.
            if (!budget.canAttemptOrdinary()) {
                continue;
            }

            var plan = ECOBatchDispatchPlanning
                .plan(liveRequest, provider, 1, 1, singlePower, energyService, ECOBatchMode.SINGLE);
            if (plan == null) continue;

            if (scaledProvider && !processing.claimFallbackAttempt(liveRequest.pattern())) {
                continue;
            }
            ECOBatchAdmission admission;
            try {
                admission = ECOBatchExecutor.execute(
                    plan,
                    liveRequest.inputs(),
                    liveRequest.outputs(),
                    liveRequest.remainders(),
                    liveRequest.inventory(),
                    liveRequest.level(),
                    liveRequest.job().link.getCraftingID(),
                    () -> energyTransaction.reserve(energyService, singlePower, plan.craftCount()),
                    batch -> {
                        markProviderAttempt.accept(provider);
                        budget.recordNormalProbe();
                        markNormalResume.run();
                        var single = new ECOCraftingDispatchRequest(
                            liveRequest.job(),
                            liveRequest.candidate(),
                            batch.identity()
                                .originalPattern(),
                            batch.inputCounters(),
                            batch.outputCounter(),
                            batch.remainderCounter(),
                            batch.craftCount(),
                            liveRequest.inventory(),
                            liveRequest.level());
                        return normalPush.push(single, provider) ? ECOBatchAdmission.accepted(1, false)
                            : ECOBatchAdmission.rejected();
                    });
            } catch (ECOIndeterminateBatchException failure) {
                failAmbiguousDispatch(liveRequest, "ordinary");
                return Result.none();
            } catch (RuntimeException failure) {
                accounting.failPermanently(liveRequest, "ORDINARY_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                continue;
            }
            try {

                accounting.apply(
                    liveRequest,
                    ECOCraftingDispatchResult.single(liveRequest.outputs(), liveRequest.remainders()),
                    budget::recordAcceptedNormalPush,
                    provider);
            } catch (RuntimeException failure) {
                accounting.failPermanently(liveRequest, "POST_ACCEPT_ORDINARY_ACCOUNTING_FAILURE");
                throw failure;
            }
            if (provider instanceof NativeCraftingProvider nativeProvider) {
                ProgrammableHatchCrafting.completeAcceptedPush(nativeProvider.nativeMedium());
                if (liveRequest.pattern() instanceof NativePatternDetails nativePattern
                    && !nativePattern.nativeDetails()
                        .isCraftable()) {
                    String reason = nativeProvider.singleCopyReason();
                    liveRequest.job().singleProcessingReason = reason == null ? "single_copy" : reason;
                }
            }
            return Result.accepted(1L, false);
        }

        return totalAccepted > 0L ? Result.accepted(totalAccepted, true) : Result.none();
    }

    /**
     * Ordinary-path batch dispatch for providers that explicitly own a parallel queue. This does not use the
     * FastPath cache or any FastPath recipe proof; the provider validates the complete scaled input contract.
     */
    @Nullable
    private Result tryDispatchOrdinaryBatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        double singlePower, IEnergyGrid energyService, ECOCraftingDispatchBudget budget,
        Consumer<ICraftingProvider> markProviderAttempt, Runnable markNormalResume) {
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
        boolean single = plan.craftCount() == 1;
        if (single && !budget.canAttemptOrdinary()) return null;
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
                    if (single) budget.recordNormalProbe();
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
            failAmbiguousDispatch(request, "parallel");
            return Result.none();
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "PARALLEL_BATCH_SETTLEMENT_FAILURE");
            throw failure;
        }
        if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
            return null;
        }
        long accepted = admission.acceptedCrafts();
        var result = ECOCraftingDispatchResult.batch(
            accepted,
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), accepted),
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), accepted));
        try {
            accounting.apply(request, result, () -> { if (single) budget.recordAcceptedNormalPush(); }, provider);
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "POST_ACCEPT_PARALLEL_ACCOUNTING_FAILURE");
            throw failure;
        }
        return Result.accepted(accepted, false);
    }

    private void failAmbiguousDispatch(ECOCraftingDispatchRequest request, String path) {
        String reason = "AMBIGUOUS_" + path.toUpperCase(java.util.Locale.ROOT) + "_PROVIDER_OWNERSHIP";
        accounting.failPermanently(request, reason);
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
