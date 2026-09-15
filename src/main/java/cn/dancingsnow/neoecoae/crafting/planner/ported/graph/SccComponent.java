package cn.dancingsnow.neoecoae.crafting.planner.ported.graph;

import java.util.List;

/** One maximal strongly-connected set returned by Tarjan. */
public final class SccComponent {

    private final int componentId;

    public int componentId() {
        return componentId;
    }

    private final List<Object> members;

    public List<Object> members() {
        return members;
    }

    private final List<CraftingGraphEdge> internalEdges;

    public List<CraftingGraphEdge> internalEdges() {
        return internalEdges;
    }

    private final boolean cyclic;

    public boolean cyclic() {
        return cyclic;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof SccComponent)) return false;
        SccComponent other = (SccComponent) value;
        return java.util.Objects.equals(componentId, other.componentId)
            && java.util.Objects.equals(members, other.members)
            && java.util.Objects.equals(internalEdges, other.internalEdges)
            && java.util.Objects.equals(cyclic, other.cyclic);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(componentId, members, internalEdges, cyclic);
    }

    public SccComponent(int componentId, List<Object> members, List<CraftingGraphEdge> internalEdges, boolean cyclic) {
        members = com.google.common.collect.ImmutableList.copyOf(members);
        internalEdges = com.google.common.collect.ImmutableList.copyOf(internalEdges);
        if (members.isEmpty()) throw new IllegalArgumentException("SCC must have at least one member");

        this.componentId = componentId;
        this.members = members;
        this.internalEdges = internalEdges;
        this.cyclic = cyclic;
    }
}
