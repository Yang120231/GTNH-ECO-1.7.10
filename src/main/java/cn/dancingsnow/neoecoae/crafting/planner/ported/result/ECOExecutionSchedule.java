package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.ported.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.ported.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics;

/** Runtime component phases. The list is explicitly execution (supplier-to-consumer) order. */
public final class ECOExecutionSchedule {

    private final List<ComponentExecutionPhase> phases;

    public List<ComponentExecutionPhase> phases() {
        return phases;
    }

    private final List<PhaseDependency> dependencies;

    public List<PhaseDependency> dependencies() {
        return dependencies;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ECOExecutionSchedule)) return false;
        ECOExecutionSchedule other = (ECOExecutionSchedule) value;
        return java.util.Objects.equals(this.phases, other.phases)
            && java.util.Objects.equals(this.dependencies, other.dependencies);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(phases, dependencies);
    }

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");

    public ECOExecutionSchedule(List<ComponentExecutionPhase> phases, List<PhaseDependency> dependencies) {
        phases = com.google.common.collect.ImmutableList.copyOf(phases);
        dependencies = com.google.common.collect.ImmutableList.copyOf(dependencies);

        this.phases = phases;
        this.dependencies = dependencies;
    }

    public ECOExecutionSchedule(List<ComponentExecutionPhase> phases) {
        this(phases, com.google.common.collect.ImmutableList.of());
    }

    public static final class PhaseDependency {

        private final int producerPhase;

        public int producerPhase() {
            return producerPhase;
        }

        private final int consumerPhase;

        public int consumerPhase() {
            return consumerPhase;
        }

        public PhaseDependency(int producerPhase, int consumerPhase) {
            this.producerPhase = producerPhase;
            this.consumerPhase = consumerPhase;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PhaseDependency)) return false;
            PhaseDependency other = (PhaseDependency) value;
            return java.util.Objects.equals(this.producerPhase, other.producerPhase)
                && java.util.Objects.equals(this.consumerPhase, other.consumerPhase);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(producerPhase, consumerPhase);
        }
    }

    public static final class ComponentExecutionPhase {

        private final int componentId;

        public int componentId() {
            return componentId;
        }

        private final Type type;

        public Type type() {
            return type;
        }

        private final Set<ICraftingPatternDetails> patternSet;

        public Set<ICraftingPatternDetails> patternSet() {
            return patternSet;
        }

        private final List<ICraftingPatternDetails> cycleWitness;

        public List<ICraftingPatternDetails> cycleWitness() {
            return cycleWitness;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof ComponentExecutionPhase)) return false;
            ComponentExecutionPhase other = (ComponentExecutionPhase) value;
            return java.util.Objects.equals(this.componentId, other.componentId)
                && java.util.Objects.equals(this.type, other.type)
                && java.util.Objects.equals(this.patternSet, other.patternSet)
                && java.util.Objects.equals(this.cycleWitness, other.cycleWitness);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(componentId, type, patternSet, cycleWitness);
        }

        public ComponentExecutionPhase(int componentId, Type type, Set<ICraftingPatternDetails> patternSet,
            List<ICraftingPatternDetails> cycleWitness) {
            patternSet = com.google.common.collect.ImmutableSet.copyOf(patternSet);
            cycleWitness = com.google.common.collect.ImmutableList.copyOf(cycleWitness);
            this.componentId = componentId;
            this.type = type;
            this.patternSet = patternSet;
            this.cycleWitness = cycleWitness;
        }
    }

    public enum Type {
        DAG,
        CYCLE,
        DYNAMIC_CYCLE
    }

    /** Builds the final physical graph from compiler-normalized semantics. */
    public static SelectedExecutionGraph selectedGraph(
        Map<ICraftingPatternDetails, PatternSemantics> selectedSemantics) {
        return SelectedExecutionGraph.build(selectedSemantics);
    }

    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder) {
        return from(components, executionOrder, null);
    }

    /**
     * Builds a runtime schedule from selected work only. Component candidates describe the structural graph,
     * but AE2 stores one aggregate counter per physical pattern; putting a candidate or the same physical
     * pattern into multiple phases can therefore gate real work at a phase whose inputs are not ready yet.
     */
    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder,
        Map<ICraftingPatternDetails, Long> plannedTasks) {
        return from(components, executionOrder, plannedTasks, null);
    }

    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder,
        Map<ICraftingPatternDetails, Long> plannedTasks, ExecutionProvenance provenance) {
        Map<Integer, ComponentPlanningResult> byId = components.stream()
            .collect(java.util.stream.Collectors.toMap(ComponentPlanningResult::componentId, c -> c));
        List<ICraftingPatternDetails> executableTasks = plannedTasks == null ? components.stream()
            .flatMap(
                component -> component.executionPatterns()
                    .stream())
            .collect(java.util.stream.Collectors.toList())
            : plannedTasks.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());

        // A pattern used by a solved cycle owns its entire AE2 aggregate counter, including any additional DAG
        // demand for the same physical pattern. Once the concrete witness has run, the cycle phase deliberately
        // permits that aggregate remainder.
        List<ICraftingPatternDetails> cycleOwnedPatterns = new ArrayList<>();
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            if (!ECOExecutionRequirement.componentIsExecutableCycle(component)) continue;
            for (ICraftingPatternDetails source : component.executionPatterns()) {
                ICraftingPatternDetails executable = executablePattern(source, executableTasks, plannedTasks != null);
                addPhysicalPattern(cycleOwnedPatterns, executable);
            }
        }

        var phases = new ArrayList<ComponentExecutionPhase>();
        List<ICraftingPatternDetails> assignedPatterns = new ArrayList<>();
        for (int id : executionOrder) {
            var c = byId.get(id);
            if (c == null) continue;
            if (c.type() == ComponentPlanningResult.Type.CYCLIC
                && !ECOExecutionRequirement.componentIsExecutableCycle(c)) continue;
            Type type = ECOExecutionRequirement.componentIsDynamic(c) ? Type.DYNAMIC_CYCLE
                : c.type() == ComponentPlanningResult.Type.CYCLIC ? Type.CYCLE : Type.DAG;
            Set<ICraftingPatternDetails> patterns = new LinkedHashSet<>();
            for (ICraftingPatternDetails source : c.executionPatterns()) {
                ICraftingPatternDetails executable = executablePattern(source, executableTasks, plannedTasks != null);
                if (executable == null || containsPhysicalPattern(assignedPatterns, executable)) continue;
                if (type == Type.DAG && containsPhysicalPattern(cycleOwnedPatterns, executable)) continue;
                patterns.add(executable);
                assignedPatterns.add(executable);
            }
            if (patterns.isEmpty()) continue;

            List<ICraftingPatternDetails> witness = new ArrayList<>();
            if (type == Type.CYCLE && c.cycleResult() != null) {
                for (var firing : c.cycleResult()
                    .executionWitness()) {
                    ICraftingPatternDetails executable = executablePattern(
                        (ICraftingPatternDetails) firing.pattern()
                            .details(),
                        executableTasks,
                        plannedTasks != null);
                    if (executable == null || !containsPhysicalPattern(patterns, executable)) {
                        throw new IllegalStateException(
                            "Cycle witness contains a pattern absent from the executable plan");
                    }
                    witness.add(executable);
                }
            }
            phases.add(new ComponentExecutionPhase(id, type, patterns, witness));
        }

        if (plannedTasks != null) {
            List<ICraftingPatternDetails> unassigned = executableTasks.stream()
                .filter(pattern -> !containsPhysicalPattern(assignedPatterns, pattern))
                .collect(java.util.stream.Collectors.toList());
            if (!unassigned.isEmpty() && provenance == null) {
                throw new IllegalStateException(
                    "Execution schedule does not cover " + unassigned.size() + " planned pattern(s)");
            }
            int syntheticId = components.stream()
                .mapToInt(ComponentPlanningResult::componentId)
                .max()
                .orElse(-1) + 1;
            for (ICraftingPatternDetails pattern : unassigned) {
                Set<ICraftingPatternDetails> patterns = com.google.common.collect.ImmutableSet.of(pattern);
                // synthetic phase, no debug log
                // synthetic phase, no debug log
                phases.add(
                    new ComponentExecutionPhase(
                        syntheticId++,
                        Type.DAG,
                        patterns,
                        com.google.common.collect.ImmutableList.of()));
                assignedPatterns.add(pattern);
            }
        }
        OrderedSchedule ordered = orderByExecutableDependencies(phases, provenance, executableTasks);
        return new ECOExecutionSchedule(ordered.phases(), ordered.dependencies());
    }

    /**
     * Numeric planning may switch to an alternate producer after the structural route was selected. Rebuild
     * phase edges from the final physical patterns so dependencies introduced by that alternate cannot remain
     * behind their consumers in the stale component order.
     */
    private static OrderedSchedule orderByExecutableDependencies(List<ComponentExecutionPhase> phases,
        ExecutionProvenance provenance, List<ICraftingPatternDetails> plannedTasks) {
        Map<Object, Set<Integer>> producersByKey = new LinkedHashMap<>();
        Map<Object, Set<Integer>> primaryProducersByKey = new LinkedHashMap<>();
        Map<ICraftingPatternDetails, Integer> phaseOfPattern = new java.util.IdentityHashMap<>();
        Map<Integer, Integer> phaseOfComponent = new LinkedHashMap<>();
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            phaseOfComponent.put(
                phases.get(phaseIndex)
                    .componentId(),
                phaseIndex);
            for (ICraftingPatternDetails pattern : phases.get(phaseIndex)
                .patternSet()) {
                phaseOfPattern.put(pattern, phaseIndex);
                for (var output : producedOutputs(pattern)) {
                    if (output == null || output.what() == null || output.amount() <= 0L) continue;
                    producersByKey.computeIfAbsent(output.what(), ignored -> new LinkedHashSet<>())
                        .add(phaseIndex);
                }
                GenericStack primary = primaryOutput(pattern);
                if (primary != null && primary.what() != null && primary.amount() > 0L) {
                    primaryProducersByKey.computeIfAbsent(primary.what(), ignored -> new LinkedHashSet<>())
                        .add(phaseIndex);
                }
            }
        }

        List<Set<Integer>> outgoing = new ArrayList<>(phases.size());
        int[] indegree = new int[phases.size()];
        Map<PhaseDependency, Set<String>> edgeKeys = new LinkedHashMap<>();
        for (int i = 0; i < phases.size(); i++) outgoing.add(new LinkedHashSet<>());
        for (int consumer = 0; consumer < phases.size(); consumer++) {
            int consumerPhase = consumer;
            for (ICraftingPatternDetails pattern : phases.get(consumer)
                .patternSet()) {
                PatternSemantics semantics = semantic(pattern);
                for (var input : semantics.consumedInputs()) {
                    Object key = input.key();
                    if (provenance == null || !provenance.covers(key)) {
                        Set<Integer> fallbackProducers = primaryProducersByKey.getOrDefault(
                            key,
                            producersByKey.getOrDefault(key, com.google.common.collect.ImmutableSet.of()));
                        for (int producer : fallbackProducers) {
                            addAttributedDependency(
                                phases,
                                outgoing,
                                indegree,
                                edgeKeys,
                                producer,
                                consumerPhase,
                                pattern,
                                key,
                                semantics,
                                "fallback");
                        }
                        continue;
                    }
                    for (MaterialSource source : provenance.suppliersOf(key)) {
                        Integer producer = null;
                        String kind;
                        if (source instanceof MaterialSource.PatternOutput output) {
                            producer = matchingPhase(output.pattern(), phaseOfPattern);
                            kind = output.primary() ? "primary of " + output.pattern()
                                : "byproduct of " + output.pattern();
                            if (producer == null && containsPhysicalPattern(plannedTasks, output.pattern())) {
                                throw new IllegalStateException(
                                    "Attributed supplier has no phase: key=" + key + " pattern=" + output.pattern());
                            }
                        } else if (source instanceof MaterialSource.CycleOutput output) {
                            producer = phaseOfComponent.get(output.componentId());
                            kind = "cycle " + output.componentId();
                        } else {
                            continue;
                        }
                        if (producer != null) addAttributedDependency(
                            phases,
                            outgoing,
                            indegree,
                            edgeKeys,
                            producer,
                            consumerPhase,
                            pattern,
                            key,
                            semantics,
                            kind);
                    }
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < indegree.length; i++) if (indegree[i] == 0) ready.add(i);
        List<ComponentExecutionPhase> ordered = new ArrayList<>(phases.size());
        List<Integer> orderedIndices = new ArrayList<>(phases.size());
        while (!ready.isEmpty()) {
            int phaseIndex = ready.remove();
            ordered.add(phases.get(phaseIndex));
            orderedIndices.add(phaseIndex);
            for (int dependent : outgoing.get(phaseIndex)) {
                if (--indegree[dependent] == 0) ready.add(dependent);
            }
        }
        if (ordered.size() != phases.size()) {
            String cycles = describeResidualCycles(phases, outgoing, edgeKeys, orderedIndices);
            throw new IllegalStateException(
                "Final executable pattern dependencies contain a cycle outside a solved cycle phase: " + cycles);
        }
        int[] remapped = new int[phases.size()];
        for (int i = 0; i < orderedIndices.size(); i++) remapped[orderedIndices.get(i)] = i;
        List<PhaseDependency> dependencies = new ArrayList<>();
        for (int producer = 0; producer < outgoing.size(); producer++) {
            for (int consumer : outgoing.get(producer)) {
                dependencies.add(new PhaseDependency(remapped[producer], remapped[consumer]));
            }
        }
        dependencies.sort(
            java.util.Comparator.comparingInt(PhaseDependency::consumerPhase)
                .thenComparingInt(PhaseDependency::producerPhase));
        validateScheduleTopology(dependencies);
        return new OrderedSchedule(
            com.google.common.collect.ImmutableList.copyOf(ordered),
            com.google.common.collect.ImmutableList.copyOf(dependencies));
    }

    private static final class OrderedSchedule {

        private final List<ComponentExecutionPhase> phases;

        public List<ComponentExecutionPhase> phases() {
            return phases;
        }

        private final List<PhaseDependency> dependencies;

        public List<PhaseDependency> dependencies() {
            return dependencies;
        }

        public OrderedSchedule(List<ComponentExecutionPhase> phases, List<PhaseDependency> dependencies) {
            this.phases = phases;
            this.dependencies = dependencies;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof OrderedSchedule)) return false;
            OrderedSchedule other = (OrderedSchedule) value;
            return java.util.Objects.equals(this.phases, other.phases)
                && java.util.Objects.equals(this.dependencies, other.dependencies);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(phases, dependencies);
        }
    }

    /**
     * Names every residual cycle: its member phases with their patterns, and each intra-cycle edge with the
     * item keys that created it. The scheduler derives edges from every physical output, so a phase that
     * re-emits an upstream material as a byproduct shows up here as an edge back into its own supplier.
     */
    private static String describeResidualCycles(List<ComponentExecutionPhase> phases, List<Set<Integer>> outgoing,
        Map<PhaseDependency, Set<String>> edgeKeys, List<Integer> orderedIndices) {
        Set<Integer> remaining = new LinkedHashSet<>();
        for (int i = 0; i < phases.size(); i++) if (!orderedIndices.contains(i)) remaining.add(i);
        List<List<Integer>> cycles = stronglyConnectedComponents(remaining, outgoing).stream()
            .filter(scc -> scc.size() > 1)
            .collect(java.util.stream.Collectors.toList());
        StringBuilder text = new StringBuilder("remainingPhases=").append(remaining.size())
            .append(" cyclicGroups=")
            .append(cycles.size());
        for (int group = 0; group < cycles.size(); group++) {
            List<Integer> members = new ArrayList<>(cycles.get(group));
            members.sort(null);
            text.append("; cycle#")
                .append(group)
                .append(" phases=[");
            for (int i = 0; i < members.size(); i++) {
                ComponentExecutionPhase phase = phases.get(members.get(i));
                if (i > 0) text.append(", ");
                text.append('p')
                    .append(members.get(i))
                    .append('(')
                    .append(phase.type())
                    .append(" component=")
                    .append(phase.componentId())
                    .append(" patterns=")
                    .append(phase.patternSet())
                    .append(')');
            }
            text.append("] edges=[");
            boolean first = true;
            for (int producer : members) {
                for (int consumer : outgoing.get(producer)) {
                    if (!members.contains(consumer)) continue;
                    if (!first) text.append(", ");
                    first = false;
                    text.append('p')
                        .append(producer)
                        .append("->p")
                        .append(consumer)
                        .append(" via ")
                        .append(
                            edgeKeys.getOrDefault(
                                new PhaseDependency(producer, consumer),
                                com.google.common.collect.ImmutableSet.of()));
                }
            }
            text.append(']');
        }
        return text.toString();
    }

    private static List<List<Integer>> stronglyConnectedComponents(Set<Integer> nodes, List<Set<Integer>> outgoing) {
        List<List<Integer>> result = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>(), low = new HashMap<>();
        Set<Integer> onStack = new HashSet<>();
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        int[] next = { 0 };
        for (int node : nodes)
            if (!index.containsKey(node)) tarjan(node, nodes, outgoing, index, low, onStack, stack, next, result);
        return result;
    }

    private static void tarjan(int v, Set<Integer> nodes, List<Set<Integer>> outgoing, Map<Integer, Integer> index,
        Map<Integer, Integer> low, Set<Integer> onStack, java.util.ArrayDeque<Integer> stack, int[] next,
        List<List<Integer>> result) {
        index.put(v, next[0]);
        low.put(v, next[0]++);
        stack.push(v);
        onStack.add(v);
        for (int w : outgoing.get(v)) if (nodes.contains(w)) {
            if (!index.containsKey(w)) {
                tarjan(w, nodes, outgoing, index, low, onStack, stack, next, result);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) low.put(v, Math.min(low.get(v), index.get(w)));
        }
        if (low.get(v)
            .equals(index.get(v))) {
            List<Integer> scc = new ArrayList<>();
            int w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (w != v);
            result.add(scc);
        }
    }

    private static boolean addDependency(List<Set<Integer>> outgoing, int[] indegree, int producer, int consumer) {
        if (producer == consumer) return false;
        if (outgoing.get(producer)
            .add(consumer)) {
            indegree[consumer]++;
            return true;
        }
        return false;
    }

    private static void addAttributedDependency(List<ComponentExecutionPhase> phases, List<Set<Integer>> outgoing,
        int[] indegree, Map<PhaseDependency, Set<String>> edgeKeys, int producer, int consumer,
        ICraftingPatternDetails consumerPattern, Object key, PatternSemantics semantics, String source) {
        if (producer == consumer) return;
        edgeKeys.computeIfAbsent(new PhaseDependency(producer, consumer), ignored -> new LinkedHashSet<>())
            .add(key + " (" + source + ")");
        addDependency(outgoing, indegree, producer, consumer);
    }

    private static Integer matchingPhase(ICraftingPatternDetails pattern,
        Map<ICraftingPatternDetails, Integer> phaseOfPattern) {
        Integer direct = phaseOfPattern.get(pattern);
        if (direct != null) return direct;
        Integer match = null;
        for (var entry : phaseOfPattern.entrySet()) {
            if (!PlanIdentity.samePattern(entry.getKey(), pattern)) continue;
            if (match != null && !match.equals(entry.getValue())) {
                throw new IllegalStateException("Attributed pattern is owned by multiple phases: " + pattern);
            }
            match = entry.getValue();
        }
        return match;
    }

    private static ICraftingPatternDetails executablePattern(ICraftingPatternDetails source,
        List<ICraftingPatternDetails> executableTasks, boolean requirePlannedTask) {
        if (source == null) return null;
        for (ICraftingPatternDetails task : executableTasks) if (task == source) return task;
        List<ICraftingPatternDetails> matches = executableTasks.stream()
            .filter(task -> PlanIdentity.samePattern(source, task))
            .collect(java.util.stream.Collectors.toList());
        if (matches.size() == 1) return matches.get(0);
        return requirePlannedTask ? null : source;
    }

    private static PatternSemantics semantic(ICraftingPatternDetails pattern) {
        return new cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.GtnhPatternSemanticAdapter()
            .analyze(pattern);
    }

    /** True recipe outputs only. Input remainders are deliberately not phase producers. */
    private static List<GenericStack> producedOutputs(ICraftingPatternDetails pattern) {
        PatternSemantics semantics = semantic(pattern);
        List<GenericStack> result = new ArrayList<>(semantics.producedOutputs());
        GenericStack primary = primaryOutput(pattern);
        if (primary != null) result.add(primary);
        return result;
    }

    private static GenericStack primaryOutput(ICraftingPatternDetails pattern) {
        try {
            var outputs = pattern.getCondensedAEOutputs();
            if (outputs == null || outputs.length == 0 || outputs[0] == null) return null;
            return new GenericStack(
                new cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey(outputs[0]),
                outputs[0].getStackSize());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void validateScheduleTopology(List<PhaseDependency> dependencies) {
        for (PhaseDependency dependency : dependencies) {
            if (dependency.producerPhase() < dependency.consumerPhase()) continue;
            throw new IllegalStateException(
                "Executable dependency is not in supplier-to-consumer order: " + dependency);
        }
    }

    private static void addPhysicalPattern(List<ICraftingPatternDetails> patterns, ICraftingPatternDetails candidate) {
        if (candidate != null && !containsPhysicalPattern(patterns, candidate)) patterns.add(candidate);
    }

    private static boolean containsPhysicalPattern(Iterable<ICraftingPatternDetails> patterns,
        ICraftingPatternDetails candidate) {
        for (ICraftingPatternDetails pattern : patterns) {
            if (PlanIdentity.samePattern(pattern, candidate)) return true;
        }
        return false;
    }
}
