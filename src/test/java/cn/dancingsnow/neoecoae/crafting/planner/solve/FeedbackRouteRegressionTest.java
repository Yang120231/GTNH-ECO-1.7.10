package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlanBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class FeedbackRouteRegressionTest {

    private final AEKey a = key("seed");
    private final AEKey b = key("intermediate");
    private final AEKey c = key("byproduct");
    private final AEKey goal = key("goal");

    @Test
    void byproductFeedbackNeedsStartupStockAndIsNotADag() throws Exception {
        var finish = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L, a, 1L));
        var convert = pattern(
            1,
            b,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L));
        var network = network(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finish),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(convert),
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var graph = graph(network);
        assertEquals(
            1,
            graph.cycles()
                .size());
        assertFalse(
            new ActiveRouteSelector().select(graph.source(), ECOCancellation.NONE)
                .acyclic());

        var missing = planner().plan(network, graph, new KeyCounter(), 2L, true, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, missing.status());
        assertFalse(
            missing.state()
                .missingItems()
                .isEmpty());
        assertTrue(
            missing.state()
                .patternTimes()
                .isEmpty());

        var stock = new KeyCounter();
        stock.add(a, 1L);
        var solved = planner().plan(network, graph, stock, 2L, true, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            solved.status(),
            solved.trace()
                .diagnostics()
                .toString());
        assertEquals(
            2L,
            solved.state()
                .patternTimes()
                .get(finish.details()));
        assertEquals(
            2L,
            solved.state()
                .patternTimes()
                .get(convert.details()));
    }

    @Test
    void ordinaryByproductDoesNotCreateACycleOrBecomeAStandaloneProducer() throws Exception {
        var recipe = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L, c, 1L));
        var network = network(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(recipe),
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        assertTrue(
            graph(network).cycles()
                .isEmpty());
        assertTrue(
            network.producersOf(c)
                .isEmpty());
        var stock = new KeyCounter();
        stock.add(a, 2L);
        assertEquals(
            PlanningStatus.SUCCESS,
            planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE)
                .status());
    }

    @Test
    void threeRecipeWitnessSurvivesRuntimeConversionAndProtectsTheOnlySeed() throws Exception {
        var prepare = pattern(
            0,
            b,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L, c, 1L));
        var grow = pattern(
            1,
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L, c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L));
        var finish = pattern(
            2,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L, c, 1L));
        var network = network(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finish),
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(grow),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(prepare),
                c,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var stock = new KeyCounter();
        stock.add(a, 1L);
        var solved = planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            solved.status(),
            solved.trace()
                .diagnostics()
                .toString());
        assertEquals(
            ECOExecutionRequirement.ORDERED,
            ECOExecutionRequirement.classify(
                solved.components(),
                solved.state()
                    .patternTimes()));
        Map<AEKey, Long> used = new HashMap<>();
        solved.state()
            .usedItems()
            .forEach(entry -> used.put(entry.getKey(), entry.getLongValue()));
        var signature = new PlanIdentity.Signature(
            goal,
            2L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            used,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var plan = ECOExecutionPlanBuilder.build(
            signature,
            ExecutionMode.ORDERED_CYCLE,
            solved.components(),
            solved.executionComponentOrder(),
            solved.state()
                .patternTimes());
        Map<Integer, IPatternDetails> patterns = new HashMap<>();
        plan.tasks()
            .forEach(task -> patterns.put(task.id(), task.pattern()));
        var runtime = new ECOExecutionRuntime(plan, patterns);
        Map<IPatternDetails, Long> remaining = new HashMap<>(
            solved.state()
                .patternTimes());
        Map<AEKey, Long> actualStock = new HashMap<>(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L));
        Map<IPatternDetails, CompiledPattern> compiled = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .mapOf(prepare.details(), prepare, grow.details(), grow, finish.details(), finish);
        int firings = 0;
        while (!runtime.isComplete(remaining)) {
            assertTrue(firings++ < 20, "Schedule failed to finish");
            var candidates = runtime.candidates(remaining);
            assertEquals(1, candidates.size(), "Only the verified next transition may spend the seed");
            var candidate = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(candidates);
            if (firings == 1) assertSame(prepare.details(), candidate.pattern());
            var recipe = compiled.get(candidate.pattern());
            var consumed = new KeyCounter();
            for (var input : recipe.inputs()) {
                long amount = input.amountPerPattern()
                    .longValueExact();
                assertTrue(actualStock.getOrDefault(input.key(), 0L) >= amount, "Runtime exhausted its seed");
                actualStock.merge(input.key(), -amount, Long::sum);
                consumed.add(input.key(), amount);
            }
            recipe.grossOutputs()
                .forEach(output -> actualStock.merge(output.what(), output.amount(), Long::sum));
            remaining.merge(candidate.pattern(), -1L, Long::sum);
            runtime.onAccepted(candidate, 1L, new KeyCounter[] { consumed });
        }
        assertEquals(2L, actualStock.get(goal));
    }

    @Test
    void alternateMultiRecipeCycleIsReclassifiedAfterDownstreamFailure() throws Exception {
        var unavailable = key("unavailable");
        var first = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(unavailable, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L));
        var second = pattern(
            1,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 2L));
        var reverse = pattern(
            2,
            b,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L));
        var network = network(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, second),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(reverse),
                unavailable,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var stock = new KeyCounter();
        stock.add(goal, 1L);
        var solved = planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            solved.status(),
            solved.trace()
                .diagnostics()
                .toString());
        assertFalse(
            solved.state()
                .patternTimes()
                .containsKey(first.details()));
        assertFalse(
            solved.cycles()
                .isEmpty());
        assertEquals(
            2L,
            solved.state()
                .patternTimes()
                .get(second.details()));
        assertEquals(
            2L,
            solved.state()
                .patternTimes()
                .get(reverse.details()));
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    @Test
    void choosesCWhenTheRecipeUsingBHasNoMaterials() throws Exception {
        var fromB = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L));
        var fromC = pattern(
            1,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L));
        var network = network(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(fromB, fromC),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                c,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var stock = new KeyCounter();
        stock.add(c, 2L);
        for (boolean cyclesEnabled : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(false, true)) {
            var solved = planner().plan(network, graph(network), stock, 2L, cyclesEnabled, ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, solved.status());
            assertEquals(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(fromC.details(), 2L),
                solved.state()
                    .patternTimes());
            assertEquals(
                2L,
                solved.state()
                    .usedItems()
                    .get(c));
        }
    }

    @Test
    void blockedCyclicAlternativeDoesNotHideALaterUsableRecipe() throws Exception {
        var missing = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L));
        var cyclic = pattern(
            1,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 2L));
        var reverse = pattern(
            2,
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L));
        var usable = pattern(
            3,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L));
        for (var candidates : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(missing, cyclic, usable),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(cyclic, missing, usable))) {
            var network = network(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                    goal,
                    candidates,
                    a,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(reverse),
                    b,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                    c,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
            var stock = new KeyCounter();
            stock.add(c, 2L);
            var planner = planner();
            var graph = graph(network);
            var solved = planner.planWithCycleFallback(
                network,
                graph,
                planner.selectRoutes(graph, true, ECOCancellation.NONE),
                stock,
                PlannerInventorySnapshot.of(stock),
                2L,
                false,
                ECOCancellation.NONE);
            assertEquals(
                PlanningStatus.SUCCESS,
                solved.status(),
                solved.trace()
                    .diagnostics()
                    .toString());
            if (solved.state()
                .patternTimes()
                .containsKey(cyclic.details())) {
                // The later producer can now supply one real startup seed. This uses less C than
                // taking the direct route twice, and must still account for the bootstrap craft.
                assertEquals(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                        .mapOf(usable.details(), 1L, reverse.details(), 2L, cyclic.details(), 2L),
                    solved.state()
                        .patternTimes());
                assertEquals(
                    1L,
                    solved.state()
                        .usedItems()
                        .get(c));
            } else {
                assertEquals(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(usable.details(), 2L),
                    solved.state()
                        .patternTimes());
                assertEquals(
                    2L,
                    solved.state()
                        .usedItems()
                        .get(c));
            }
            assertNull(ECOPlanMaterialValidator.firstDeficit(solved.state(), goal, 2L, stock, network));
        }
    }

    @Test
    void alternateByproductFeedbackIsAlsoReclassifiedBeforeCreditingOutputs() throws Exception {
        var unavailable = key("unavailable");
        var first = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(unavailable, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L));
        var finish = pattern(
            1,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L, a, 1L));
        var convert = pattern(
            2,
            b,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L));
        var network = network(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, finish),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(convert),
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                unavailable,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        for (boolean seeded : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(false, true)) {
            var stock = new KeyCounter();
            if (seeded) stock.add(a, 1L);
            var solved = planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE);
            assertFalse(
                solved.cycles()
                    .isEmpty());
            if (seeded) {
                assertEquals(
                    PlanningStatus.SUCCESS,
                    solved.status(),
                    solved.trace()
                        .diagnostics()
                        .toString());
                assertEquals(
                    1L,
                    solved.state()
                        .usedItems()
                        .get(a));
            } else {
                assertNotEquals(PlanningStatus.SUCCESS, solved.status());
                assertFalse(
                    solved.state()
                        .missingItems()
                        .isEmpty());
                assertTrue(
                    solved.state()
                        .patternTimes()
                        .isEmpty());
            }
        }
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

    private static ComponentPlanner planner() {
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
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
