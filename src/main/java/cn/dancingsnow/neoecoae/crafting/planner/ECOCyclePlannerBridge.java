package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.crafting.planner.ported.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.CraftingDependencyGraph;
import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.CraftingGraphNode;
import cn.dancingsnow.neoecoae.crafting.planner.ported.graph.TarjanSccAnalyzer;

/** Legacy snapshot boundary. Recipe objects retain physical pattern identity during cycle solving. */
final class ECOCyclePlannerBridge<K, P> {

    private final CondensationGraph graph;

    ECOCyclePlannerBridge(K goal, Iterable<ECORecipe<K, P>> recipes, ECOCancellation cancellation)
        throws InterruptedException {
        Map<Object, List<CompiledPattern>> candidates = new LinkedHashMap<>();
        List<CraftingGraphEdge> edges = new ArrayList<>();
        int id = 0;
        for (ECORecipe<K, P> recipe : recipes) {
            cancellation.checkpoint();
            List<CompiledInput> inputs = new ArrayList<>();
            recipe.inputs.forEach((key, count) -> inputs.add(new CompiledInput(key, count)));
            List<GenericStack> outputs = new ArrayList<>();
            recipe.outputs.forEach((key, count) -> outputs.add(new GenericStack(key, count)));
            for (Map.Entry<K, Long> output : recipe.outputs.entrySet()) {
                CompiledPattern pattern = new CompiledPattern(
                    id++,
                    recipe,
                    output.getKey(),
                    output.getValue(),
                    inputs,
                    outputs);
                candidates.computeIfAbsent(output.getKey(), ignored -> new ArrayList<>())
                    .add(pattern);
                for (CompiledInput input : inputs) {
                    candidates.computeIfAbsent(input.key(), ignored -> new ArrayList<>());
                    edges.add(new CraftingGraphEdge(output.getKey(), input.key(), pattern, input));
                }
            }
        }
        candidates.computeIfAbsent(goal, ignored -> new ArrayList<>());
        Map<Object, CraftingGraphNode> nodes = new LinkedHashMap<>();
        candidates.forEach((key, patterns) -> nodes.put(key, new CraftingGraphNode(key, patterns)));
        CraftingDependencyGraph source = new CraftingDependencyGraph(goal, nodes, edges);
        graph = CondensationGraph.build(source, new TarjanSccAnalyzer().analyze(source, cancellation), cancellation);
    }

    CycleSolveResult solve(K key, long target, Map<K, Long> stock, ECOCancellation cancellation)
        throws InterruptedException {
        if (!(graph.componentFor(key) instanceof CycleComponent)) return null;
        CycleComponent component = (CycleComponent) graph.componentFor(key);
        // A single-pattern growth phase already has a compact legacy runtime representation.
        if (component.patterns()
            .stream()
            .map(CompiledPattern::details)
            .distinct()
            .count() < 2) return null;
        Map<Object, Long> inventory = new LinkedHashMap<>();
        stock.forEach(inventory::put);
        return new BoundedCycleSolver().solve(
            new CycleSolveRequest(
                component,
                Collections.singletonMap(key, target),
                inventory,
                component.outgoingDependencies(),
                new CycleSolveRequest.PlannerOptions()),
            cancellation);
    }
}
