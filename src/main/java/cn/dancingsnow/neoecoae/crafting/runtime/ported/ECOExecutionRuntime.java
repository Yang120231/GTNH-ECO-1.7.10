package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionSchedule;

/**
 * Mutable server-side cursor for one immutable ECO execution plan.
 *
 * <p>
 * The plan describes what may be dispatched. This class owns the small amount of state that changes while a
 * job runs: ordered-step progress, dynamic firing counts, phase completion and the startup seed still reserved in
 * the CPU inventory. A failed provider attempt never reaches {@link #onAccepted} and therefore never advances any
 * scheduler state.
 * </p>
 */
public final class ECOExecutionRuntime {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae.dispatch");
    private static final boolean WATCHDOG_DEBUG = false;
    private static final int MAX_DIAGNOSTIC_PHASES = 16;
    private static final int MAX_DIAGNOSTIC_TASKS = 8;
    private static final int MAX_DIAGNOSTIC_LENGTH = 4096;

    private final ECOExecutionPlan plan;
    private final ICraftingPatternDetails[] patternsById;

    private final TaskProgress[] progressByTaskId;
    private final int[][] progressAliasesByTaskId;
    private final List<Set<Object>> inputKeysByTaskId;
    private final int[] stepCursor;
    private final int[] dynamicCursor;
    private final List<long[]> remainingSteps;
    private final List<Map<Integer, Long>> remainingDynamicFirings;
    private final BitSet completedPhases;
    private final int[] unfinishedTasksByPhase;
    private final boolean[] unfinishedTasks;
    private final int[] activeDynamicTasksByPhase;
    private final int[] remainingDependencies;
    private final int[] activeTaskBuffer;
    private final List<List<Integer>> dependentsByPhase;
    // Reused by the dispatch loop; callers consume the snapshot before requesting the next one.
    private final List<DispatchCandidate> candidateBuffer = new ArrayList<>();
    private final List<Map<Object, Long>> startupSeedRemainingByPhase;
    private final Map<Object, Long> startupSeedGenerations = new HashMap<>();
    private long startupSeedGeneration;
    private boolean reconciledEmptyCandidates;

