package cn.dancingsnow.neoecoae.crafting.planner.trace;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Complete SCC explanation for the cycle graph UI, including the solver's own answer when it ran. */
@com.github.bsideup.jabel.Desugar
public record CycleTrace(int componentId, List<AEKey> members, List<CraftingGraphEdge> internalEdges,
    List<CraftingGraphEdge> externalEdges, Map<AEKey, Long> requiredOutputs, CyclePlanningStatus status,
    @Nullable CycleSolveResult solveResult) {

    public CycleTrace(int componentId, List<AEKey> members, List<CraftingGraphEdge> internalEdges,
        Map<AEKey, Long> requiredOutputs, CyclePlanningStatus status) {
        this(
            componentId,
            members,
            internalEdges,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            requiredOutputs,
            status,
            null);
    }

    public CycleTrace {

    }
}
