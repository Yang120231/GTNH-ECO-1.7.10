package cn.dancingsnow.neoecoae.crafting.planner.ported.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.CraftingGraphEdge;

/** One deduplicated edge in the SCC condensation DAG. */
public final class ComponentDependency {

    private final int fromComponentId;

    public int fromComponentId() {
        return fromComponentId;
    }

    private final int toComponentId;

    public int toComponentId() {
        return toComponentId;
    }

    private final List<CraftingGraphEdge> relationships;

    public List<CraftingGraphEdge> relationships() {
        return relationships;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ComponentDependency)) return false;
        ComponentDependency other = (ComponentDependency) value;
        return java.util.Objects.equals(fromComponentId, other.fromComponentId)
            && java.util.Objects.equals(toComponentId, other.toComponentId)
            && java.util.Objects.equals(relationships, other.relationships);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(fromComponentId, toComponentId, relationships);
    }

    public ComponentDependency(int fromComponentId, int toComponentId, List<CraftingGraphEdge> relationships) {
        relationships = com.google.common.collect.ImmutableList.copyOf(relationships);
        if (fromComponentId == toComponentId) {
            throw new IllegalArgumentException("Condensation dependencies cannot be self edges");
        }

        this.fromComponentId = fromComponentId;
        this.toComponentId = toComponentId;
        this.relationships = relationships;
    }
}
