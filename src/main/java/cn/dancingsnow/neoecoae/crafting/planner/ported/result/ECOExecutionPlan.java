package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.List;
import java.util.Objects;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.identity.PlanIdentity;

/** Immutable, fully validated hand-off from the planner to the crafting CPU. */
public final class ECOExecutionPlan {

    private final PlanIdentity.Signature signature;

    public PlanIdentity.Signature signature() {
        return signature;
    }

    private final ExecutionMode mode;

    public ExecutionMode mode() {
        return mode;
    }

    private final List<TaskSpec> tasks;

    public List<TaskSpec> tasks() {
        return tasks;
    }

    private final List<PhaseSpec> phases;

    public List<PhaseSpec> phases() {
        return phases;
    }

    private final ECOExecutionSchedule schedule;

    public ECOExecutionSchedule schedule() {
        return schedule;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ECOExecutionPlan)) return false;
        ECOExecutionPlan other = (ECOExecutionPlan) value;
        return java.util.Objects.equals(signature, other.signature) && java.util.Objects.equals(mode, other.mode)
            && java.util.Objects.equals(tasks, other.tasks)
            && java.util.Objects.equals(phases, other.phases)
            && java.util.Objects.equals(schedule, other.schedule);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(signature, mode, tasks, phases, schedule);
    }

    public ECOExecutionPlan(PlanIdentity.Signature signature, ExecutionMode mode, List<TaskSpec> tasks,
        List<PhaseSpec> phases, ECOExecutionSchedule schedule) {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(mode, "mode");
        tasks = com.google.common.collect.ImmutableList.copyOf(tasks);
        phases = com.google.common.collect.ImmutableList.copyOf(phases);
        Objects.requireNonNull(schedule, "schedule");
        validateShape(tasks, phases, mode);

        this.signature = signature;
        this.mode = mode;
        this.tasks = tasks;
        this.phases = phases;
        this.schedule = schedule;
    }

    public TaskSpec task(int taskId) {
        if (taskId < 0 || taskId >= tasks.size()
            || tasks.get(taskId)
                .id() != taskId) {
            throw new IllegalArgumentException("Unknown execution task " + taskId);
        }
        return tasks.get(taskId);
    }

    public static final class TaskSpec {

        private final int id;

        public int id() {
            return id;
        }

        private final PlanIdentity.PatternIdentity identity;

        public PlanIdentity.PatternIdentity identity() {
            return identity;
        }

        private final ICraftingPatternDetails pattern;

        public ICraftingPatternDetails pattern() {
            return pattern;
        }

        private final PatternRuntimeInfo runtimeInfo;

        public PatternRuntimeInfo runtimeInfo() {
            return runtimeInfo;
        }

        private final long totalCount;

        public long totalCount() {
            return totalCount;
        }

        private final int phaseIndex;

        public int phaseIndex() {
            return phaseIndex;
        }

        private final TaskKind kind;

        public TaskKind kind() {
            return kind;
        }

        private final List<PlannedInputAllocation> inputAllocations;

        public List<PlannedInputAllocation> inputAllocations() {
            return inputAllocations;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof TaskSpec)) return false;
            TaskSpec other = (TaskSpec) value;
            return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(identity, other.identity)
                && java.util.Objects.equals(pattern, other.pattern)
                && java.util.Objects.equals(runtimeInfo, other.runtimeInfo)
                && java.util.Objects.equals(totalCount, other.totalCount)
                && java.util.Objects.equals(phaseIndex, other.phaseIndex)
                && java.util.Objects.equals(kind, other.kind)
                && java.util.Objects.equals(inputAllocations, other.inputAllocations);
        }

        @Override
        public int hashCode() {
            return java.util.Objects
                .hash(id, identity, pattern, runtimeInfo, totalCount, phaseIndex, kind, inputAllocations);
        }

        public TaskSpec(int id, PlanIdentity.PatternIdentity identity, ICraftingPatternDetails pattern,
            PatternRuntimeInfo runtimeInfo, long totalCount, int phaseIndex, TaskKind kind,
            List<PlannedInputAllocation> inputAllocations) {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(runtimeInfo, "runtimeInfo");
            Objects.requireNonNull(kind, "kind");
            inputAllocations = com.google.common.collect.ImmutableList.copyOf(inputAllocations);
            if (id < 0 || totalCount <= 0 || phaseIndex < 0) {
                throw new IllegalArgumentException("Invalid execution task");
            }

            this.id = id;
            this.identity = identity;
            this.pattern = pattern;
            this.runtimeInfo = runtimeInfo;
            this.totalCount = totalCount;
            this.phaseIndex = phaseIndex;
            this.kind = kind;
            this.inputAllocations = inputAllocations;
        }

        public TaskSpec(int id, PlanIdentity.PatternIdentity identity, ICraftingPatternDetails pattern,
            PatternRuntimeInfo runtimeInfo, long totalCount, int phaseIndex, TaskKind kind) {
            this(
                id,
                identity,
                pattern,
                runtimeInfo,
                totalCount,
                phaseIndex,
                kind,
                com.google.common.collect.ImmutableList.of());
        }
    }

    /** Frozen fields needed by dispatch/UI without reinterpreting the planner graph. */
    public static final class PatternRuntimeInfo {

        private final Object definition;

        public Object definition() {
            return definition;
        }

        private final int inputSlots;

        public int inputSlots() {
            return inputSlots;
        }

        private final List<GenericStack> outputs;

        public List<GenericStack> outputs() {
            return outputs;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PatternRuntimeInfo)) return false;
            PatternRuntimeInfo other = (PatternRuntimeInfo) value;
            return java.util.Objects.equals(definition, other.definition)
                && java.util.Objects.equals(inputSlots, other.inputSlots)
                && java.util.Objects.equals(outputs, other.outputs);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(definition, inputSlots, outputs);
        }

        public PatternRuntimeInfo(Object definition, int inputSlots, List<GenericStack> outputs) {
            if (inputSlots < 0) throw new IllegalArgumentException("Negative input slot count");
            outputs = com.google.common.collect.ImmutableList.copyOf(outputs);

            this.definition = definition;
            this.inputSlots = inputSlots;
            this.outputs = outputs;
        }

        public static PatternRuntimeInfo from(ICraftingPatternDetails pattern) {
            java.util.List<GenericStack> outputs = new java.util.ArrayList<>();
            for (var output : pattern.getCondensedAEOutputs()) {
                outputs.add(
                    new GenericStack(
                        new cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey(output),
                        output.getStackSize()));
            }
            return new PatternRuntimeInfo(pattern.getPattern(), pattern.getCondensedAEInputs().length, outputs);
        }
    }

    public static final class PhaseSpec {

        private final int index;

        public int index() {
            return index;
        }

        private final int componentId;

        public int componentId() {
            return componentId;
        }

        private final ECOExecutionSchedule.Type type;

        public ECOExecutionSchedule.Type type() {
            return type;
        }

        private final List<Integer> taskIds;

        public List<Integer> taskIds() {
            return taskIds;
        }

        private final List<ExecutionStep> steps;

        public List<ExecutionStep> steps() {
            return steps;
        }

        private final List<Integer> dependencies;

        public List<Integer> dependencies() {
            return dependencies;
        }

        private final java.util.Map<Integer, Long> dynamicFirings;

        public java.util.Map<Integer, Long> dynamicFirings() {
            return dynamicFirings;
        }

        private final java.util.Map<Object, Long> initialSeed;

        public java.util.Map<Object, Long> initialSeed() {
            return initialSeed;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PhaseSpec)) return false;
            PhaseSpec other = (PhaseSpec) value;
            return java.util.Objects.equals(index, other.index)
                && java.util.Objects.equals(componentId, other.componentId)
                && java.util.Objects.equals(type, other.type)
                && java.util.Objects.equals(taskIds, other.taskIds)
                && java.util.Objects.equals(steps, other.steps)
                && java.util.Objects.equals(dependencies, other.dependencies)
                && java.util.Objects.equals(dynamicFirings, other.dynamicFirings)
                && java.util.Objects.equals(initialSeed, other.initialSeed);
        }

        @Override
        public int hashCode() {
            return java.util.Objects
                .hash(index, componentId, type, taskIds, steps, dependencies, dynamicFirings, initialSeed);
        }

        public PhaseSpec(int index, int componentId, ECOExecutionSchedule.Type type, List<Integer> taskIds,
            List<ExecutionStep> steps, List<Integer> dependencies, java.util.Map<Integer, Long> dynamicFirings,
            java.util.Map<Object, Long> initialSeed) {
            if (index < 0) throw new IllegalArgumentException("Negative phase index");
            Objects.requireNonNull(type, "type");
            taskIds = com.google.common.collect.ImmutableList.copyOf(taskIds);
            steps = com.google.common.collect.ImmutableList.copyOf(steps);
            dependencies = com.google.common.collect.ImmutableList.copyOf(dependencies);
            dynamicFirings = com.google.common.collect.ImmutableMap.copyOf(dynamicFirings);
            initialSeed = com.google.common.collect.ImmutableMap.copyOf(initialSeed);
            for (var entry : dynamicFirings.entrySet()) {
                Integer taskId = entry.getKey();
                Long count = entry.getValue();
                if (taskId == null || !taskIds.contains(taskId) || count == null || count <= 0L) {
                    throw new IllegalArgumentException("Invalid dynamic cycle firing vector");
                }
            }
            initialSeed.forEach((key, amount) -> {
                if (key == null || amount == null || amount <= 0L) {
                    throw new IllegalArgumentException("Invalid dynamic cycle seed");
                }
            });

            this.index = index;
            this.componentId = componentId;
            this.type = type;
            this.taskIds = taskIds;
            this.steps = steps;
            this.dependencies = dependencies;
            this.dynamicFirings = dynamicFirings;
            this.initialSeed = initialSeed;
        }

        public PhaseSpec(int index, int componentId, ECOExecutionSchedule.Type type, List<Integer> taskIds,
            List<ExecutionStep> steps, List<Integer> dependencies) {
            this(
                index,
                componentId,
                type,
                taskIds,
                steps,
                dependencies,
                com.google.common.collect.ImmutableMap.of(),
                com.google.common.collect.ImmutableMap.of());
        }
    }

    /** One ordered run. Count is deliberately compressed and may be much larger than an int. */
    public static final class ExecutionStep {

        private final int taskId;

        public int taskId() {
            return taskId;
        }

        private final long count;

        public long count() {
            return count;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof ExecutionStep)) return false;
            ExecutionStep other = (ExecutionStep) value;
            return java.util.Objects.equals(taskId, other.taskId) && java.util.Objects.equals(count, other.count);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(taskId, count);
        }

        public ExecutionStep(int taskId, long count) {
            if (taskId < 0 || count <= 0) throw new IllegalArgumentException("Invalid execution step");

            this.taskId = taskId;
            this.count = count;
        }
    }

    public enum TaskKind {
        DAG,
        CYCLE_REMAINDER,
        CYCLE_ORDERED,
        CYCLE_DYNAMIC
    }

    private static void validateShape(List<TaskSpec> tasks, List<PhaseSpec> phases, ExecutionMode mode) {
        boolean[] taskOwned = new boolean[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            TaskSpec task = tasks.get(i);
            if (task.id() != i) throw new IllegalArgumentException("Task ids must be dense and stable");
            if (task.phaseIndex() >= phases.size()) throw new IllegalArgumentException("Task phase is absent");
            boolean[] allocatedSlots = new boolean[task.runtimeInfo()
                .inputSlots()];
            for (PlannedInputAllocation allocation : task.inputAllocations()) {
                if (allocation.slot() >= allocatedSlots.length || allocatedSlots[allocation.slot()]) {
                    throw new IllegalArgumentException("Invalid or duplicate task input allocation slot");
                }
                allocatedSlots[allocation.slot()] = true;
                if (allocation.totalCrafts() != task.totalCount()) {
                    throw new IllegalArgumentException("Task input allocation does not cover its complete count");
                }
            }
        }
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            PhaseSpec phase = phases.get(phaseIndex);
            if (phase.index() != phaseIndex) throw new IllegalArgumentException("Phase ids must be dense");
            for (int taskId : phase.taskIds()) {
                TaskSpec task = tasks.get(taskId);
                if (task.phaseIndex() != phaseIndex || taskOwned[taskId]) {
                    throw new IllegalArgumentException("Every task must have exactly one runtime owner");
                }
                taskOwned[taskId] = true;
            }
            if (phase.type() == ECOExecutionSchedule.Type.DAG && !phase.steps()
                .isEmpty()) {
                throw new IllegalArgumentException("A DAG phase cannot contain ordered cycle steps");
            }
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && !phase.steps()
                .isEmpty()) {
                throw new IllegalArgumentException("A dynamic cycle phase cannot contain ordered steps");
            }
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && phase.dynamicFirings()
                .isEmpty()) {
                throw new IllegalArgumentException("A dynamic cycle phase requires an exact firing vector");
            }
            if (phase.type() != ECOExecutionSchedule.Type.DYNAMIC_CYCLE && !phase.dynamicFirings()
                .isEmpty()) {
                throw new IllegalArgumentException("Only a dynamic cycle phase can contain dynamic firings");
            }
            phase.dynamicFirings()
                .forEach((taskId, count) -> {
                    if (count > tasks.get(taskId)
                        .totalCount()) {
                        throw new IllegalArgumentException("Dynamic firing count exceeds its task total");
                    }
                });
            if (phase.type() == ECOExecutionSchedule.Type.DAG && !phase.initialSeed()
                .isEmpty()) {
                throw new IllegalArgumentException("Only a cycle phase can retain startup seed metadata");
            }
            java.util.HashSet<Integer> uniqueDependencies = new java.util.HashSet<>();
            for (int dependency : phase.dependencies()) {
                if (dependency < 0 || dependency >= phaseIndex) {
                    throw new IllegalArgumentException("Phase dependencies must follow stable topological order");
                }
                if (!uniqueDependencies.add(dependency)) {
                    throw new IllegalArgumentException("Duplicate phase dependency");
                }
            }
            for (ExecutionStep step : phase.steps()) {
                if (!phase.taskIds()
                    .contains(step.taskId())) {
                    throw new IllegalArgumentException("Cycle step references a task outside its phase");
                }
            }
        }
        for (boolean owned : taskOwned) if (!owned) {
            throw new IllegalArgumentException("Execution plan contains an unowned task");
        }
        if (mode == ExecutionMode.ORDERED_CYCLE && phases.stream()
            .noneMatch(
                p -> p.type() == ECOExecutionSchedule.Type.CYCLE && !p.steps()
                    .isEmpty())) {
            throw new IllegalArgumentException("Ordered-cycle mode requires an ordered cycle trace");
        }
        if (mode == ExecutionMode.DYNAMIC_CYCLE && phases.stream()
            .noneMatch(
                p -> p.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && !p.taskIds()
                    .isEmpty())) {
            throw new IllegalArgumentException("Dynamic-cycle mode requires a dynamic cycle phase");
        }
    }
}
