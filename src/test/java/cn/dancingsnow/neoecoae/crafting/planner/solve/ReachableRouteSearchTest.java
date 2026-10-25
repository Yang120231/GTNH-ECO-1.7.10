package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

class ReachableRouteSearchTest {

    private final AEKey goal = key("goal");

    @Test
    void routeSearchOnlyVariesProducersTheRouteActuallyReaches() throws Exception {
        var gold = key("gold");
        var c = key("c");
        var scarce = key("scarce");
        var raw = key("raw");
        var e1 = key("e1");
        var e2 = key("e2");
        var e3 = key("e3");
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    0,
                    goal,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(gold, 2L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L)),
                pattern(
                    1,
                    goal,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(c, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L))));
        producers.put(
            gold,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    2,
                    gold,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(gold, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(gold, 2L))));
        producers.put(
            c,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    3,
                    c,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(e1, 1L, e2, 1L, e3, 1L, scarce, 5L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(c, 1L))));
        int id = 4;
        for (var e : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(e1, e2, e3)) {
            producers.put(
                e,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                    pattern(
                        id++,
                        e,
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 1L),
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(e, 1L)),
                    pattern(
                        id++,
                        e,
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 1L),
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(e, 1L))));
        }
        producers.put(scarce, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        producers.put(raw, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var network = network(producers);
        var stock = new KeyCounter();
        stock.add(scarce, 1L);
        stock.add(raw, 10L);
        var graph = graph(network);
        var acyclic = spy(new AcyclicCraftingSolver());
        var cycleSolves = new java.util.concurrent.atomic.AtomicInteger();
        var cycleSolver = new BoundedCycleSolver();
        var planner = new ComponentPlanner(acyclic, (request, cancellation) -> {
            cycleSolves.incrementAndGet();
            return cycleSolver.solve(request, cancellation);
        });
        var initial = new ActiveRouteSelector().selectWithChoices(
            graph.source(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1),
            ECOCancellation.NONE);

        var result = planner.planWithCycleFallback(
            network,
            graph,
            initial,
            stock,
            PlannerInventorySnapshot.of(stock),
            1L,
            false,
            ECOCancellation.NONE);

        assertNotEquals(PlanningStatus.SUCCESS, result.status());
        // The preferred route, then goal -> blocked once (the e producers are unreachable there), then only the
        // reachable e1/e2/e3 deviations: 3 singles + 3 pairs + 1 triple. The Cartesian enumerator needed 16.
        verify(acyclic, times(9)).solve(
            same(network),
            any(),
            any(PlannerInventorySnapshot.class),
            eq(1L),
            anyMap(),
            anySet(),
            eq(false),
            any());
        assertEquals(1, cycleSolves.get(), "The seedless cycle must be solved once and then reused");
        assertEquals(1L, stock.get(scarce));
        assertEquals(10L, stock.get(raw));
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private CompiledNetwork network(Map<AEKey, List<CompiledPattern>> producers) {
        return new CompiledNetwork(
            goal,
            producers,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            producers.values()
                .stream()
                .mapToInt(List::size)
                .sum(),
            10);
    }

    private static CondensationGraph graph(CompiledNetwork network) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        return CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, AEKey primary, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = new ArrayList<GenericStack>();
        stacks.add(new GenericStack(primary, outputs.get(primary)));
        outputs.forEach((key, amount) -> { if (!key.equals(primary)) stacks.add(new GenericStack(key, amount)); });
        var rawInputs = inputs.entrySet()
            .stream()
            .map(entry -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getPossibleInputs())
                    .thenReturn(new GenericStack[] { new GenericStack(entry.getKey(), entry.getValue()) });
                when(input.getMultiplier()).thenReturn(1L);
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        when(details.getOutputs()).thenReturn(stacks);
        when(details.getPrimaryOutput()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(stacks));
        var compiled = inputs.entrySet()
            .stream()
            .map(entry -> new CompiledInput(null, entry.getKey(), entry.getValue(), true, null))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var consumed = inputs.entrySet()
            .stream()
            .map(
                entry -> new PatternSemantics.Input(
                    null,
                    entry.getKey(),
                    PlannerAmount.of(entry.getValue()),
                    null,
                    PlannerAmount.ZERO))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var semantics = new PatternSemantics(
            details,
            null,
            consumed,
            stacks,
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
            primary,
            PlannerAmount.of(outputs.get(primary)),
            compiled,
            stacks,
            true,
            null,
            false,
            semantics);
    }
}
