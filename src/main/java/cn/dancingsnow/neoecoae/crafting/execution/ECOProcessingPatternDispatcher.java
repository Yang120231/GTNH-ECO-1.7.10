package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
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
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/**
 * ECO owns processing batch growth; optional adapters only transport the selected batch.
 *
 * <p>
 * The per-tick budget counts provider submissions, never crafts: one push carrying a billion copies costs one
 * attempt. Batch size comes from the task, materials, energy, cycle seeds, the target's capacity and numeric bounds.
 */
final class ECOProcessingPatternDispatcher {

    private static final long ELIGIBILITY_CACHE_TICKS = 10L;
    private static final long SCALE_PROBE_INTERVAL_TICKS = 5L;
    static final int MAX_ATTEMPTS_PER_TICK = 64;
    /** One continuously accepting pattern may use half of a tick; other ready tasks keep the rest. */
    static final int MAX_ATTEMPTS_PER_PATTERN_PER_TICK = MAX_ATTEMPTS_PER_TICK / 2;
    static final int MAX_ATTEMPTS_PER_VISIT = 32;
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, ProbeState>> states = new Reference2ObjectOpenHashMap<>();
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, Eligibility>> eligibility = new Reference2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<IPatternDetails> patternAttempts = new Reference2IntOpenHashMap<>();
    private long tick = Long.MIN_VALUE;
    private int attempts;

    ECOProcessingPatternDispatcher(ECOCraftingCPULogic owner, ECOCraftingEnergyTransaction energy,
        ECOCraftingDispatchAccounting accounting) {
        this.owner = owner;
        this.energy = energy;
        this.accounting = accounting;
    }

    void beginTick(long gameTick) {
        if (tick != gameTick) {
            tick = gameTick;
            attempts = 0;
            patternAttempts.clear();
        }
    }

    void reset() {
        states.clear();
        eligibility.clear();
        patternAttempts.clear();
        tick = Long.MIN_VALUE;
        attempts = 0;
    }

    boolean isScaledAttemptBudgetExhausted() {
        return attempts >= MAX_ATTEMPTS_PER_TICK;
    }

    /** A pattern that used its share pauses until the next tick; this is not a provider failure. */
    boolean isScaledAttemptBudgetExhausted(IPatternDetails pattern) {
        return isScaledAttemptBudgetExhausted() || patternAttempts.getInt(pattern) >= MAX_ATTEMPTS_PER_PATTERN_PER_TICK;
    }

    /** Whether a scaled provider may still receive a submission for this pattern this tick. */
    boolean mayAttemptScaled(ICraftingProvider provider, IPatternDetails pattern) {
        return provider instanceof NativeCraftingProvider nativeProvider && (!isScaledAttemptBudgetExhausted(pattern)
            && (nativeProvider.supportsCountedBatch(pattern) || nativeProvider.supportsScaledProcessing())
            || nativeProvider.canDeliverCircuitInstantly(pattern));
    }

    boolean claimFallbackAttempt(IPatternDetails pattern) {
        if (isScaledAttemptBudgetExhausted(pattern)) return false;
        claimAttempt(pattern);
        return true;
    }

    private void claimAttempt(IPatternDetails pattern) {
        attempts++;
        patternAttempts.addTo(pattern, 1);
    }

    boolean isScaledDispatchDeferred(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        var byPattern = states.get(provider);
        var state = byPattern == null ? null : byPattern.get(request.pattern());
        return state != null && tick < state.nextDispatchTick;
    }

    boolean isScaledFallbackDeferred(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        var byPattern = states.get(provider);
        var state = byPattern == null ? null : byPattern.get(request.pattern());
        return state != null && tick < state.nextFallbackTick;
    }

    boolean supportsScaledDispatchCached(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        // Interface settings are live; a cached capability must never override an optimization opt-out or lock.
        if (!scaledTransportAllowed(provider)) return false;
        Reference2ObjectOpenHashMap<IPatternDetails, Eligibility> byPattern = eligibility
            .computeIfAbsent(provider, ignored -> new Reference2ObjectOpenHashMap<>());
        Eligibility cached = byPattern.get(request.pattern());
        if (cached != null && tick - cached.tick() < ELIGIBILITY_CACHE_TICKS) return cached.supported();
        boolean supported = supportsScaledDispatch(request, provider);
        byPattern.put(request.pattern(), new Eligibility(tick, supported));
        return supported;
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
        request.job().singleProcessingReason = "";
        return count;
    }

