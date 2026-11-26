package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.compat.proghatches.ProgrammableHatchCrafting;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters;

/**
 * ECO owns processing batch growth; optional adapters only transport the selected batch.
 *
 * <p>
 * The per-tick budget counts provider submissions, never crafts: one push carrying a billion copies costs one
 * attempt. Batch size comes from the task, materials, energy, cycle seeds, the target's capacity and numeric bounds.
 */
final class ECOProcessingPatternDispatcher {

    private static final long ELIGIBILITY_CACHE_TICKS = 10L;
    private static final long SCALE_PROBE_INTERVAL_TICKS = 2L;
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;

    ECOProcessingPatternDispatcher(ECOCraftingCPULogic owner, ECOCraftingEnergyTransaction energy,
        ECOCraftingDispatchAccounting accounting) {
        this.owner = owner;
        this.energy = energy;
        this.accounting = accounting;
    }

    /** The two native batch lanes are independent of the allowance for ordinary calls. */
    boolean mayAttemptScaled(ICraftingProvider provider, IPatternDetails pattern, ECOCraftingDispatchBudget budget) {
        return provider instanceof NativeCraftingProvider nativeProvider && (budget.remainingScaled() > 0
            && (nativeProvider.supportsCountedBatch(pattern) || nativeProvider.supportsScaledProcessing())
            || nativeProvider.canDeliverCircuitInstantly(pattern));
    }

    boolean canVisitScaled(ECOCraftingDispatchRequest request, ICraftingProvider provider, ProbeState state,
        ECOCraftingDispatchBudget budget) {
        return budget.remainingScaled() > 0 && budget.tick() >= state.nextFallbackTick
            && budget.tick() >= state.nextDispatchTick
            && supportsScaledDispatchCached(request, provider, state, budget.tick());
    }

    boolean supportsScaledDispatchCached(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        ProbeState state, long tick) {
        // Only pattern facts are cached. Target faces, locks and optimization opt-outs are always live.
        if (!(provider instanceof NativeCraftingProvider) || !scaledTransportAllowed(provider)) return false;
        if (state.eligibility == null || tick - state.eligibility.tick() >= ELIGIBILITY_CACHE_TICKS)
            state.eligibility = new Eligibility(tick, isScalablePattern(request.pattern()));
        return state.eligibility.supported();
    }

    boolean isInstantCircuitRequest(ECOCraftingDispatchRequest request) {
        return request.inputs().length == 0 && request.outputs()
            .size() == 1
            && request.remainders()
                .isEmpty()
            && request.pattern() instanceof NativePatternDetails nativePattern
            && ProgrammableHatchCrafting.isCircuitPattern(nativePattern.nativeDetails())
            && request.job().finalOutput != null
            && !request.outputs()
                .iterator()
                .next()
                .getKey()
                .matches(request.job().finalOutput);
    }

