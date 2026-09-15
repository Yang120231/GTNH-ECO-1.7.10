package cn.dancingsnow.neoecoae.crafting.planner.ported.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.crafting.planner.ported.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.ported.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.crafting.planner.ported.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.ported.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.ported.component.PlanningComponent;

/** SCC contraction result. Its component graph is validated and topologically ordered. */
public final class CondensationGraph {

    private static final class Pair {

        private final int from;

        public int from() {
            return from;
        }

        private final int to;

        public int to() {
            return to;
        }

        public Pair(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Pair)) return false;
            Pair other = (Pair) value;
            return java.util.Objects.equals(from, other.from) && java.util.Objects.equals(to, other.to);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(from, to);
        }
    }

    private final CraftingDependencyGraph source;
    private final Map<Integer, PlanningComponent> components;
    private final Map<Object, Integer> componentByKey;
    private final List<ComponentDependency> dependencies;
    private final List<PlanningComponent> topologicalOrder;

    private CondensationGraph(CraftingDependencyGraph source, Map<Integer, PlanningComponent> components,
        Map<Object, Integer> componentByKey, List<ComponentDependency> dependencies,
        List<PlanningComponent> topologicalOrder) {
        this.source = source;
        this.components = com.google.common.collect.ImmutableMap.copyOf(components);
        this.componentByKey = com.google.common.collect.ImmutableMap.copyOf(componentByKey);
        this.dependencies = com.google.common.collect.ImmutableList.copyOf(dependencies);
        this.topologicalOrder = com.google.common.collect.ImmutableList.copyOf(topologicalOrder);
    }

    public static CondensationGraph build(CraftingDependencyGraph graph, List<SccComponent> sccs,
        ECOCancellation cancellation) throws InterruptedException {
        Map<Object, Integer> componentByKey = new HashMap<>();
        for (SccComponent scc : sccs) {
            cancellation.checkpoint();
            for (Object member : scc.members()) componentByKey.put(member, scc.componentId());
        }

        Map<Pair, List<CraftingGraphEdge>> grouped = new LinkedHashMap<>();
        for (CraftingGraphEdge edge : graph.edges()) {
            cancellation.checkpoint();
            int from = componentByKey.get(edge.producer());
            int to = componentByKey.get(edge.requiredInput());
            if (from != to) grouped.computeIfAbsent(new Pair(from, to), ignored -> new ArrayList<>())
                .add(edge);
        }
        List<ComponentDependency> dependencies = grouped.entrySet()
            .stream()
            .map(
                entry -> new ComponentDependency(
                    entry.getKey()
                        .from(),
                    entry.getKey()
                        .to(),
                    entry.getValue()))
            .collect(java.util.stream.Collectors.toList());

        Map<Integer, List<ComponentDependency>> incoming = new HashMap<>();
        Map<Integer, List<ComponentDependency>> outgoing = new HashMap<>();
        for (ComponentDependency dependency : dependencies) {
            outgoing.computeIfAbsent(dependency.fromComponentId(), ignored -> new ArrayList<>())
                .add(dependency);
            incoming.computeIfAbsent(dependency.toComponentId(), ignored -> new ArrayList<>())
                .add(dependency);
        }

        Map<Integer, PlanningComponent> components = new LinkedHashMap<>();
        for (SccComponent scc : sccs) {
            cancellation.checkpoint();
            LinkedHashSet<CompiledPattern> patterns = new LinkedHashSet<>();
            if (scc.cyclic()) {
                for (CraftingGraphEdge edge : scc.internalEdges()) patterns.add(edge.pattern());
            } else {
                for (Object member : scc.members()) patterns.addAll(
                    graph.nodes()
                        .get(member)
                        .candidatePatterns());
            }
            PlanningComponent component = scc.cyclic()
                ? new CycleComponent(
                    scc.componentId(),
                    scc.members(),
                    com.google.common.collect.ImmutableList.copyOf(patterns),
                    scc.internalEdges(),
                    incoming.getOrDefault(scc.componentId(), com.google.common.collect.ImmutableList.of()),
                    outgoing.getOrDefault(scc.componentId(), com.google.common.collect.ImmutableList.of()))
                : new AcyclicComponent(
                    scc.componentId(),
                    scc.members()
                        .get(0),
                    com.google.common.collect.ImmutableList.copyOf(patterns));
            components.put(scc.componentId(), component);
        }

        Map<Integer, Integer> indegree = new HashMap<>();
        for (Integer id : components.keySet()) indegree.put(id, 0);
        for (ComponentDependency edge : dependencies) indegree.merge(edge.toComponentId(), 1, Integer::sum);
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (Integer id : components.keySet()) if (indegree.get(id) == 0) ready.addLast(id);
        List<PlanningComponent> order = new ArrayList<>(components.size());
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            int id = ready.removeFirst();
            order.add(components.get(id));
            for (ComponentDependency edge : outgoing.getOrDefault(id, com.google.common.collect.ImmutableList.of())) {
                int remaining = indegree.merge(edge.toComponentId(), -1, Integer::sum);
                if (remaining == 0) ready.addLast(edge.toComponentId());
            }
        }
        if (order.size() != components.size()) {
            throw new IllegalStateException("SCC condensation graph must be acyclic");
        }
        return new CondensationGraph(graph, components, componentByKey, dependencies, order);
    }

    public CraftingDependencyGraph source() {
        return source;
    }

    public Map<Integer, PlanningComponent> components() {
        return components;
    }

    public PlanningComponent componentFor(Object key) {
        return components.get(componentByKey.get(key));
    }

    public List<ComponentDependency> dependencies() {
        return dependencies;
    }

    public List<PlanningComponent> topologicalOrder() {
        return topologicalOrder;
    }

    /** Execution order follows supplier -> consumer, opposite of producer->required-input edges. */
    public List<PlanningComponent> executionOrder() {
        List<PlanningComponent> result = new ArrayList<>(topologicalOrder);
        java.util.Collections.reverse(result);
        return com.google.common.collect.ImmutableList.copyOf(result);
    }

    public List<CycleComponent> cycles() {
        return topologicalOrder.stream()
            .filter(CycleComponent.class::isInstance)
            .map(CycleComponent.class::cast)
            .collect(java.util.stream.Collectors.toList());
    }
}
