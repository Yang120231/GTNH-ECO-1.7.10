package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import net.minecraft.world.World;

import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.TickClock;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Owns candidate/phase traversal and input resolution. It deliberately exposes only a small pass result to the CPU
 * logic; provider algorithms and FastPath internals live behind {@link ECOCraftingProviderDispatcher}.
 */
final class ECOCraftingTaskScheduler {

    // A large exact order can contain thousands of task candidates. Walking every
    // candidate in one CPU callback turns a temporary provider stall into a multi-
    // second server tick. The cursor resumes at the next candidate on the next call.
    private static final int MAX_CANDIDATE_INSPECTIONS_PER_EXECUTE = 64;
    private static final int CURSOR_IDLE_PASS_STREAK = 16;

    private final ECOProviderCursor providerCursor = new ECOProviderCursor();
    private final ECOCraftingInputTemplateCache inputTemplateCache = new ECOCraftingInputTemplateCache();
    private final ECOCraftingRemainderCache remainderCache = ECOCraftingRemainderCache.shared();
    private final ECOCraftingProviderDispatcher providerDispatcher;
    private final List<ECOExecutionRuntime.DispatchCandidate> nativeCandidateBuffer = new java.util.ArrayList<>();
    private final Map<FailedCandidateKey, InputAvailabilityEpoch> missingInputFailures = new Object2ObjectOpenHashMap<>();
    private final Object2LongMap<AEKey> physicalInsertGenerations = new Object2LongOpenHashMap<>();

    private long physicalInsertGeneration;
    private IPatternDetails resumeDispatchPattern;
    // Keep successful-pattern priority separate from the bounded stalled-candidate scan cursor.
    private IPatternDetails preferredDispatchPattern;
    private int cursorIdlePasses;
    private int sharedRemainingNormalProbes = -1;
    private DispatchPassResult lastPass = DispatchPassResult.EMPTY;

    ECOCraftingTaskScheduler(ECOCraftingProviderDispatcher providerDispatcher) {
        this.providerDispatcher = providerDispatcher;
    }

    void resetDispatchState() {
        inputTemplateCache.clear();
        providerCursor.clear();
        resumeDispatchPattern = null;
        preferredDispatchPattern = null;
        cursorIdlePasses = 0;
        resetMissingInputMemo();
    }

    void reset() {
        resetDispatchState();
        providerDispatcher.reset();
        sharedRemainingNormalProbes = -1;
        lastPass = DispatchPassResult.EMPTY;
    }

    boolean hasPendingTasks(ExecutingCraftingJob current) {
        if (!current.deferredStock.isEmpty() || !current.deferredEmitted.isEmpty()) return true;
        for (var task : current.tasks.values()) {
            if (task.value > 0L) return true;
        }
        return false;
    }

    void recordPhysicalInsert(AEKey key) {
        if (key == null) return;
        long current = physicalInsertGenerations.getLong(key);
        if (current != Long.MAX_VALUE) physicalInsertGenerations.put(key, current + 1L);
        if (physicalInsertGeneration != Long.MAX_VALUE) physicalInsertGeneration++;
    }

    void invalidateInputTemplates(@org.jetbrains.annotations.Nullable AEKey key) {
        inputTemplateCache.inventoryChanged(key);
    }

    void beginSharedProbeBudget(int operationLimit) {
        sharedRemainingNormalProbes = ECOCraftingDispatchStrategy.ordinaryLimit(operationLimit);
    }

    void consumeSharedProbeBudget(int normalProbes) {
        if (sharedRemainingNormalProbes >= 0) {
            sharedRemainingNormalProbes = Math.max(0, sharedRemainingNormalProbes - normalProbes);
        }
    }

    void endSharedProbeBudget() {
        sharedRemainingNormalProbes = -1;
    }

    DispatchPassResult lastPass() {
        return lastPass;
    }

