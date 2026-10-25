package cn.dancingsnow.neoecoae.crafting.planner.graph;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Amount-free material vertex in the goal-reachable recipe graph. */
@com.github.bsideup.jabel.Desugar
public record CraftingGraphNode(AEKey key, List<CompiledPattern> candidatePatterns) {

    public CraftingGraphNode {

    }
}
