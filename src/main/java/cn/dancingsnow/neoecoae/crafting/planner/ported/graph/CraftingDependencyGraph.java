package cn.dancingsnow.neoecoae.crafting.planner.ported.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable amount-free graph compiled only from the current goal's reachable closure. */
public final class CraftingDependencyGraph {

    private final Object goal;
    private final Map<Object, CraftingGraphNode> nodes;
    private final List<CraftingGraphEdge> edges;
    private final Map<Object, List<CraftingGraphEdge>> outgoing;

    public CraftingDependencyGraph(Object goal, Map<Object, CraftingGraphNode> nodes, List<CraftingGraphEdge> edges) {
        this.goal = goal;
        this.nodes = com.google.common.collect.ImmutableMap.copyOf(nodes);
        this.edges = com.google.common.collect.ImmutableList.copyOf(edges);
        Map<Object, List<CraftingGraphEdge>> byProducer = new LinkedHashMap<>();
        for (Object key : nodes.keySet()) byProducer.put(key, new ArrayList<>());
        for (CraftingGraphEdge edge : edges) {
            byProducer.computeIfAbsent(edge.producer(), ignored -> new ArrayList<>())
                .add(edge);
        }
        Map<Object, List<CraftingGraphEdge>> frozen = new LinkedHashMap<>();
        byProducer.forEach((key, value) -> frozen.put(key, com.google.common.collect.ImmutableList.copyOf(value)));
        this.outgoing = com.google.common.collect.ImmutableMap.copyOf(frozen);
    }

    public Object goal() {
        return goal;
    }

    public Map<Object, CraftingGraphNode> nodes() {
        return nodes;
    }

    public List<CraftingGraphEdge> edges() {
        return edges;
    }

    public List<CraftingGraphEdge> outgoing(Object key) {
        return outgoing.getOrDefault(key, com.google.common.collect.ImmutableList.of());
    }
}
