package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ComponentExternalDemandCacheTest {

    @Test
    void repeatedExternalShortageIsSolvedOnceAndDoesNotHideAFeasibleAlternative() throws Exception {
        AEKey goal = key("goal"), seed = key("seed"), boundary = key("boundary"), raw = key("raw"), fuel = key("fuel");
        var first = pattern(0, goal, 1L, new GenericStack(seed, 2L));
        var second = pattern(1, goal, 1L, new GenericStack(seed, 2L));
        var third = pattern(2, goal, 1L, new GenericStack(seed, 2L));
        var feasible = pattern(3, goal, 1L, new GenericStack(fuel, 1L));
        var growth = pattern(4, seed, 2L, new GenericStack(seed, 1L), new GenericStack(boundary, 1L));
        var external = pattern(5, boundary, 1L, new GenericStack(raw, 2L));
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, second, third, feasible),
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth),
                boundary,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(external),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                fuel,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            6,
            8);
        var dependencyGraph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var graph = CondensationGraph.build(
            dependencyGraph,
            new TarjanSccAnalyzer().analyze(dependencyGraph, ECOCancellation.NONE),
            ECOCancellation.NONE);
        var initial = new ActiveRouteSelector().selectWithChoices(
            dependencyGraph,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 0),
            ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 1L);
        stock.add(raw, 1L);
        stock.add(fuel, 1L);
        var solver = spy(new AcyclicCraftingSolver());
        var planner = new ComponentPlanner(solver, new BoundedCycleSolver(), 4);

        var result = planner.planWithCycleFallback(
            network,
            graph,
            initial,
            stock,
            PlannerInventorySnapshot.of(stock),
            1L,
            false,
            ECOCancellation.NONE);

        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(feasible.details(), 1L),
            result.state()
                .patternTimes());
        verify(solver, times(4)).solve(
            same(network),
            any(),
            any(PlannerInventorySnapshot.class),
            eq(1L),
            anyMap(),
            anySet(),
            eq(false),
            any());
        verify(solver).solveDemands(
            any(),
            any(),
            eq(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(boundary, 1L)),
            anyMap(),
            anySet(),
            eq(false),
            any());
        // The missing original is solved once; optional seed retention cannot run before a whole-order success.
        verify(solver, times(1)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
        assertEquals(1L, stock.get(seed));
        assertEquals(1L, stock.get(raw));
        assertEquals(1L, stock.get(fuel));

        // A new request can observe changed stock and must not inherit the previous missing proof.
        stock.add(raw, 1L);
        var recovered = planner.planWithCycleFallback(
            network,
            graph,
            initial,
            stock,
            PlannerInventorySnapshot.of(stock),
            1L,
            false,
            ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            recovered.status(),
            recovered.trace()
                .diagnostics()
                .toString());
        assertTrue(
            recovered.state()
                .patternTimes()
                .containsKey(growth.details()));
        assertTrue(
            recovered.state()
                .patternTimes()
                .containsKey(external.details()));
        // Again one extra solve for the seed-retaining candidate ({boundary=2} fails on raw=2) plus the accepted
        // {boundary=1}.
        verify(solver, times(3)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, amount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            outputs,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        return new CompiledPattern(
            id,
            details,
            output,
            PlannerAmount.of(amount),
            java.util.Arrays.stream(inputs)
                .map(input -> new CompiledInput(null, input.what(), input.amount(), true, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            outputs,
            true,
            null,
            false,
            semantics);
    }
}
