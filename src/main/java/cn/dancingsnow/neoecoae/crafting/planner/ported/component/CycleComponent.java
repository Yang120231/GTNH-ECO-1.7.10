package cn.dancingsnow.neoecoae.crafting.planner.ported.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.CraftingGraphEdge;

/** A complete cyclic SCC, isolated from the ordinary DAG numeric solver. */
public final class CycleComponent implements PlanningComponent {

    private final int componentId;

    public int componentId() {
        return componentId;
    }

    private final List<Object> members;

    public List<Object> members() {
        return members;
    }

    private final List<CompiledPattern> patterns;

    public List<CompiledPattern> patterns() {
        return patterns;
    }

    private final List<CraftingGraphEdge> internalEdges;

    public List<CraftingGraphEdge> internalEdges() {
        return internalEdges;
    }

    private final List<ComponentDependency> incomingDependencies;

    public List<ComponentDependency> incomingDependencies() {
        return incomingDependencies;
    }

    private final List<ComponentDependency> outgoingDependencies;

    public List<ComponentDependency> outgoingDependencies() {
        return outgoingDependencies;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CycleComponent)) return false;
        CycleComponent other = (CycleComponent) value;
        return java.util.Objects.equals(componentId, other.componentId)
            && java.util.Objects.equals(members, other.members)
            && java.util.Objects.equals(patterns, other.patterns)
            && java.util.Objects.equals(internalEdges, other.internalEdges)
            && java.util.Objects.equals(incomingDependencies, other.incomingDependencies)
            && java.util.Objects.equals(outgoingDependencies, other.outgoingDependencies);
    }

    @Override
    public int hashCode() {
        return java.util.Objects
            .hash(componentId, members, patterns, internalEdges, incomingDependencies, outgoingDependencies);
    }

    public CycleComponent(int componentId, List<Object> members, List<CompiledPattern> patterns,
        List<CraftingGraphEdge> internalEdges, List<ComponentDependency> incomingDependencies,
        List<ComponentDependency> outgoingDependencies) {
        members = com.google.common.collect.ImmutableList.copyOf(members);
        patterns = com.google.common.collect.ImmutableList.copyOf(patterns);
        internalEdges = com.google.common.collect.ImmutableList.copyOf(internalEdges);
        incomingDependencies = com.google.common.collect.ImmutableList.copyOf(incomingDependencies);
        outgoingDependencies = com.google.common.collect.ImmutableList.copyOf(outgoingDependencies);
        if (members.isEmpty()) throw new IllegalArgumentException("Cycle component must not be empty");

        this.componentId = componentId;
        this.members = members;
        this.patterns = patterns;
        this.internalEdges = internalEdges;
        this.incomingDependencies = incomingDependencies;
        this.outgoingDependencies = outgoingDependencies;
    }
}