    DispatchPassResult execute(int maxPatterns, ICraftingService craftingService, IEnergyGrid energyService,
        World level, ExecutingCraftingJob current, ListCraftingInventory inventory,
        Function<IPatternDetails, Iterable<ICraftingProvider>> providerSupplier, BooleanSupplier jobStillActive,
        ECOCraftingCpuContext cpuContext, ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        providerCursor.beginPass(craftingService, TickClock.currentTick());
        providerDispatcher.beginTick(TickClock.currentTick());
        int ordinaryLimit = ECOCraftingDispatchStrategy.ordinaryLimit(maxPatterns);
        int probeLimit = sharedRemainingNormalProbes >= 0 ? sharedRemainingNormalProbes : ordinaryLimit;
        var budget = new ECOCraftingDispatchBudget(ordinaryLimit, probeLimit);

        int totalPushed = 0;
        // A CPU callback may advance several independent ready tasks. Keep a bounded
        // pass window so verified provider batches cannot turn one callback into an
        // unbounded walk over the whole execution graph.
        int taskDispatchLimit = MAX_CANDIDATE_INSPECTIONS_PER_EXECUTE;
        int acceptedDispatches = 0;
        cursorIdlePasses = 0;
        BitSet blockedOrderedPhases = new BitSet();
        dispatchPass: while (jobStillActive.getAsBoolean()) {
            var candidates = current.executionRuntime == null ? nativeDispatchCandidates(current)
                : current.executionRuntime.candidates();
            if (candidates.isEmpty()) {
                preferredDispatchPattern = null;
                cursorIdlePasses = 0;
                break;
            }

            int size = candidates.size();
            int preferred = preferredIndex(candidates);
            int cursor = cursorIndex(candidates);
            blockedOrderedPhases.clear();
            boolean acceptedInPass = false;

            if (preferred >= 0 && cursorIdlePasses > 0) {
                var outcome = inspectAndDispatch(
                    candidates,
                    preferred,
                    false,
                    blockedOrderedPhases,
                    current,
                    providerSupplier,
                    budget,
                    cpuContext,
                    energyService,
                    level,
                    inventory,
                    jobStillActive,
                    normalPush);
                totalPushed = addPushed(totalPushed, outcome.acceptedCrafts());
                if (outcome.stopJob()) break dispatchPass;
                if (outcome.accepted()) {
                    cursorIdlePasses--;
                    acceptedDispatches++;
                    acceptedInPass = true;
                } else {
                    cursorIdlePasses = 0;
                }
            }

            if (!acceptedInPass) {
                boolean preferredCheckedInWindow = false;
                int inspected = 0;
                int step = 0;
                for (; step < size && inspected < MAX_CANDIDATE_INSPECTIONS_PER_EXECUTE; step++) {
                    int candidateIndex = (cursor + step) % size;
                    if (candidateIndex == preferred) preferredCheckedInWindow = true;
                    inspected++;
                    var outcome = inspectAndDispatch(
                        candidates,
                        candidateIndex,
                        true,
                        blockedOrderedPhases,
                        current,
                        providerSupplier,
                        budget,
                        cpuContext,
                        energyService,
                        level,
                        inventory,
                        jobStillActive,
                        normalPush);
                    totalPushed = addPushed(totalPushed, outcome.acceptedCrafts());
                    if (outcome.stopJob()) break dispatchPass;
                    if (outcome.accepted()) {
                        cursorIdlePasses = 0;
                        acceptedDispatches++;
                        acceptedInPass = true;
                        break;
                    }
                }
                if (!acceptedInPass && step < size) {
                    // Preserve progress through a stalled candidate set. This is deliberately
                    // separate from provider round-robin state: no provider was accepted.
                    resumeDispatchPattern = candidates.get((cursor + step) % size)
                        .pattern();
                }
                if (!acceptedInPass && preferred >= 0 && !preferredCheckedInWindow) {
                    var outcome = inspectAndDispatch(
                        candidates,
                        preferred,
                        false,
                        blockedOrderedPhases,
                        current,
                        providerSupplier,
                        budget,
                        cpuContext,
                        energyService,
                        level,
                        inventory,
                        jobStillActive,
                        normalPush);
                    totalPushed = addPushed(totalPushed, outcome.acceptedCrafts());
                    if (outcome.stopJob()) break dispatchPass;
                    if (outcome.accepted()) {
                        cursorIdlePasses = CURSOR_IDLE_PASS_STREAK;
                        acceptedDispatches++;
                        acceptedInPass = true;
                    }
                }
            }
            if (!acceptedInPass) break;
            if (acceptedDispatches >= taskDispatchLimit) break;
            // Re-enter with a fresh candidate snapshot so independent tasks can advance
            // in the same CPU callback without allowing an old dependency snapshot to leak.
        }

        lastPass = new DispatchPassResult(totalPushed, budget.normalProbes(), budget.acceptedNormalPushes());
        return lastPass;
    }

