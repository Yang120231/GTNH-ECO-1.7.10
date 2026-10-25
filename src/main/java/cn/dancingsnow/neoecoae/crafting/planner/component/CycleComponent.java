package cn.dancingsnow.neoecoae.crafting.planner.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** A complete cyclic SCC, isolated from the ordinary DAG numeric solver. */
@com.github.bsideup.jabel.Desugar
public record CycleComponent(int componentId, List<AEKey> members, List<CompiledPattern> patterns,
    List<CraftingGraphEdge> internalEdges, List<ComponentDependency> incomingDependencies,
    List<ComponentDependency> outgoingDependencies) implements PlanningComponent {

    public CycleComponent {

        if (members.isEmpty()) throw new IllegalArgumentException("Cycle component must not be empty");
    }
}
