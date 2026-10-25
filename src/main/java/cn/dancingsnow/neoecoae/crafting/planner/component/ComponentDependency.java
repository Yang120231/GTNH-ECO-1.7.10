package cn.dancingsnow.neoecoae.crafting.planner.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;

/** One deduplicated edge in the SCC condensation DAG. */
@com.github.bsideup.jabel.Desugar
public record ComponentDependency(int fromComponentId, int toComponentId, List<CraftingGraphEdge> relationships) {

    public ComponentDependency {

        if (fromComponentId == toComponentId) {
            throw new IllegalArgumentException("Condensation dependencies cannot be self edges");
        }
    }
}
