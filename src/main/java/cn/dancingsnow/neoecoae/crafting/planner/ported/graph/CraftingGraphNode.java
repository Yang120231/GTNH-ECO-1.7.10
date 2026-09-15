package cn.dancingsnow.neoecoae.crafting.planner.ported.graph;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;

/** Amount-free material vertex in the goal-reachable recipe graph. */
public final class CraftingGraphNode {

    private final Object key;

    public Object key() {
        return key;
    }

    private final List<CompiledPattern> candidatePatterns;

    public List<CompiledPattern> candidatePatterns() {
        return candidatePatterns;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CraftingGraphNode)) return false;
        CraftingGraphNode other = (CraftingGraphNode) value;
        return java.util.Objects.equals(key, other.key)
            && java.util.Objects.equals(candidatePatterns, other.candidatePatterns);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key, candidatePatterns);
    }

    public CraftingGraphNode(Object key, List<CompiledPattern> candidatePatterns) {
        candidatePatterns = com.google.common.collect.ImmutableList.copyOf(candidatePatterns);

        this.key = key;
        this.candidatePatterns = candidatePatterns;
    }
}
