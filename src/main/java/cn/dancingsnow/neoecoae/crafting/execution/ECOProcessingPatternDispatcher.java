package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEProcessingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/** ECO owns processing batch growth; optional adapters only transport the selected batch. */
final class ECOProcessingPatternDispatcher {

    private static final long TICK_BUDGET = 1_000_000L;
    private static final long ELIGIBILITY_CACHE_TICKS = 10L;
    private static final long SCALE_PROBE_INTERVAL_TICKS = 5L;
    static final int MAX_ATTEMPTS_PER_TICK = 64;
    static final int MAX_ATTEMPTS_PER_VISIT = 32;
    private final ECOCraftingCPULogic owner;
    private final ECOCraftingEnergyTransaction energy;
    private final ECOCraftingDispatchAccounting accounting;
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, ProbeState>> states = new Reference2ObjectOpenHashMap<>();
    private final Map<ICraftingProvider, Reference2ObjectOpenHashMap<IPatternDetails, Eligibility>> eligibility = new Reference2ObjectOpenHashMap<>();
    private long tick = Long.MIN_VALUE, used;
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
            used = 0;
            attempts = 0;
        }
    }

    void reset() {
        states.clear();
        eligibility.clear();
        tick = Long.MIN_VALUE;
        used = 0;
        attempts = 0;
    }

    boolean isScaledAttemptBudgetExhausted() {
        return attempts >= MAX_ATTEMPTS_PER_TICK || used >= TICK_BUDGET;
    }

    boolean claimFallbackAttempt() {
        if (isScaledAttemptBudgetExhausted()) return false;
        attempts++;
        return true;
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
        Reference2ObjectOpenHashMap<IPatternDetails, Eligibility> byPattern = eligibility
            .computeIfAbsent(provider, ignored -> new Reference2ObjectOpenHashMap<>());
        Eligibility cached = byPattern.get(request.pattern());
        if (cached != null && tick - cached.tick() < ELIGIBILITY_CACHE_TICKS) return cached.supported();
        boolean supported = supportsScaledDispatch(request, provider);
        byPattern.put(request.pattern(), new Eligibility(tick, supported));
        return supported;
    }

    /** Learns from fully inserted chunks; target saturation ends a visit without erasing its successes. */
    @Nullable
    ECOCraftingDispatchResult tryScaledDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        double onePower, IEnergyGrid service, Consumer<ICraftingProvider> mark,
        ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        if (NativeProviderAdapters.isEnabled(provider) || isScaledAttemptBudgetExhausted()
            || isScaledDispatchDeferred(request, provider)
            || !supportsScaledDispatchCached(request, provider)) return null;
        Object logic = providerLogic(provider);
        long transportLimit = Long.MAX_VALUE;

        long budget = TICK_BUDGET - used;
        if (budget <= 0) return null;
        ProbeState state = states.computeIfAbsent(provider, x -> new Reference2ObjectOpenHashMap<>())
            .computeIfAbsent(request.pattern(), x -> new ProbeState());
        long limit = Math.min(request.allowedCrafts(), budget);
        var ramp = state.beginRun(tick, SCALE_PROBE_INTERVAL_TICKS);
        var planning = ECOBatchDispatchPlanning.prepare(request, provider);
        IPatternDetails cachedExecutionPattern = null;
        long cachedCopies = 0;
        int visitAttempts = 0;
        // Unbounded pattern hatches store any push whole: offer everything material and energy allow at once.
        // The first rejection hands control back to the normal probing ramp.
        boolean offerAll = isUnboundedTarget(provider);
        while (ramp.owned < limit && attempts < MAX_ATTEMPTS_PER_TICK
            && visitAttempts < MAX_ATTEMPTS_PER_VISIT
            && !provider.isBusy()
            && !NativeProviderAdapters.isEnabled(provider)) {
            attempts++;
            visitAttempts++;
            long offer = offerAll ? limit - ramp.owned : Math.min(ramp.offer(limit - ramp.owned), transportLimit);
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
                        return normalPush.push(scaledRequest, provider)
                            ? ECOBatchAdmission.accepted(batch.craftCount(), fullyInserted(logic))
                            : ECOBatchAdmission.rejected();
                    });
            } catch (ECOIndeterminateBatchException failure) {
                request.job()
                    .failPermanently("AMBIGUOUS_SCALED_PROVIDER_OWNERSHIP");
                return null;
            } catch (RuntimeException failure) {
                request.job()
                    .failPermanently("SCALED_BATCH_SETTLEMENT_FAILURE");
                throw failure;
            }
            if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) {
                offerAll = false;
                if (ramp.record(offer, 0, false, tick)) continue;
                break;
            }
            used += admission.acceptedCrafts();
            try {

                accounting.apply(request, scaledResult(request, admission.acceptedCrafts()), () -> {}, provider);
            } catch (RuntimeException failure) {
                request.job()
                    .failPermanently("POST_ACCEPT_PROCESSING_ACCOUNTING_FAILURE");
                throw failure;
            }
            if (!ramp.record(offer, admission.acceptedCrafts(), admission.canContinue(), tick)) break;
        }
        return ramp.owned > 0 ? scaledResult(request, ramp.owned) : null;
    }

    private static ECOCraftingDispatchResult scaledResult(ECOCraftingDispatchRequest request, long copies) {
        return ECOCraftingDispatchResult.batch(
            copies,
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.outputs()), copies),
            ECOBatchCraftingHelper.multiply(ECOFastPathStacks.copyCounter(request.remainders()), copies));
    }

    private static boolean fullyInserted(Object logic) {
        return logic instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider
            && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) logic)
                .isScaledProcessingFullyInserted();
    }

    private static boolean isUnboundedTarget(ICraftingProvider provider) {
        return provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider
            && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) provider)
                .isUnboundedPatternHatch();
    }

    private static boolean isProcessingPattern(Object pattern) {
        return pattern instanceof AEProcessingPattern;
    }

    private static Object providerLogic(ICraftingProvider provider) {
        return provider;
    }

    /**
     * Selects providers for ECO-owned aggregate submission. AE2LT uses a direct counted
     * transport; ordinary providers receive an execution view preserving sparse input order.
     * Acceptance transfers the entire chunk; the send buffer separately determines capacity proof.
     */
    static boolean supportsScaledDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        if (NativeProviderAdapters.isEnabled(provider)
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

        private static long doubled(long count) {
            return Math.min(Integer.MAX_VALUE, count * 2L);
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