    long tryInstantCircuitDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider, IEnergyGrid service,
        Consumer<ICraftingProvider> mark) {
        if (!(provider instanceof NativeCraftingProvider nativeProvider)
            || !nativeProvider.canDeliverCircuitInstantly(request.pattern())) return 0;
        var output = request.outputs()
            .iterator()
            .next();
        var key = output.getKey();
        long perCraft = output.getLongValue();
        if (perCraft <= 0) return 0;
        long capacity = request.job().exactOrder ? Long.MAX_VALUE
            : (Long.MAX_VALUE - request.inventory().list.get(key)) / perCraft;
        var batch = ECOBatchDispatchPlanning
            .plan(request, provider, capacity, Long.MAX_VALUE, 0, service, ECOBatchMode.LINEAR);
        if (batch == null) return 0;
        long count = batch.craftCount();
        long amount = Math.multiplyExact(perCraft, count);
        mark.accept(provider);
        try {
            accounting.apply(request, scaledResult(request, count), () -> {
                if (owner.insertForJob(request.job().link.getCraftingID(), key, amount, Actionable.MODULATE) != amount)
                    throw new IllegalStateException("Instant circuit did not reach its CPU");
            }, provider);
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "INSTANT_CIRCUIT_SETTLEMENT_FAILURE");
            throw failure;
        }
        request.job()
            .clearSingleProcessingReason(request.pattern());
        return count;
    }

    long tryCountedDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider, double onePower,
        IEnergyGrid service, Consumer<ICraftingProvider> mark, ECOCraftingDispatchBudget budget) {
        if (!(provider instanceof NativeCraftingProvider nativeProvider) || budget.remainingScaled() == 0
            || !nativeProvider.supportsCountedBatch(request.pattern())) return 0;
        long accepted;
        try {
            accepted = dispatchCounted(request, nativeProvider, onePower, service, energy, () -> {
                budget.recordScaledProbe();
                mark.accept(provider);
            });
        } catch (ECOIndeterminateBatchException failure) {
            accounting.failPermanently(request, "AMBIGUOUS_COUNTED_PROVIDER_OWNERSHIP");
            return 0;
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "COUNTED_BATCH_SETTLEMENT_FAILURE");
            throw failure;
        }
        if (accepted == 0) return 0;
        try {
            accounting.apply(request, scaledResult(request, accepted), () -> {}, provider);
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "POST_ACCEPT_COUNTED_ACCOUNTING_FAILURE");
            throw failure;
        }
        ProgrammableHatchCrafting.completeAcceptedPush(nativeProvider.nativeMedium());
        return accepted;
    }

    static long dispatchCounted(ECOCraftingDispatchRequest request, NativeCraftingProvider provider, double onePower,
        IEnergyGrid service, ECOCraftingEnergyTransaction energy, Runnable markAttempt) {
        var plan = ECOBatchDispatchPlanning
            .plan(request, provider, Integer.MAX_VALUE, Long.MAX_VALUE, onePower, service, ECOBatchMode.LINEAR);
        if (plan == null || plan.craftCount() <= 1) return 0;
        return ECOBatchExecutor.execute(
            plan,
            request.inputs(),
            request.outputs(),
            request.remainders(),
            request.inventory(),
            request.level(),
            request.job().link.getCraftingID(),
            () -> energy.reserve(service, onePower, plan.craftCount()),
            batch -> {
                markAttempt.run();
                int accepted = provider.pushCountedBatch(request.pattern(), request.inputs(), (int) batch.craftCount());
                return accepted == 0 ? ECOBatchAdmission.rejected() : ECOBatchAdmission.accepted(accepted, false);
            })
            .acceptedCrafts();
    }

    /** Learns from fully inserted chunks; target saturation ends a visit without erasing its successes. */
    @Nullable
    ECOCraftingDispatchResult tryScaledDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        ECOBatchDispatchPlanning.Prepared planning, long limit, ProbeState state, ECOCraftingDispatchBudget budget,
        double onePower, IEnergyGrid service, Consumer<ICraftingProvider> mark,
        ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        if (limit <= 0 || !canVisitScaled(request, provider, state, budget)) return null;
        long tick = budget.tick();
        var nativeProvider = (NativeCraftingProvider) provider;
        // A plain-blocking interface takes exactly one push per empty target: that push carries the learned size.
        boolean singlePush = isSinglePushTarget(provider);
        boolean directBuffer = nativeProvider.hasDirectBatchBuffer();
        long transportLimit = nativeProvider.scaledProcessingCopyLimit(request.inputs());

        var ramp = state.beginRun(tick, SCALE_PROBE_INTERVAL_TICKS, directBuffer, Math.min(limit, transportLimit));
        IPatternDetails cachedExecutionPattern = null;
        long cachedCopies = 0;
        while (ramp.owned < limit && budget.remainingScaled() > 0
            && budget.canStartSubmission()
            && !provider.isBusy()
            && scaledTransportAllowed(provider)) {
            // Probes are real submissions: accepted chunks already own their share of the remaining order.
            long maxOffer = Math.min(ramp.offer(limit - ramp.owned), transportLimit);
            var plan = planning.plan(maxOffer, limit - ramp.owned, onePower, service, ECOBatchMode.LINEAR);
            if (plan == null) {
                state.nextDispatchTick = tick + 1;
                break;
            }
            long offer = plan.craftCount();
            if (offer == 1 && !budget.canAttemptOrdinary()) break;
            if (cachedCopies != offer) {
                cachedExecutionPattern = offer == 1 ? request.pattern()
                    : new ECOProcessingExecutionPattern(request.pattern(), offer);
                cachedCopies = offer;
            }
            IPatternDetails executionPattern = cachedExecutionPattern;
            boolean[] pushed = new boolean[1];
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
                    () -> energy.reserve(service, onePower, plan.craftCount()),
                    batch -> {
                        mark.accept(provider);

                        // Only this external API boundary needs an AE2 pattern-shaped execution view.
                        // The task, plan and accounting retain the original pattern identity.
                        if (executionPattern == null) return ECOBatchAdmission.rejected();
                        var scaledRequest = new ECOCraftingDispatchRequest(
                            request.job(),
                            request.candidate(),
                            executionPattern,
                            batch.inputCounters(),
                            batch.outputCounter(),
                            batch.remainderCounter(),
                            batch.craftCount(),
                            request.inventory(),
                            request.level());
                        budget.recordScaledProbe();
                        if (offer == 1) budget.recordNormalProbe();
                        pushed[0] = true;
                        return normalPush.push(scaledRequest, provider)
                            ? ECOBatchAdmission
                                .accepted(batch.craftCount(), nativeProvider.isScaledProcessingFullyInserted())
                            : ECOBatchAdmission.rejected();
                    });
            } catch (ECOIndeterminateBatchException failure) {
                accounting.failPermanently(request, "AMBIGUOUS_SCALED_PROVIDER_OWNERSHIP");
                break;
            } catch (RuntimeException failure) {
                accounting.failPermanently(request, "SCALED_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                if (!pushed[0]) break; // Local reservation/materialization failed: no receiver feedback.
                if (temporarilyUnavailable(provider)) {
                    // Occupied or locked target: retry next tick with the same size; the one-copy fallback would
                    // be refused for the same reason.
                    state.nextDispatchTick = state.nextFallbackTick = tick + 1;
                    break;
                }
                if (ramp.record(offer, 0, false, tick)) continue;
                break;
            }
            try {
                accounting.apply(request, scaledResult(request, admission.acceptedCrafts()), () -> {}, provider);
            } catch (RuntimeException failure) {
                accounting.failPermanently(request, "POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
                throw failure;
            }
            if (!ramp.record(offer, admission.acceptedCrafts(), admission.canContinue(), tick)) break;
            // Once the offer stops growing, rotate instead of spending the rest of the tick on this receiver.
            if (ramp.next <= offer || offer < maxOffer
                || singlePush
                || request.job().suspended
                || owner.getJob() != request.job()) break;
        }
        return ramp.owned > 0 ? scaledResult(request, ramp.owned) : null;
    }

    private static ECOCraftingDispatchResult scaledResult(ECOCraftingDispatchRequest request, long copies) {
        return ECOCraftingDispatchResult.batch(
            copies,
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), copies),
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), copies));
    }

    /** Blocking providers retain their own acceptance checks while ECO sizes each push. */
    static boolean scaledTransportAllowed(ICraftingProvider provider) {
        if (provider instanceof NativeCraftingProvider nativeProvider)
            return nativeProvider.allowsScaledWhileBlocking() && ECOParallelCraftingProviders.find(provider) == null;
        return !NativeProviderAdapters.isEnabled(provider);
    }

    private static boolean isSinglePushTarget(ICraftingProvider provider) {
        return provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider
            && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) provider).isSinglePushPerVisit();
    }

    static boolean temporarilyUnavailable(ICraftingProvider provider) {
        try {
            return provider.isBusy()
                || provider instanceof NativeCraftingProvider nativeProvider && nativeProvider.wasRejectedByBlocking();
        } catch (RuntimeException | LinkageError noHint) {
            return true; // The rejection is settled; a missing diagnostic is not capacity feedback.
        }
    }

    // Cache only immutable pattern facts. Live interface settings, target faces and explicit queue ownership
    // have already been checked for this submission, including on a cache hit.
    private static boolean isScalablePattern(IPatternDetails pattern) {
        return ECOPatternWrappers.unwrap(pattern) == pattern && pattern instanceof NativePatternDetails
            && pattern.supportsPushInputsToExternalInventory()
            && pattern.getInputs().length > 0
            && !pattern.getOutputs()
                .isEmpty();
    }

    @com.github.bsideup.jabel.Desugar
    private record Eligibility(long tick, boolean supported) {}

    /** History survives visits; growth/recovery flags belong only to the current visit. */
    static final class ProbeState {

        final ECOCraftingDispatchStrategy singles = new ECOCraftingDispatchStrategy();
        private Eligibility eligibility;
        long remembered = 1;
        long nextProbeTick = Long.MIN_VALUE;
        long nextDispatchTick = Long.MIN_VALUE;
        long nextFallbackTick = Long.MIN_VALUE;
        boolean probed;
        boolean directBackedOff;

        Run beginRun(long currentTick, long probeInterval, boolean directBuffer, long directTarget) {
            if (directBuffer && !directBackedOff) {
                return new Run(Math.max(1L, directTarget), true, probeInterval);
            }
            boolean probe = currentTick >= nextProbeTick;
            long target = probe ? (probed ? doubled(remembered) : remembered) : remembered;
            // A new provider/pattern starts with the same one-copy proof but may grow immediately.
            return new Run(target, probe && (probed || remembered == 1), probeInterval);
        }

        /** Probing views stop growing at Integer.MAX_VALUE; larger counted batches are never shrunk by it. */
        private static long doubled(long count) {
            return count >= Integer.MAX_VALUE ? count : Math.min(Integer.MAX_VALUE, count * 2L);
        }

        final class Run {

            long next = remembered;
            long owned;
            final boolean growing;
            final long probeInterval;
            boolean backingOff;

            private Run(long target, boolean growing, long probeInterval) {
                this.next = target;
                this.growing = growing;
                this.probeInterval = probeInterval;
            }

            long offer(long remaining) {
                return Math.min(next, remaining);
            }

            /** Returns whether this visit should try another chunk. */
            boolean record(long offered, long accepted, boolean fullyInserted, long currentTick) {
                if (accepted <= 0) {
                    directBackedOff = true;
                    nextProbeTick = currentTick + probeInterval;
                    // A target filled by earlier chunks cannot prove that those chunks were too large.
                    if (owned > 0 || offered < next) {
                        nextDispatchTick = currentTick + 1;
                        if (owned > 0 || offered == 1) nextFallbackTick = currentTick + 1;
                        return false;
                    }
                    remembered = next = Math.max(1, offered / 2);
                    backingOff = true;
                    if (offered > 1) return true; // Halving strictly decreases to one.
                    nextFallbackTick = currentTick + 1; // This was already an ordinary one-copy refusal.
                    nextDispatchTick = currentTick + probeInterval;
                    nextProbeTick = currentTick + 2 * probeInterval;
                    return false;
                }
                if (accepted != offered || !fullyInserted) {
                    directBackedOff = true;
                    if (owned == 0 && offered == next) {
                        remembered = Math.min(remembered, Math.max(1, Math.min(accepted, offered / 2)));
                    }
                    owned += accepted;
                    nextProbeTick = currentTick + probeInterval;
                    nextDispatchTick = currentTick + 1;
                    nextFallbackTick = currentTick + 1;
                    return false;
                }
                owned += accepted;
                probed = true;
                if (backingOff) {
                    remembered = offered;
                    return false;
                }
                remembered = Math.max(remembered, offered);
                if (growing && offered == next) {
                    nextProbeTick = currentTick + 1;
                    next = doubled(offered);
                } else {
                    next = remembered;
                }
                return true;
            }
        }
    }
}