    private CandidateOutcome inspectAndDispatch(List<ECOExecutionRuntime.DispatchCandidate> candidates,
        int candidateIndex, boolean fromCursor, BitSet blockedOrderedPhases, ExecutingCraftingJob current,
        Function<IPatternDetails, Iterable<ICraftingProvider>> providerSupplier, ECOCraftingDispatchBudget budget,
        ECOCraftingCpuContext cpuContext, IEnergyGrid energyService, World level, ListCraftingInventory inventory,
        BooleanSupplier jobStillActive, ECOCraftingProviderDispatcher.ECOCraftingNormalPush normalPush) {
        var candidate = candidates.get(candidateIndex);
        if (blockedOrderedPhases.get(candidate.phaseIndex())) return CandidateOutcome.SKIPPED;

        var progress = current.tasks.get(candidate.pattern());
        if (progress == null || progress.value <= 0L) {
            providerCursor.forget(candidate.pattern());
            releasePreferredPattern(candidate.pattern());
            return CandidateOutcome.SKIPPED;
        }
        long allowedCount = Math.min(candidate.maxDispatchCount(), progress.value);
        if (allowedCount <= 0L) {
            releasePreferredPattern(candidate.pattern());
            return CandidateOutcome.SKIPPED;
        }

        var pattern = candidate.pattern();
        // The final collection step is completed by output delivery after every requested material is ready.
        if (pattern instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.TemporaryCraftingOrder.Pattern)
            return CandidateOutcome.SKIPPED;
        if (current.isProcessingStepStopped(pattern)) {
            releasePreferredPattern(pattern);
            if (candidate.blocksOrderedPhase()) blockedOrderedPhases.set(candidate.phaseIndex());
            return CandidateOutcome.SKIPPED;
        }
        // The explicit execution runtime owns phase/cycle gating. Legacy jobs retain the growth barrier.
        if (current.executionRuntime == null && !current.canDispatchAfterGrowth(pattern)) {
            releasePreferredPattern(pattern);
            return CandidateOutcome.SKIPPED;
        }

        FailedCandidateKey failedCandidateKey = current.executionRuntime == null ? null
            : new FailedCandidateKey(candidate.phaseIndex(), candidate.taskId());
        Set<AEKey> dependencyKeys = current.executionRuntime == null
            ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf()
            : current.executionRuntime.inputKeys(candidate.taskId());
        boolean preciseFailureEpoch = failedCandidateKey != null && !dependencyKeys.isEmpty()
            && current.fuzzyPlanningItemIds.isEmpty()
            && !ECOCraftingInputPreview.hasReusableTemplates(pattern, remainderCache);
        if (failedCandidateKey != null) {
            var previousFailure = missingInputFailures.get(failedCandidateKey);
            if (previousFailure != null && availabilityUnchanged(
                previousFailure,
                dependencyKeys,
                current.executionRuntime,
                preciseFailureEpoch)) {
                releasePreferredPattern(pattern);
                if (candidate.blocksOrderedPhase()) {
                    blockedOrderedPhases.set(candidate.phaseIndex());
                }
                return CandidateOutcome.SKIPPED;
            }
        }

        // An exhausted ordinary budget pauses priority instead of ending it.
        boolean[] providerEligibility = new boolean[2];
        var providers = providerCursor
            .availableProviders(pattern, () -> providerSupplier.apply(pattern), providerCandidate -> {
                boolean eligible = providerDispatcher.isEligible(providerCandidate, pattern, budget);
                providerEligibility[eligible ? 0 : 1] = true;
                return eligible;
            },
                (providerCandidate, busy) -> ECOCraftingDispatchPolicyRegistry
                    .isProviderAvailable(cpuContext, providerCandidate, busy));
        if (!providers.hasNext()) {
            if (providerEligibility[0] || !providerEligibility[1]) releasePreferredPattern(pattern);
            if (candidate.blocksOrderedPhase()) blockedOrderedPhases.set(candidate.phaseIndex());
            return CandidateOutcome.SKIPPED;
        }

        long physicalGenerationBeforeResolve = physicalInsertGeneration;
        long seedGenerationBeforeResolve = current.executionRuntime == null ? 0L
            : current.executionRuntime.startupSeedGeneration();
        long reloadGenerationBeforeResolve = PatternRuntime.reloadGeneration();
        var outputs = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter();
        var remainders = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter();
        var protectedStartupSeed = current.executionRuntime == null
            ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.<AEKey, Long>mapOf()
            : current.executionRuntime.protectedStartupSeed(candidate);
        var inputInventory = current.executionRuntime == null && current.fuzzyPlanningItemIds.isEmpty()
            ? new ECOCraftingInputPreview(inventory, inputTemplateCache)
            : new ECOCraftingInputPreview(
                inventory,
                pattern,
                protectedStartupSeed,
                remainderCache,
                current.fuzzyPlanningItemIds);
        var inputs = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
            pattern,
            inputInventory,
            level,
            outputs,
            remainders,
            remainderCache);
        if (inputs == null) {
            releasePreferredPattern(pattern);
            if (failedCandidateKey != null) {
                boolean stable = physicalGenerationBeforeResolve == physicalInsertGeneration
                    && seedGenerationBeforeResolve == current.executionRuntime.startupSeedGeneration()
                    && reloadGenerationBeforeResolve == PatternRuntime.reloadGeneration();
                if (stable) {
                    missingInputFailures.put(
                        failedCandidateKey,
                        captureAvailabilityEpoch(dependencyKeys, current.executionRuntime, preciseFailureEpoch));
                } else {
                    missingInputFailures.remove(failedCandidateKey);
                }
            }
            if (candidate.blocksOrderedPhase()) {
                blockedOrderedPhases.set(candidate.phaseIndex());
            }
            return CandidateOutcome.SKIPPED;
        }