    public ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, ICraftingPatternDetails> patternsById) {
        this(plan, toPatternArray(plan, patternsById), null);
    }

    public ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, ICraftingPatternDetails> patternsById,
        TaskProgress[] progressByTaskId) {
        this(plan, toPatternArray(plan, patternsById), progressByTaskId);
    }

    ECOExecutionRuntime(ECOExecutionPlan plan, ICraftingPatternDetails[] patternsById,
        TaskProgress[] progressByTaskId) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.patternsById = Objects.requireNonNull(patternsById, "patternsById")
            .clone();
        if (this.patternsById.length != plan.tasks()
            .size()) {
            throw new IllegalArgumentException("Execution pattern binding shape changed");
        }
        this.progressByTaskId = progressByTaskId == null ? null : progressByTaskId.clone();
        if (this.progressByTaskId != null && this.progressByTaskId.length != plan.tasks()
            .size()) {
            throw new IllegalArgumentException("Execution progress binding shape changed");
        }
        this.stepCursor = new int[plan.phases()
            .size()];
        this.dynamicCursor = new int[plan.phases()
            .size()];
        this.remainingSteps = new ArrayList<>(
            plan.phases()
                .size());
        this.remainingDynamicFirings = new ArrayList<>(
            plan.phases()
                .size());
        this.completedPhases = new BitSet(
            plan.phases()
                .size());
        this.inputKeysByTaskId = new ArrayList<>(
            plan.tasks()
                .size());
        this.unfinishedTasksByPhase = new int[plan.phases()
            .size()];
        this.unfinishedTasks = new boolean[plan.tasks()
            .size()];
        this.activeDynamicTasksByPhase = new int[plan.phases()
            .size()];
        this.remainingDependencies = new int[plan.phases()
            .size()];
        this.activeTaskBuffer = new int[plan.tasks()
            .size()];
        this.dependentsByPhase = createDependents(plan);
        this.startupSeedRemainingByPhase = new ArrayList<>(
            plan.phases()
                .size());

        for (var task : plan.tasks()) {
            ICraftingPatternDetails actual = pattern(task.id());
            if (actual == null || !samePattern(task.pattern(), actual)) {
                throw new IllegalArgumentException(
                    "Execution task is not bound to the submitted pattern vector: " + task.id());
            }
            if (this.progressByTaskId != null && this.progressByTaskId[task.id()] == null) {
                throw new IllegalArgumentException("Execution task has no bound progress: " + task.id());
            }
        }
        this.progressAliasesByTaskId = createProgressAliases(
            this.progressByTaskId,
            plan.tasks()
                .size());
        rejectSharedProgressAliases();
        for (var phase : plan.phases()) {
            long[] steps = new long[phase.steps()
                .size()];
            for (int i = 0; i < steps.length; i++) steps[i] = phase.steps()
                .get(i)
                .count();
            remainingSteps.add(steps);
            remainingDynamicFirings.add(new LinkedHashMap<>(phase.dynamicFirings()));
            startupSeedRemainingByPhase.add(new LinkedHashMap<>(phase.initialSeed()));
        }
        for (ICraftingPatternDetails pattern : this.patternsById) {
            inputKeysByTaskId.add(Collections.unmodifiableSet(inputKeys(pattern)));
        }
        rebuildProgressState();
        logSharedProgressAliases();
    }

    public ECOExecutionPlan plan() {
        return plan;
    }

    /** Candidate returned by the scheduler for one provider attempt. */
    public static final class DispatchCandidate {

        private final int taskId;

        public int taskId() {
            return taskId;
        }

        private final int phaseIndex;

        public int phaseIndex() {
            return phaseIndex;
        }

        private final ICraftingPatternDetails pattern;

        public ICraftingPatternDetails pattern() {
            return pattern;
        }

        private final long maxDispatchCount;

        public long maxDispatchCount() {
            return maxDispatchCount;
        }

        private final boolean blocksOrderedPhase;

        public boolean blocksOrderedPhase() {
            return blocksOrderedPhase;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof DispatchCandidate)) return false;
            DispatchCandidate other = (DispatchCandidate) value;
            return java.util.Objects.equals(taskId, other.taskId)
                && java.util.Objects.equals(phaseIndex, other.phaseIndex)
                && java.util.Objects.equals(pattern, other.pattern)
                && java.util.Objects.equals(maxDispatchCount, other.maxDispatchCount)
                && java.util.Objects.equals(blocksOrderedPhase, other.blocksOrderedPhase);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(taskId, phaseIndex, pattern, maxDispatchCount, blocksOrderedPhase);
        }

        public DispatchCandidate(int taskId, int phaseIndex, ICraftingPatternDetails pattern, long maxDispatchCount,
            boolean blocksOrderedPhase) {
            Objects.requireNonNull(pattern, "pattern");
            if (taskId < 0 || phaseIndex < 0 || maxDispatchCount <= 0L) {
                throw new IllegalArgumentException("Invalid execution candidate");
            }

            this.taskId = taskId;
            this.phaseIndex = phaseIndex;
            this.pattern = pattern;
            this.maxDispatchCount = maxDispatchCount;
            this.blocksOrderedPhase = blocksOrderedPhase;
        }
    }

    /**
     * Returns all currently legal candidates in deterministic phase/task order.
     *
     * <p>
     * An ordered phase contributes only its current step. Other ready phases remain eligible, so an unrelated
     * independent phase is not held hostage by a busy provider in one ordered phase. Dynamic phases rotate after a
     * successful firing; this prevents one branch from consuming every copy of a shared startup seed before another
     * currently runnable branch gets a chance.
     * </p>
     */
    public List<DispatchCandidate> candidates() {
        requireProgressBinding();
        return candidatesInternal(null, true);
    }

    /** Compatibility entry point for callers that have not bound live task progress. */
    public List<DispatchCandidate> candidates(Map<ICraftingPatternDetails, Long> remainingTasks) {
        return candidatesInternal(Objects.requireNonNull(remainingTasks, "remainingTasks"), false);
    }

    private List<DispatchCandidate> candidatesInternal(Map<ICraftingPatternDetails, Long> remainingTasks,
        boolean allowReconciliation) {
        if (remainingTasks != null) refreshCompleted(remainingTasks);
        List<DispatchCandidate> result = candidateBuffer;
        result.clear();
        for (int phaseIndex = 0; phaseIndex < plan.phases()
            .size(); phaseIndex++) {
            if (completedPhases.get(phaseIndex) || !dependenciesComplete(phaseIndex)) continue;
            var phase = plan.phases()
                .get(phaseIndex);
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
                .isEmpty()) {
                advanceFinishedSteps(phaseIndex);
                if (progressByTaskId != null) maybeCompletePhase(phaseIndex);
                if (completedPhases.get(phaseIndex)) continue;
                if (stepCursor[phaseIndex] < phase.steps()
                    .size()) {
                    var step = phase.steps()
                        .get(stepCursor[phaseIndex]);
                    long remaining = taskRemaining(step.taskId(), remainingTasks);
                    long allowed = Math.min(remainingSteps.get(phaseIndex)[stepCursor[phaseIndex]], remaining);
                    if (allowed > 0L) {
                        result.add(candidate(phaseIndex, step.taskId(), allowed, true));
                    }
                } else {
                    addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
                }
                continue;
            }

            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                Map<Integer, Long> dynamic = remainingDynamicFirings.get(phaseIndex);
                int activeCount = 0;
                for (int taskId : phase.taskIds()) {
                    if (dynamic.getOrDefault(taskId, 0L) > 0L && taskRemaining(taskId, remainingTasks) > 0L) {
                        activeTaskBuffer[activeCount++] = taskId;
                    }
                }
                if (progressByTaskId != null) maybeCompletePhase(phaseIndex);
                if (completedPhases.get(phaseIndex)) continue;
                if (activeCount == 0 && !hasDynamicFirings(dynamic)) {
                    addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
                    continue;
                }
                if (activeCount == 0) continue;
                int start = Math.floorMod(dynamicCursor[phaseIndex], activeCount);
                boolean sharedInput = activeCount > 1;
                for (int offset = 0; offset < activeCount; offset++) {
                    int taskId = activeTaskBuffer[(start + offset) % activeCount];
                    long allowed = Math.min(dynamic.getOrDefault(taskId, 0L), taskRemaining(taskId, remainingTasks));
                    if (sharedInput && sharesInputWithAnother(taskId, activeTaskBuffer, activeCount)) {
                        allowed = Math.min(allowed, 1L);
                    }
                    if (allowed > 0L) result.add(candidate(phaseIndex, taskId, allowed, false));
                }
                continue;
            }

            int candidatesBeforePhase = result.size();
            addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
            if (progressByTaskId != null && result.size() == candidatesBeforePhase) {
                // TaskProgress is the source of truth. If a ready DAG phase produces no candidate, reconcile its
                // cached unfinished count before deciding that it must remain open. This repairs a stale phase cache
                // without scanning the complete execution plan on every successful dispatch.
                refreshPhaseTaskState(phaseIndex, phase.taskIds());
                maybeCompletePhase(phaseIndex);
            }
        }
        if (!result.isEmpty()) return result;
        if (allowReconciliation && !reconciledEmptyCandidates
            && completedPhases.cardinality() < plan.phases()
                .size()) {
            reconciledEmptyCandidates = true;
            String before = WATCHDOG_DEBUG ? describeProgressCache() : null;
            rebuildProgressState();
            if (WATCHDOG_DEBUG) {
                String after = describeProgressCache();
                if (!Objects.equals(before, after)) {
                    LOGGER.warn(
                        "[ECO Execution Cache Reconciliation] repaired stale cache before=[{}] after=[{}]",
                        before,
                        after);
                }
            }
            return candidatesInternal(null, false);
        }
        return com.google.common.collect.ImmutableList.of();
    }

    /** Commit scheduler state only after the provider has accepted the extracted inputs. */
    public void onAccepted(DispatchCandidate candidate, long count, java.util.Map<Object, Long>[] inputs) {
        if (count <= 0L || count > candidate.maxDispatchCount()) {
            throw new IllegalArgumentException("Accepted dispatch exceeds scheduler allowance");
        }
        int phaseIndex = candidate.phaseIndex();
        var phase = plan.phases()
            .get(phaseIndex);
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
            .isEmpty()
            && stepCursor[phaseIndex] < phase.steps()
                .size()) {
            var step = phase.steps()
                .get(stepCursor[phaseIndex]);
            if (step.taskId() != candidate.taskId()) {
                throw new IllegalStateException("Accepted task is not the current ordered step");
            }
            long[] steps = remainingSteps.get(phaseIndex);
            steps[stepCursor[phaseIndex]] -= count;
            advanceFinishedSteps(phaseIndex);
        } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            Map<Integer, Long> dynamic = remainingDynamicFirings.get(phaseIndex);
            long before = dynamic.getOrDefault(candidate.taskId(), 0L);
            // Once the exact cycle firing vector is exhausted, remaining AE2 task progress is the aggregate
            // non-cycle remainder. It is legal work, but must not be charged to the already-finished vector.
            if (before > 0L) {
                if (count > before) throw new IllegalStateException("Accepted task exceeds dynamic firing vector");
                dynamic.put(candidate.taskId(), before - count);
                if (before - count <= 0L) activeDynamicTasksByPhase[phaseIndex]--;
                int taskPosition = phase.taskIds()
                    .indexOf(candidate.taskId());
                if (taskPosition >= 0 && !phase.taskIds()
                    .isEmpty()) {
                    dynamicCursor[phaseIndex] = (taskPosition + 1) % phase.taskIds()
                        .size();
                }
            }
        }
        reconciledEmptyCandidates = false;
        refreshTaskState(candidate.taskId());
        maybeCompletePhase(phaseIndex);
        consumeStartupSeed(candidate, inputs, count);
    }

    /**
     * Commits a virtual execution together with its bound task progress. Virtual providers do not expose a
     * concrete input container at this boundary, so startup-seed accounting is deliberately left unchanged; the
     * provider has already performed its own logical input accounting.
     */
    public void onVirtualAccepted(DispatchCandidate candidate, long count) {
        requireProgressBinding();
        if (count <= 0L || count > candidate.maxDispatchCount()) {
            throw new IllegalArgumentException("Accepted virtual dispatch exceeds scheduler allowance");
        }
        var progress = progressByTaskId[candidate.taskId()];
        if (progress == null || progress.value < count) {
            throw new IllegalArgumentException("Accepted virtual dispatch exceeds task progress");
        }
        progress.value -= count;
        onAccepted(candidate, count, new java.util.Map[0]);
    }

    long startupSeedGeneration(Object key) {
        return key == null ? 0L : startupSeedGenerations.getOrDefault(key, 0L);
    }

    long startupSeedGeneration() {
        return startupSeedGeneration;
    }

    Set<Object> inputKeys(int taskId) {
        if (taskId < 0 || taskId >= inputKeysByTaskId.size()) return com.google.common.collect.ImmutableSet.of();
        return inputKeysByTaskId.get(taskId);
    }

    public boolean isComplete() {
        requireProgressBinding();
        refreshCompleted();
        return completedPhases.cardinality() == plan.phases()
            .size();
    }

    public boolean isComplete(Map<ICraftingPatternDetails, Long> remainingTasks) {
        refreshCompleted(Objects.requireNonNull(remainingTasks, "remainingTasks"));
        return completedPhases.cardinality() == plan.phases()
            .size();
    }

    /** Read-only, bounded snapshot used only after dispatch has already been classified as stalled. */
    String describeStallState() {
        var text = new StringBuilder().append("completedPhases=")
            .append(completedPhases.cardinality())
            .append('/')
            .append(
                plan.phases()
                    .size())
            .append(" progressBinding=")
            .append(progressByTaskId != null)
            .append(" sharedProgressAliases=")
            .append(describeProgressAliases())
            .append(" phases=[");
        int included = 0;
        int unfinished = 0;
        for (int phaseIndex = 0; phaseIndex < plan.phases()
            .size(); phaseIndex++) {
            if (completedPhases.get(phaseIndex)) continue;
            unfinished++;
            if (included >= MAX_DIAGNOSTIC_PHASES) continue;
            if (included++ > 0) text.append(", ");
            var phase = plan.phases()
                .get(phaseIndex);
            text.append("phase=")
                .append(phaseIndex)
                .append(" type=")
                .append(phase.type())
                .append(" ready=")
                .append(remainingDependencies[phaseIndex] == 0)
                .append(" remainingDependencies=")
                .append(remainingDependencies[phaseIndex])
                .append(" dependencies=")
                .append(phase.dependencies())
                .append(" unfinishedTasks=")
                .append(unfinishedTasksByPhase[phaseIndex]);

            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
                .isEmpty()) {
                int cursor = stepCursor[phaseIndex];
                text.append(" stepCursor=")
                    .append(cursor)
                    .append('/')
                    .append(
                        phase.steps()
                            .size());
                if (cursor < phase.steps()
                    .size()) {
                    var step = phase.steps()
                        .get(cursor);
                    text.append(" currentStep={task=")
                        .append(step.taskId())
                        .append(" remainingStep=")
                        .append(remainingSteps.get(phaseIndex)[cursor])
                        .append(" taskRemaining=")
                        .append(taskRemaining(step.taskId(), null))
                        .append('}');
                }
            } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                text.append(" dynamicCursor=")
                    .append(dynamicCursor[phaseIndex])
                    .append(" activeDynamicTasks=")
                    .append(activeDynamicTasksByPhase[phaseIndex])
                    .append(" remainingFirings=");
                appendDynamicFirings(text, phaseIndex);
            }
            text.append(" taskRemaining=");
            appendTaskRemaining(text, phase.taskIds());
        }
        if (unfinished > included) {
            text.append(", ... ")
                .append(unfinished - included)
                .append(" phases omitted");
        }
        text.append(']');
        return text.length() <= MAX_DIAGNOSTIC_LENGTH ? text.toString()
            : text.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
    }

    private void appendDynamicFirings(StringBuilder text, int phaseIndex) {
        text.append('[');
        int included = 0;
        int remainingEntries = 0;
        for (var entry : remainingDynamicFirings.get(phaseIndex)
            .entrySet()) {
            if (entry.getValue() <= 0L) continue;
            remainingEntries++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            text.append("task=")
                .append(entry.getKey())
                .append(" firing=")
                .append(entry.getValue())
                .append(" taskRemaining=")
                .append(taskRemaining(entry.getKey(), null));
        }
        if (remainingEntries > included) {
            text.append(",...")
                .append(remainingEntries - included)
                .append(" omitted");
        }
        text.append(']');
    }

    private void appendTaskRemaining(StringBuilder text, List<Integer> taskIds) {
        text.append('[');
        int included = 0;
        int unfinished = 0;
        for (int taskId : taskIds) {
            long remaining = taskRemaining(taskId, null);
            if (remaining <= 0L) continue;
            unfinished++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            text.append(taskId)
                .append('=')
                .append(remaining);
        }
        if (unfinished > included) text.append(",...")
            .append(unfinished - included)
            .append(" omitted");
        text.append(']');
    }

    /** Amount of planned input that must stay in the CPU for future executions of {@code key}. */
    public long reservedInputAmount(Object key) {
        requireProgressBinding();
        return reservedInputAmount(key, null);
    }

    /** Compatibility entry point for callers that have not bound live task progress. */
    public long reservedInputAmount(Object key, Map<ICraftingPatternDetails, Long> remainingTasks) {
        if (key == null) return 0L;
        if (progressByTaskId == null) Objects.requireNonNull(remainingTasks, "remainingTasks");
        long result = totalStartupSeedRemaining(key);
        for (var task : plan.tasks()) {
            long remaining = taskRemaining(task.id(), remainingTasks);
            if (remaining <= 0L) continue;
            ICraftingPatternDetails pattern = pattern(task.id());
            result = NEMath.saturatingAdd(result, inputAmount(pattern, key, remaining));
        }
        return result;
    }

    public Object startupSeedShortfall(
        cn.dancingsnow.neoecoae.crafting.fastpath.ported.ECOBatchCraftingHelper.BatchInventory inventory) {
        Map<Object, Long> totals = protectedStartupSeed(null);
        for (var entry : totals.entrySet()) {
            if (inventory.extract(entry.getKey(), entry.getValue(), Actionable.SIMULATE) < entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Java-8/NBT boundary; resource encoding belongs to the caller's stack registry. */
    public interface ResourceCodec {

        net.minecraft.nbt.NBTTagCompound write(Object key, long amount);

        GenericStack read(net.minecraft.nbt.NBTTagCompound data);
    }

    public net.minecraft.nbt.NBTTagCompound write(ResourceCodec codec) {
        net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
        data.setIntArray("stepCursor", stepCursor);
        data.setIntArray("dynamicCursor", dynamicCursor);
        net.minecraft.nbt.NBTTagList phases = new net.minecraft.nbt.NBTTagList();
        for (int index = 0; index < plan.phases()
            .size(); index++) {
            net.minecraft.nbt.NBTTagCompound phase = new net.minecraft.nbt.NBTTagCompound();
            long[] remaining = remainingSteps.get(index);
            net.minecraft.nbt.NBTTagList steps = new net.minecraft.nbt.NBTTagList();
            for (long count : remaining) {
                net.minecraft.nbt.NBTTagCompound step = new net.minecraft.nbt.NBTTagCompound();
                step.setLong("count", count);
                steps.appendTag(step);
            }
            phase.setTag("steps", steps);
            net.minecraft.nbt.NBTTagList dynamic = new net.minecraft.nbt.NBTTagList();
            for (var entry : remainingDynamicFirings.get(index)
                .entrySet()) {
                net.minecraft.nbt.NBTTagCompound firing = new net.minecraft.nbt.NBTTagCompound();
                firing.setInteger("task", entry.getKey());
                firing.setLong("count", entry.getValue());
                dynamic.appendTag(firing);
            }
            phase.setTag("dynamic", dynamic);
            phases.appendTag(phase);
        }
        data.setTag("phases", phases);
        data.setIntArray(
            "completed",
            completedPhases.stream()
                .toArray());
        net.minecraft.nbt.NBTTagList seeds = new net.minecraft.nbt.NBTTagList();
        for (int index = 0; index < startupSeedRemainingByPhase.size(); index++) {
            for (var entry : startupSeedRemainingByPhase.get(index)
                .entrySet()) {
                net.minecraft.nbt.NBTTagCompound seed = codec.write(entry.getKey(), entry.getValue());
                seed.setInteger("phase", index);
                seeds.appendTag(seed);
            }
        }
        data.setTag("startupSeeds", seeds);
        return data;
    }

    public static ECOExecutionRuntime read(ECOExecutionPlan plan, Map<Integer, ICraftingPatternDetails> patterns,
        TaskProgress[] progress, net.minecraft.nbt.NBTTagCompound data, ResourceCodec codec) {
        ECOExecutionRuntime runtime = new ECOExecutionRuntime(plan, patterns, progress);
        int[] cursor = data.getIntArray("stepCursor");
        int[] dynamicCursor = data.getIntArray("dynamicCursor");
        if (cursor.length != runtime.stepCursor.length || dynamicCursor.length != runtime.dynamicCursor.length)
            throw new IllegalArgumentException("Execution cursor shape changed");
        System.arraycopy(cursor, 0, runtime.stepCursor, 0, cursor.length);
        System.arraycopy(dynamicCursor, 0, runtime.dynamicCursor, 0, dynamicCursor.length);
        net.minecraft.nbt.NBTTagList phases = data.getTagList("phases", 10);
        if (phases.tagCount() != plan.phases()
            .size()) throw new IllegalArgumentException("Execution phase count changed");
        for (int index = 0; index < phases.tagCount(); index++) {
            var phase = phases.getCompoundTagAt(index);
            var steps = phase.getTagList("steps", 10);
            long[] remaining = runtime.remainingSteps.get(index);
            if (steps.tagCount() != remaining.length || cursor[index] < 0 || cursor[index] > remaining.length)
                throw new IllegalArgumentException("Invalid execution step shape");
            for (int i = 0; i < remaining.length; i++) {
                long count = steps.getCompoundTagAt(i)
                    .getLong("count");
                if (count < 0 || count > plan.phases()
                    .get(index)
                    .steps()
                    .get(i)
                    .count()) throw new IllegalArgumentException("Invalid remaining count");
                remaining[i] = count;
            }
            var dynamic = runtime.remainingDynamicFirings.get(index);
            dynamic.clear();
            var entries = phase.getTagList("dynamic", 10);
            for (int i = 0; i < entries.tagCount(); i++) {
                var entry = entries.getCompoundTagAt(i);
                int task = entry.getInteger("task");
                long count = entry.getLong("count");
                if (count < 0 || count > plan.phases()
                    .get(index)
                    .dynamicFirings()
                    .getOrDefault(task, -1L) || dynamic.containsKey(task))
                    throw new IllegalArgumentException("Invalid dynamic count");
                dynamic.put(task, count);
            }
        }
        runtime.completedPhases.clear();
        for (int phase : data.getIntArray("completed")) {
            if (phase < 0 || phase >= plan.phases()
                .size()) throw new IllegalArgumentException("Invalid completed phase");
            runtime.completedPhases.set(phase);
        }
        runtime.startupSeedRemainingByPhase.forEach(Map::clear);
        var seeds = data.getTagList("startupSeeds", 10);
        for (int i = 0; i < seeds.tagCount(); i++) {
            var seed = seeds.getCompoundTagAt(i);
            int phase = seed.getInteger("phase");
            GenericStack stack = codec.read(seed);
            if (phase < 0 || phase >= plan.phases()
                .size() || stack == null || stack.amount() <= 0)
                throw new IllegalArgumentException("Invalid startup seed");
            runtime.startupSeedRemainingByPhase.get(phase)
                .merge(stack.what(), stack.amount(), Math::addExact);
        }
        runtime.validateStartupSeedOwnership();
        runtime.rebuildProgressState();
        return runtime;
    }

    private void refreshCompleted(Map<ICraftingPatternDetails, Long> remainingTasks) {
        for (int phaseIndex = 0; phaseIndex < plan.phases()
            .size(); phaseIndex++) {
            if (!completedPhases.get(phaseIndex) && phaseComplete(phaseIndex, remainingTasks)) {
                markPhaseCompleted(phaseIndex);
            }
        }
    }

    private void refreshCompleted() {
        for (int phaseIndex = 0; phaseIndex < plan.phases()
            .size(); phaseIndex++) {
            if (!completedPhases.get(phaseIndex) && phaseComplete(phaseIndex, null)) {
                markPhaseCompleted(phaseIndex);
            }
        }
    }

    private boolean phaseComplete(int phaseIndex, Map<ICraftingPatternDetails, Long> remainingTasks) {
        var phase = plan.phases()
            .get(phaseIndex);
        if (progressByTaskId != null && remainingTasks == null) {
            if (unfinishedTasksByPhase[phaseIndex] > 0) return false;
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
                .isEmpty()) {
                return stepCursor[phaseIndex] >= phase.steps()
                    .size();
            }
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                return activeDynamicTasksByPhase[phaseIndex] == 0;
            }
            return true;
        }
        for (int taskId : phase.taskIds()) if (taskRemaining(taskId, remainingTasks) > 0L) return false;
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
            .isEmpty()) {
            return stepCursor[phaseIndex] >= phase.steps()
                .size();
        }
        if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            return !hasDynamicFirings(remainingDynamicFirings.get(phaseIndex));
        }
        return true;
    }

    private boolean dependenciesComplete(int phaseIndex) {
        return remainingDependencies[phaseIndex] == 0;
    }

    private void advanceFinishedSteps(int phaseIndex) {
        var steps = plan.phases()
            .get(phaseIndex)
            .steps();
        long[] remaining = remainingSteps.get(phaseIndex);
        while (stepCursor[phaseIndex] < steps.size() && remaining[stepCursor[phaseIndex]] == 0L) {
            stepCursor[phaseIndex]++;
        }
    }

    private void addAllPhaseTasks(List<DispatchCandidate> result, int phaseIndex, List<Integer> taskIds,
        Map<ICraftingPatternDetails, Long> remainingTasks, boolean blocksOrderedPhase) {
        for (int taskId : taskIds) {
            long remaining = taskRemaining(taskId, remainingTasks);
            if (remaining > 0L) result.add(candidate(phaseIndex, taskId, remaining, blocksOrderedPhase));
        }
    }

    private DispatchCandidate candidate(int phaseIndex, int taskId, long allowed, boolean blocksOrderedPhase) {
        ICraftingPatternDetails pattern = pattern(taskId);
        if (pattern == null) throw new IllegalStateException("Execution task is not bound: " + taskId);
        return new DispatchCandidate(taskId, phaseIndex, pattern, allowed, blocksOrderedPhase);
    }

    private long taskRemaining(int taskId, Map<ICraftingPatternDetails, Long> remainingTasks) {
        if (progressByTaskId != null) {
            var progress = progressByTaskId[taskId];
            return progress == null ? 0L : Math.max(0L, progress.value);
        }
        if (remainingTasks == null) return 0L;
        ICraftingPatternDetails pattern = pattern(taskId);
        if (pattern == null) return 0L;
        Long remaining = remainingTasks.get(pattern);
        return remaining == null ? 0L : Math.max(0L, remaining);
    }

    private boolean sharesInputWithAnother(int taskId, int[] active, int activeCount) {
        Set<Object> keys = inputKeysByTaskId.get(taskId);
        if (keys.isEmpty()) return false;
        for (int index = 0; index < activeCount; index++) {
            int otherId = active[index];
            if (otherId == taskId) continue;
            Set<Object> otherKeys = inputKeysByTaskId.get(otherId);
            for (Object key : keys) if (otherKeys.contains(key)) return true;
        }
        return false;
    }

    private static boolean hasDynamicFirings(Map<Integer, Long> dynamic) {
        for (long value : dynamic.values()) if (value > 0L) return true;
        return false;
    }

    private static java.util.Set<Object> inputKeys(ICraftingPatternDetails pattern) {
        java.util.Set<Object> result = new java.util.HashSet<>();
        for (var input : pattern.getCondensedAEInputs())
            if (input != null) result.add(new cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey(input));
        return result;
    }

    private static long inputAmount(ICraftingPatternDetails pattern, Object key, long remaining) {
        long amount = 0;
        for (var input : pattern.getCondensedAEInputs())
            if (input != null && key.equals(new cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey(input)))
                amount = NEMath.saturatingAdd(amount, input.getStackSize());
        return NEMath.saturatingMultiply(amount, remaining);
    }

    private static boolean samePattern(ICraftingPatternDetails left, ICraftingPatternDetails right) {
        if (left == right) return true;
        if (left == null || right == null || left.getPattern() == null || right.getPattern() == null) return false;
        net.minecraft.item.ItemStack first = left.getPattern()
            .copy();
        net.minecraft.item.ItemStack second = right.getPattern()
            .copy();
        first.stackSize = 1;
        second.stackSize = 1;
        return first.writeToNBT(new net.minecraft.nbt.NBTTagCompound())
            .equals(second.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
    }

    public static final class TaskProgress {

        public long value;

        public TaskProgress(long value) {
            this.value = value;
        }
    }

    Map<Object, Long> protectedStartupSeed(DispatchCandidate candidate) {
        int ownerPhase = candidate == null ? -1 : candidate.phaseIndex();
        Map<Object, Long> result = new LinkedHashMap<>();
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            if (phaseIndex == ownerPhase) continue;
            startupSeedRemainingByPhase.get(phaseIndex)
                .forEach((key, amount) -> result.merge(key, amount, NEMath::saturatingAdd));
        }
        return result.isEmpty() ? com.google.common.collect.ImmutableMap.of()
            : com.google.common.collect.ImmutableMap.copyOf(result);
    }

    boolean preservesStartupSeeds(DispatchCandidate candidate, List<GenericStack> inputs,
        cn.dancingsnow.neoecoae.crafting.fastpath.ported.ECOBatchCraftingHelper.BatchInventory inventory) {
        Map<Object, Long> protectedAmounts = protectedStartupSeed(candidate);
        if (protectedAmounts.isEmpty() || inputs.isEmpty()) return true;
        Map<Object, Long> required = new LinkedHashMap<>();
        for (GenericStack input : inputs) {
            if (input != null && input.amount() > 0L) {
                required.merge(input.what(), input.amount(), NEMath::saturatingAdd);
            }
        }
        for (var entry : required.entrySet()) {
            long available = Math
                .max(0L, inventory.available(entry.getKey()) - protectedAmounts.getOrDefault(entry.getKey(), 0L));
            if (available < entry.getValue()) return false;
        }
        return true;
    }

    private long totalStartupSeedRemaining(Object key) {
        long result = 0L;
        for (Map<Object, Long> phaseSeeds : startupSeedRemainingByPhase) {
            result = NEMath.saturatingAdd(result, phaseSeeds.getOrDefault(key, 0L));
        }
        return result;
    }

    private void consumeStartupSeed(DispatchCandidate candidate, java.util.Map<Object, Long>[] inputs, long count) {
        if (inputs == null) return;
        Map<Object, Long> ownedSeeds = startupSeedRemainingByPhase.get(candidate.phaseIndex());
        if (ownedSeeds.isEmpty()) return;
        for (java.util.Map<Object, Long> input : inputs) {
            if (input == null) continue;
            for (var entry : input.entrySet()) {
                long consumed = NEMath.saturatingMultiply(entry.getValue(), count);
                long reserved = ownedSeeds.getOrDefault(entry.getKey(), 0L);
                if (reserved > 0L && consumed > 0L) {
                    long remaining = Math.max(0L, reserved - consumed);
                    if (remaining != reserved) {
                        ownedSeeds.put(entry.getKey(), remaining);
                        incrementStartupSeedGeneration(entry.getKey());
                    }
                }
            }
        }
        ownedSeeds.entrySet()
            .removeIf(entry -> entry.getValue() <= 0L);
    }

    private void requireProgressBinding() {
        if (progressByTaskId == null) {
            throw new IllegalStateException("Execution runtime has no live task-progress binding");
        }
    }

    private ICraftingPatternDetails pattern(int taskId) {
        return taskId >= 0 && taskId < patternsById.length ? patternsById[taskId] : null;
    }

    private void refreshTaskState(int taskId) {
        if (progressByTaskId == null || taskId < 0 || taskId >= unfinishedTasks.length) return;
        BitSet affectedPhases = new BitSet(
            plan.phases()
                .size());
        for (int aliasedTaskId : progressAliasesByTaskId[taskId]) {
            refreshSingleTaskState(aliasedTaskId);
            affectedPhases.set(
                plan.task(aliasedTaskId)
                    .phaseIndex());
        }
        for (int phaseIndex = affectedPhases.nextSetBit(0); phaseIndex
            >= 0; phaseIndex = affectedPhases.nextSetBit(phaseIndex + 1)) {
            maybeCompletePhase(phaseIndex);
        }
    }

    private void refreshSingleTaskState(int taskId) {
        boolean unfinished = taskRemaining(taskId, null) > 0L;
        if (unfinished == unfinishedTasks[taskId]) return;
        unfinishedTasks[taskId] = unfinished;
        int phaseIndex = plan.task(taskId)
            .phaseIndex();
        unfinishedTasksByPhase[phaseIndex] += unfinished ? 1 : -1;
    }

    private void refreshPhaseTaskState(int phaseIndex, List<Integer> taskIds) {
        int unfinished = 0;
        for (int taskId : taskIds) {
            if (plan.task(taskId)
                .phaseIndex() != phaseIndex) {
                throw new IllegalStateException("Execution task is assigned to the wrong phase: " + taskId);
            }
            boolean liveUnfinished = taskRemaining(taskId, null) > 0L;
            unfinishedTasks[taskId] = liveUnfinished;
            if (liveUnfinished) unfinished++;
        }
        unfinishedTasksByPhase[phaseIndex] = unfinished;
    }

    private String describeProgressCache() {
        var text = new StringBuilder("completed=").append(completedPhases.cardinality())
            .append('/')
            .append(
                plan.phases()
                    .size())
            .append(" mismatches=[");
        int mismatches = 0;
        int included = 0;
        for (int phaseIndex = 0; phaseIndex < plan.phases()
            .size(); phaseIndex++) {
            int actual = actualUnfinishedTasks(phaseIndex);
            int cached = unfinishedTasksByPhase[phaseIndex];
            if (actual == cached) continue;
            mismatches++;
            if (included >= MAX_DIAGNOSTIC_PHASES) continue;
            if (included++ > 0) text.append(',');
            text.append("phase=")
                .append(phaseIndex)
                .append(" cached=")
                .append(cached)
                .append(" actual=")
                .append(actual);
        }
        if (mismatches > included) text.append(",...")
            .append(mismatches - included)
            .append(" omitted");
        text.append("] remainingDependencies=")
            .append(Arrays.toString(remainingDependencies));
        return text.length() <= MAX_DIAGNOSTIC_LENGTH ? text.toString()
            : text.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
    }

    private int actualUnfinishedTasks(int phaseIndex) {
        int actual = 0;
        for (int taskId : plan.phases()
            .get(phaseIndex)
            .taskIds()) {
            if (taskRemaining(taskId, null) > 0L) actual++;
        }
        return actual;
    }

    private String describeProgressAliases() {
        if (progressByTaskId == null) return "[]";
        var text = new StringBuilder("[");
        int groups = 0;
        int included = 0;
        for (int taskId = 0; taskId < progressAliasesByTaskId.length; taskId++) {
            int[] aliases = progressAliasesByTaskId[taskId];
            if (aliases.length <= 1 || aliases[0] != taskId) continue;
            groups++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            appendProgressAlias(text, aliases);
        }
        if (groups > included) text.append(",...")
            .append(groups - included)
            .append(" groups omitted");
        return text.append(']')
            .toString();
    }

    private void appendProgressAlias(StringBuilder text, int[] aliases) {
        text.append("identity=")
            .append(System.identityHashCode(progressByTaskId[aliases[0]]))
            .append(" tasks=[");
        int limit = Math.min(aliases.length, MAX_DIAGNOSTIC_TASKS);
        for (int index = 0; index < limit; index++) {
            if (index > 0) text.append(',');
            text.append(aliases[index]);
        }
        if (aliases.length > limit) text.append(",...")
            .append(aliases.length - limit)
            .append(" omitted");
        text.append("] phases=[");
        for (int index = 0; index < limit; index++) {
            if (index > 0) text.append(',');
            text.append(
                plan.task(aliases[index])
                    .phaseIndex());
        }
        if (aliases.length > limit) text.append(",...");
        text.append(']');
    }

    private void logSharedProgressAliases() {
        if (!WATCHDOG_DEBUG || progressByTaskId == null) return;
        String aliases = describeProgressAliases();
        if (!"[]".equals(aliases)) LOGGER.warn("[ECO Execution] shared TaskProgress aliases={}", aliases);
    }

    private void maybeCompletePhase(int phaseIndex) {
        if (progressByTaskId == null || completedPhases.get(phaseIndex)) return;
        var phase = plan.phases()
            .get(phaseIndex);
        if (unfinishedTasksByPhase[phaseIndex] > 0) return;
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
            .isEmpty()
            && stepCursor[phaseIndex] < phase.steps()
                .size())
            return;
        if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && activeDynamicTasksByPhase[phaseIndex] > 0)
            return;
        markPhaseCompleted(phaseIndex);
    }

    private void markPhaseCompleted(int phaseIndex) {
        if (completedPhases.get(phaseIndex)) return;
        completedPhases.set(phaseIndex);
        Map<Object, Long> releasedSeeds = startupSeedRemainingByPhase.get(phaseIndex);
        if (!releasedSeeds.isEmpty()) {
            for (Object key : releasedSeeds.keySet()) incrementStartupSeedGeneration(key);
            releasedSeeds.clear();
        }
        for (int dependent : dependentsByPhase.get(phaseIndex)) {
            if (remainingDependencies[dependent] > 0) remainingDependencies[dependent]--;
        }
    }

    private void incrementStartupSeedGeneration(Object key) {
        if (key == null) return;
        long current = startupSeedGenerations.getOrDefault(key, 0L);
        if (current != Long.MAX_VALUE) startupSeedGenerations.put(key, current + 1L);
        if (startupSeedGeneration != Long.MAX_VALUE) startupSeedGeneration++;
    }

    private void rebuildProgressState() {
        reconcileCycleProgress();
        // With a live binding, completion is derived from task progress and the rebuilt witness rather than trusted
        // as an independent persisted source of truth. The compatibility runtime has no such authoritative counter.
        if (progressByTaskId != null) completedPhases.clear();
        Arrays.fill(unfinishedTasksByPhase, 0);
        Arrays.fill(unfinishedTasks, false);
        Arrays.fill(activeDynamicTasksByPhase, 0);
        Arrays.fill(remainingDependencies, 0);
        for (var dependents : dependentsByPhase) dependents.clear();
        for (var phase : plan.phases()) {
            int phaseIndex = phase.index();
            for (int dependency : phase.dependencies()) {
                dependentsByPhase.get(dependency)
                    .add(phaseIndex);
                if (!completedPhases.get(dependency)) remainingDependencies[phaseIndex]++;
            }
            for (var entry : remainingDynamicFirings.get(phaseIndex)
                .entrySet()) {
                if (entry.getValue() > 0L) activeDynamicTasksByPhase[phaseIndex]++;
            }
        }
        if (progressByTaskId != null) {
            for (int taskId = 0; taskId < progressByTaskId.length; taskId++) {
                boolean unfinished = taskRemaining(taskId, null) > 0L;
                unfinishedTasks[taskId] = unfinished;
                if (unfinished) unfinishedTasksByPhase[plan.task(taskId)
                    .phaseIndex()]++;
            }
            refreshCompleted();
        }
    }

    private static List<List<Integer>> createDependents(ECOExecutionPlan plan) {
        List<List<Integer>> result = new ArrayList<>(
            plan.phases()
                .size());
        for (int i = 0; i < plan.phases()
            .size(); i++) result.add(new ArrayList<>());
        return result;
    }

    private static int[][] createProgressAliases(TaskProgress[] progressByTaskId, int taskCount) {
        int[][] result = new int[taskCount][];
        if (progressByTaskId == null) {
            for (int taskId = 0; taskId < taskCount; taskId++) result[taskId] = new int[] { taskId };
            return result;
        }

        var aliasesByProgress = new IdentityHashMap<TaskProgress, List<Integer>>();
        for (int taskId = 0; taskId < progressByTaskId.length; taskId++) {
            aliasesByProgress.computeIfAbsent(progressByTaskId[taskId], ignored -> new ArrayList<>())
                .add(taskId);
        }
        for (var aliases : aliasesByProgress.values()) {
            int[] taskIds = aliases.stream()
                .mapToInt(Integer::intValue)
                .toArray();
            for (int taskId : taskIds) result[taskId] = taskIds;
        }
        return result;
    }

    private void rejectSharedProgressAliases() {
        if (progressByTaskId == null) return;
        for (int taskId = 0; taskId < progressAliasesByTaskId.length; taskId++) {
            int[] aliases = progressAliasesByTaskId[taskId];
            if (aliases.length > 1 && aliases[0] == taskId) {
                throw new IllegalArgumentException(
                    "Execution tasks share one aggregate TaskProgress: " + Arrays.toString(aliases));
            }
        }
    }

    /** Rebuilds cycle-only progress from the one-to-one live task counters that record accepted provider pushes. */
    private void reconcileCycleProgress() {
        if (progressByTaskId == null) return;
        boolean changed = false;
        for (int phaseIndex = 0; phaseIndex < plan.phases()
            .size(); phaseIndex++) {
            var phase = plan.phases()
                .get(phaseIndex);
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps()
                .isEmpty()) {
                long[] steps = remainingSteps.get(phaseIndex);
                long[] completedByTask = new long[plan.tasks()
                    .size()];
                for (int taskId : phase.taskIds()) {
                    completedByTask[taskId] = Math.max(
                        0L,
                        plan.task(taskId)
                            .totalCount() - taskRemaining(taskId, null));
                }
                boolean prefixComplete = true;
                for (int index = 0; index < phase.steps()
                    .size(); index++) {
                    var step = phase.steps()
                        .get(index);
                    long consumed = prefixComplete ? Math.min(step.count(), completedByTask[step.taskId()]) : 0L;
                    long rebuilt = step.count() - consumed;
                    completedByTask[step.taskId()] -= consumed;
                    if (rebuilt > 0L) prefixComplete = false;
                    changed |= steps[index] != rebuilt;
                    steps[index] = rebuilt;
                }
                stepCursor[phaseIndex] = 0;
                advanceFinishedSteps(phaseIndex);
            } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                Map<Integer, Long> dynamic = remainingDynamicFirings.get(phaseIndex);
                dynamic.keySet()
                    .removeIf(
                        taskId -> !phase.dynamicFirings()
                            .containsKey(taskId));
                for (var initial : phase.dynamicFirings()
                    .entrySet()) {
                    int taskId = initial.getKey();
                    long completed = Math.max(
                        0L,
                        plan.task(taskId)
                            .totalCount() - taskRemaining(taskId, null));
                    long rebuilt = Math.max(0L, initial.getValue() - completed);
                    if (dynamic.getOrDefault(taskId, -1L) != rebuilt) {
                        dynamic.put(taskId, rebuilt);
                        changed = true;
                    }
                }
            }
        }
        if (changed && WATCHDOG_DEBUG) {
            LOGGER.warn("[ECO Execution] reconciled cycle witness counters to accepted AE2 task progress");
        }
    }

    private void restoreLegacyStartupSeeds(Map<Object, Long> legacySeeds) {
        for (var entry : legacySeeds.entrySet()) {
            List<Integer> owners = new ArrayList<>();
            long plannedTotal = 0L;
            for (int phaseIndex = 0; phaseIndex < plan.phases()
                .size(); phaseIndex++) {
                boolean hasLiveWork = progressByTaskId == null ? !completedPhases.get(phaseIndex) : false;
                if (progressByTaskId != null) {
                    for (int taskId : plan.phases()
                        .get(phaseIndex)
                        .taskIds()) {
                        if (taskRemaining(taskId, null) > 0L) {
                            hasLiveWork = true;
                            break;
                        }
                    }
                }
                if (!hasLiveWork) continue;
                long planned = plan.phases()
                    .get(phaseIndex)
                    .initialSeed()
                    .getOrDefault(entry.getKey(), 0L);
                if (planned <= 0L) continue;
                owners.add(phaseIndex);
                plannedTotal = NEMath.saturatingAdd(plannedTotal, planned);
            }
            if (owners.isEmpty() || entry.getValue() > plannedTotal) {
                throw new IllegalArgumentException("Persisted startup seed exceeds execution-plan ownership");
            }
            if (owners.size() > 1 && entry.getValue() != plannedTotal) {
                // The legacy format aggregated equal keys across phases. Once any owner consumed only part of that
                // total, its remaining ownership is unknowable; suspending is safer than assigning another phase's
                // seed and recreating the cross-phase theft this format is being replaced to prevent.
                throw new IllegalArgumentException("Legacy startup seed has ambiguous phase ownership");
            }
            long remaining = entry.getValue();
            for (int phaseIndex : owners) {
                long assigned = Math.min(
                    remaining,
                    plan.phases()
                        .get(phaseIndex)
                        .initialSeed()
                        .getOrDefault(entry.getKey(), 0L));
                startupSeedRemainingByPhase.get(phaseIndex)
                    .put(entry.getKey(), assigned);
                remaining -= assigned;
            }
        }
    }

    private void validateStartupSeedOwnership() {
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            Map<Object, Long> planned = plan.phases()
                .get(phaseIndex)
                .initialSeed();
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex)
                .entrySet()) {
                if (entry.getValue() <= 0L || entry.getValue() > planned.getOrDefault(entry.getKey(), 0L)) {
                    throw new IllegalArgumentException("Persisted startup seed exceeds its phase ownership");
                }
            }
        }
    }

    private static ICraftingPatternDetails[] toPatternArray(ECOExecutionPlan plan,
        Map<Integer, ICraftingPatternDetails> patternsById) {
        ICraftingPatternDetails[] result = new ICraftingPatternDetails[plan.tasks()
            .size()];
        for (var entry : patternsById.entrySet()) {
            if (entry.getKey() != null && entry.getKey() >= 0 && entry.getKey() < result.length) {
                result[entry.getKey()] = entry.getValue();
            }
        }
        return result;
    }
}
