package cn.dancingsnow.neoecoae.crafting.planner.graph;

import java.util.List;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** One maximal strongly-connected set returned by Tarjan. */
@com.github.bsideup.jabel.Desugar
public record SccComponent(int componentId, List<AEKey> members, List<CraftingGraphEdge> internalEdges,
    boolean cyclic) {

    public SccComponent {

        if (members.isEmpty()) throw new IllegalArgumentException("SCC must have at least one member");
    }
}