        if (current.executionRuntime != null) {
            allowedCount = current.executionRuntime.limitCycleBatch(candidate, inputs, inventory, allowedCount);
        }
        var dispatchCandidate = allowedCount == candidate.maxDispatchCount() ? candidate
            : new ECOExecutionRuntime.DispatchCandidate(
                candidate.taskId(),
                candidate.phaseIndex(),
                pattern,
                allowedCount,
                candidate.blocksOrderedPhase(),
                candidate.orderedStepIndex());
        var request = new ECOCraftingDispatchRequest(
            current,
            dispatchCandidate,
            pattern,
            inputs,
            outputs,
            remainders,
            allowedCount,
            inventory,
            level);
        ECOCraftingProviderDispatcher.Result dispatch;
        int[] providerAttempts = { 0 };
        try (var ignored = cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching
            .openDispatch(current.fuzzyPlanningItemIds)) {
            dispatch = providerDispatcher.dispatchCandidate(request, providers, budget, energyService, provider -> {
                providerAttempts[0]++;
                providerCursor.advanceAfter(pattern, provider);
            },
                () -> { if (fromCursor) resumeDispatchPattern = nextCandidatePattern(candidates, candidateIndex); },
                normalPush);
        }
        long acceptedCrafts = dispatch.accepted() ? dispatch.acceptedCrafts() : 0L;
        if (!jobStillActive.getAsBoolean()) return new CandidateOutcome(acceptedCrafts, dispatch.accepted(), true);
        if (dispatch.accepted()) {
            // Rebuild legal candidates and inputs after every acceptance.
            preferredDispatchPattern = progress.value > 0L ? pattern : null;
            if (fromCursor) resumeDispatchPattern = nextCandidatePattern(candidates, candidateIndex);
            if (failedCandidateKey != null) missingInputFailures.remove(failedCandidateKey);
            return new CandidateOutcome(acceptedCrafts, true, false);
        }
        if (providerAttempts[0] > 0) releasePreferredPattern(pattern);
        if (candidate.blocksOrderedPhase()) blockedOrderedPhases.set(candidate.phaseIndex());
        return CandidateOutcome.SKIPPED;
    }

    private void releasePreferredPattern(IPatternDetails pattern) {
        if (pattern.equals(preferredDispatchPattern)) preferredDispatchPattern = null;
    }

    /** Index of the last accepted pattern that still has work, or -1. */
    private int preferredIndex(List<ECOExecutionRuntime.DispatchCandidate> candidates) {
        if (preferredDispatchPattern == null) return -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i)
                .pattern()
                .equals(preferredDispatchPattern)) return i;
        }
        preferredDispatchPattern = null;
        return -1;
    }

    /** Where the bounded scan continues, independent of the preferred pattern. */
    private int cursorIndex(List<ECOExecutionRuntime.DispatchCandidate> candidates) {
        if (resumeDispatchPattern == null) return 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i)
                .pattern()
                .equals(resumeDispatchPattern)) return i;
        }
        return 0;
    }

    private static IPatternDetails nextCandidatePattern(List<ECOExecutionRuntime.DispatchCandidate> candidates,
        int candidateIndex) {
        return candidates.get((candidateIndex + 1) % candidates.size())
            .pattern();
    }

    private static int addPushed(int current, long accepted) {
        return (int) Math.min(Integer.MAX_VALUE, (long) current + Math.max(0L, accepted));
    }

    private List<ECOExecutionRuntime.DispatchCandidate> nativeDispatchCandidates(ExecutingCraftingJob current) {
        var result = nativeCandidateBuffer;
        result.clear();
        for (var entry : current.tasks.entrySet()) {
            if (entry.getValue().value > 0L) {
                // Native jobs do not consult task ids; the placeholder id is never committed to a runtime.
                result.add(
                    new ECOExecutionRuntime.DispatchCandidate(0, 0, entry.getKey(), entry.getValue().value, false));
            }
        }
        return result;
    }

    private void resetMissingInputMemo() {
        missingInputFailures.clear();
        physicalInsertGenerations.clear();
        physicalInsertGeneration = 0L;
    }

    private InputAvailabilityEpoch captureAvailabilityEpoch(Set<AEKey> dependencyKeys, ECOExecutionRuntime runtime,
        boolean precise) {
        if (!precise) {
            return new InputAvailabilityEpoch(
                false,
                physicalInsertGeneration,
                runtime.startupSeedGeneration(),
                Object2LongMaps.emptyMap(),
                Object2LongMaps.emptyMap(),
                PatternRuntime.reloadGeneration());
        }
        Object2LongMap<AEKey> physical = new Object2LongOpenHashMap<>(dependencyKeys.size());
        Object2LongMap<AEKey> startupSeeds = new Object2LongOpenHashMap<>(dependencyKeys.size());
        for (AEKey key : dependencyKeys) {
            physical.put(key, physicalInsertGenerations.getLong(key));
            startupSeeds.put(key, runtime.startupSeedGeneration(key));
        }
        // These snapshots are privately owned by the epoch.
        return new InputAvailabilityEpoch(
            precise,
            physicalInsertGeneration,
            runtime.startupSeedGeneration(),
            physical,
            startupSeeds,
            PatternRuntime.reloadGeneration());
    }

    private boolean availabilityUnchanged(InputAvailabilityEpoch previous, Set<AEKey> dependencyKeys,
        ECOExecutionRuntime runtime, boolean precise) {
        if (previous.reloadGeneration() != PatternRuntime.reloadGeneration()) return false;
        if (previous.precise() != precise) return false;
        if (!precise) {
            return previous.globalPhysicalInsertGeneration() == physicalInsertGeneration
                && previous.globalStartupSeedGeneration() == runtime.startupSeedGeneration();
        }
        for (AEKey key : dependencyKeys) {
            if (previous.physicalInsertGenerations()
                .getLong(key) != physicalInsertGenerations.getLong(key)
                || previous.startupSeedGenerations()
                    .getLong(key) != runtime.startupSeedGeneration(key))
                return false;
        }
        return true;
    }

    @com.github.bsideup.jabel.Desugar
    record DispatchPassResult(int totalPushed, int normalProbes, int acceptedNormalPushes) {

        static final DispatchPassResult EMPTY = new DispatchPassResult(0, 0, 0);
    }

    @com.github.bsideup.jabel.Desugar
    private record FailedCandidateKey(int phaseIndex, int taskId) {}

    @com.github.bsideup.jabel.Desugar
    private record InputAvailabilityEpoch(boolean precise, long globalPhysicalInsertGeneration,
        long globalStartupSeedGeneration, Object2LongMap<AEKey> physicalInsertGenerations,
        Object2LongMap<AEKey> startupSeedGenerations, long reloadGeneration) {}

    @com.github.bsideup.jabel.Desugar
    private record CandidateOutcome(long acceptedCrafts, boolean accepted, boolean stopJob) {

        static final CandidateOutcome SKIPPED = new CandidateOutcome(0L, false, false);
    }
}