    long tryCountedDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider, double onePower,
        IEnergyGrid service, Consumer<ICraftingProvider> mark) {
        if (!(provider instanceof NativeCraftingProvider nativeProvider)
            || isScaledAttemptBudgetExhausted(request.pattern())
            || !nativeProvider.supportsCountedBatch(request.pattern())) return 0;
        long accepted;
        try {
            accepted = dispatchCounted(request, nativeProvider, onePower, service, energy, () -> {
                claimAttempt(request.pattern());
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
        double onePower, IEnergyGrid service, Consumer<ICraftingProvider> mark,
        ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        if (!scaledTransportAllowed(provider) || isScaledAttemptBudgetExhausted(request.pattern())
            || isScaledDispatchDeferred(request, provider)
            || !supportsScaledDispatchCached(request, provider)) return null;
        var nativeProvider = (NativeCraftingProvider) provider;
        // A plain-blocking interface takes exactly one push per empty target: that push carries the learned size.
        boolean singlePush = isSinglePushTarget(provider);
        long transportLimit = nativeProvider.scaledProcessingCopyLimit(request.inputs());

        ProbeState state = states.computeIfAbsent(provider, x -> new Reference2ObjectOpenHashMap<>())
            .computeIfAbsent(request.pattern(), x -> new ProbeState());
        long limit = request.allowedCrafts();
        var ramp = state.beginRun(tick, SCALE_PROBE_INTERVAL_TICKS);
        var planning = ECOBatchDispatchPlanning.prepare(request, provider);
        IPatternDetails cachedExecutionPattern = null;
        long cachedCopies = 0;
        int visitAttempts = 0;
        while (ramp.owned < limit && !isScaledAttemptBudgetExhausted(request.pattern())
            && visitAttempts < MAX_ATTEMPTS_PER_VISIT
            && !provider.isBusy()
            && scaledTransportAllowed(provider)) {
            claimAttempt(request.pattern());
            visitAttempts++;
            // Probes are real submissions: accepted chunks already own their share of the remaining order.
            long offer = Math.min(ramp.offer(limit - ramp.owned), transportLimit);
            var plan = planning.plan(offer, limit - ramp.owned, onePower, service, ECOBatchMode.LINEAR);
            if (plan == null) {
                state.nextDispatchTick = tick + 1;
                break;
            }
            offer = plan.craftCount();
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
                if (pushed[0] && wasRejectedByBlocking(provider)) {
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
            if (singlePush || request.job().suspended || owner.getJob() != request.job()) break;
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
            return nativeProvider.allowsScaledWhileBlocking();
        return !NativeProviderAdapters.isEnabled(provider);
    }

    private static boolean isSinglePushTarget(ICraftingProvider provider) {
        return provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider
            && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) provider).isSinglePushPerVisit();
    }

    private static boolean wasRejectedByBlocking(ICraftingProvider provider) {
        return provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider
            && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) provider)
                .wasRejectedByBlocking();
    }

    private static boolean isProcessingPattern(Object pattern) {
        return pattern instanceof NativePatternDetails
            && ((NativePatternDetails) pattern).supportsPushInputsToExternalInventory();
    }

    /**
     * Selects providers for ECO-owned aggregate submission. AE2LT uses a direct counted
     * transport; ordinary providers receive an execution view preserving sparse input order.
     * Acceptance transfers the entire chunk; the send buffer separately determines capacity proof.
     */
    static boolean supportsScaledDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        if (!scaledTransportAllowed(provider)
            || !(provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider))
            return false;
        if (provider instanceof ECOParallelCraftingProvider) return false;
        IPatternDetails base = ECOPatternWrappers.unwrap(request.pattern());
        return base == request.pattern() && isProcessingPattern(base)
            && request.pattern()
                .getInputs().length > 0
            && !request.pattern()
                .getOutputs()
                .isEmpty()
            && request.pattern()
                .supportsPushInputsToExternalInventory()
            && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) provider)
                .supportsScaledProcessing();
    }

    @com.github.bsideup.jabel.Desugar
    private record Eligibility(long tick, boolean supported) {}

    /** History survives visits; growth/recovery flags belong only to the current visit. */
    static final class ProbeState {

        private static final int MAX_RECOVERY_RETRIES = 3;
        long remembered = 1;
        long nextProbeTick = Long.MIN_VALUE;
        long nextDispatchTick = Long.MIN_VALUE;
        long nextFallbackTick = Long.MIN_VALUE;
        boolean probed;

        Run beginRun(long currentTick, long probeInterval) {
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
            int recoveryRetries;

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
                    nextProbeTick = currentTick + probeInterval;
                    // A target filled by earlier chunks cannot prove that those chunks were too large.
                    if (owned > 0 || offered < next) {
                        nextDispatchTick = currentTick + 1;
                        if (owned > 0) nextFallbackTick = currentTick + 1;
                        return false;
                    }
                    remembered = next = Math.max(1, offered / 2);
                    backingOff = true;
                    if (offered > 1 && recoveryRetries++ < MAX_RECOVERY_RETRIES) return true;
                    nextDispatchTick = currentTick + probeInterval;
                    nextProbeTick = currentTick + 2 * probeInterval;
                    return false;
                }
                if (accepted != offered || !fullyInserted) {
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
