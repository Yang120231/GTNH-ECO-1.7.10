package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ComponentPlannerMissingSeedTest {

    @Test
    void reversibleOrderPublishesFullRawShortageToAe2() throws Exception {
        AEKey product = mock(AEKey.class, "prudentium");
        AEKey raw = mock(AEKey.class, "inferium");
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, raw))
            when(key.getAmountPerByte()).thenReturn(8);
        var upgrade = staticPattern(0, product, 1L, raw, 4L);
        var downgrade = staticPattern(1, raw, 4L, product, 1L);
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(upgrade),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(downgrade)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            2);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(raw, 19_314L);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        var outcome = planner.plan(network, condensation, stock, 640_000L, true, ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertEquals(
            2_540_686L,
            outcome.state()
                .missingItems()
                .get(raw));
        assertEquals(
            0L,
            outcome.state()
                .missingItems()
                .get(product));
        assertTrue(
            outcome.state()
                .patternTimes()
                .isEmpty(),
            "Missing cycle must not become executable");
        var plan = new AE2CraftingPlanBridge().success(product, 640_000L, true, false, outcome.state());
        var summary = CraftingPlanSummary
            .fromJob(mock(GridView.class, RETURNS_DEEP_STUBS), mock(IActionSource.class), plan);
        assertEquals(
            2_540_686L,
            summary.getEntries()
                .stream()
                .filter(
                    e -> e.getWhat()
                        .equals(raw))
                .findFirst()
                .orElseThrow(java.util.NoSuchElementException::new)
                .getMissingAmount());
    }

    @Test
    void missingSeedDoesNotHideTheWholeOrdersExternalIngredients() throws Exception {
        AEKey seed = mock(AEKey.class, "seed"), fuel = mock(AEKey.class, "fuel");
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(seed, fuel))
            when(key.getAmountPerByte()).thenReturn(8);
        var growth = staticPattern(0, seed, 2L, new GenericStack(seed, 1L), new GenericStack(fuel, 7L));
        var network = new CompiledNetwork(
            seed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth),
                fuel,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            2);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(fuel, 3L);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 100L, true, ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertEquals(
            1L,
            outcome.state()
                .missingItems()
                .get(seed));
        assertEquals(
            697L,
            outcome.state()
                .missingItems()
                .get(fuel));
        assertTrue(
            outcome.state()
                .patternTimes()
                .isEmpty());
    }

    @Test
    void toolRecipeReversibleBoneRouteReportsQuantityInsteadOfUnsupported() throws Exception {
        AEKey goal = mock(AEKey.class, "goal"), tool = mock(AEKey.class, "tool");
        AEKey block = mock(AEKey.class, "bone_block"), dust = mock(AEKey.class, "bone_meal");
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(goal, tool, block, dust))
            when(key.getAmountPerByte()).thenReturn(8);
        var input = new CompiledInput(null, tool, 1L, true, null, tool, 1L);
        var original = staticPattern(0, goal, 1L, tool, 1L);
        var semantics = new PatternSemantics(
            original.details(),
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            original.outputs(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(tool, 1L)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        var finish = new CompiledPattern(
            0,
            original.details(),
            goal,
            PlannerAmount.ONE,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(input),
            original.outputs(),
            true,
            null,
            false,
            semantics,
            new SpecialPatternAnalysis(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                    new SpecialPatternAnalysis.Requirement(input, tool, SpecialPatternAnalysis.Type.REUSABLE, 0, 0))));
        var makeTool = staticPattern(1, tool, 1L, block, 20L);
        var pack = staticPattern(2, block, 1L, dust, 9L);
        var unpack = staticPattern(3, dust, 9L, block, 1L);
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finish),
                tool,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(makeTool),
                block,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pack),
                dust,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unpack)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            4,
            4);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        var stock = new KeyCounter();
        stock.add(block, 5L);
        var result = planner.plan(network, condensation, stock, 1000L, true, ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        assertEquals(
            15L,
            result.state()
                .missingItems()
                .get(block));
        assertTrue(result.state().unsupported.isEmpty());
        stock.add(block, 15L);
        var supplied = planner.plan(network, condensation, stock, 1000L, true, ECOCancellation.NONE);
        assertEquals(cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS, supplied.status());
        assertEquals(
            1L,
            supplied.state()
                .patternTimes()
                .get(makeTool.details()));
    }

    @Test
    void missingReusableCatalystOutsideCycleGraphReachesMissingSummary() throws Exception {
        AEKey seed = mock(AEKey.class);
        AEKey catalyst = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(seed, catalyst))
            when(key.getAmountPerByte()).thenReturn(8);
        var catalystInput = new CompiledInput(null, catalyst, 1L, true, null, catalyst, 1L);
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(seed, 2L));
        IPatternDetails details = mock(IPatternDetails.class);
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            outputs,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(catalyst, 1L)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        var special = new SpecialPatternAnalysis(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new SpecialPatternAnalysis.Requirement(
                    catalystInput,
                    catalyst,
                    SpecialPatternAnalysis.Type.REUSABLE,
                    0,
                    0)));
        var growth = new CompiledPattern(
            0,
            details,
            seed,
            PlannerAmount.of(2L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new CompiledInput(null, seed, 1L, true, null), catalystInput),
            outputs,
            true,
            null,
            false,
            semantics,
            special);
        var network = new CompiledNetwork(
            seed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth),
                catalyst,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            2);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        assertFalse(
            condensation.source()
                .nodes()
                .containsKey(catalyst));
        var stock = new KeyCounter();
        stock.add(seed, 1L);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 10L, true, ECOCancellation.NONE);

        assertEquals(
            1L,
            outcome.state()
                .missingItems()
                .get(catalyst));
        assertTrue(
            outcome.state()
                .patternTimes()
                .isEmpty(),
            "Blocked cycle must remain uncommitted");
        var plan = new AE2CraftingPlanBridge().partial(seed, 10L, false, outcome.state());
        var summary = CraftingPlanSummary
            .fromJob(mock(GridView.class, RETURNS_DEEP_STUBS), mock(IActionSource.class), plan);
        assertTrue(summary.isSimulation());
        assertEquals(
            1L,
            summary.getEntries()
                .stream()
                .filter(
                    entry -> entry.getWhat()
                        .equals(catalyst))
                .findFirst()
                .orElseThrow(java.util.NoSuchElementException::new)
                .getMissingAmount());
    }

    @Test
    void stockedSelfGrowthUsesOneSeedInsteadOfMissingTheWholeDemand() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey seed = mock(AEKey.class);
        AEKey fuel = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, seed, fuel))
            when(key.getAmountPerByte()).thenReturn(8);
        var consumer = staticPattern(0, product, 1L, seed, 51L);
        var growth = staticPattern(1, seed, 2L, new GenericStack(seed, 1L), new GenericStack(fuel, 1L));
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth),
                fuel,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 23L);
        stock.add(fuel, 28L);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 1L, true, ECOCancellation.NONE);

        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertTrue(
            outcome.state()
                .missingItems()
                .isEmpty());
        assertEquals(
            28L,
            outcome.state()
                .patternTimes()
                .get(growth.details()));
        assertEquals(
            1L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(
                outcome.trace()
                    .cycles())
                .solveResult()
                .requiredSeed()
                .get(seed));
        assertTrue(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(
                outcome.trace()
                    .cycles())
                .solveResult()
                .seedShortfall()
                .isEmpty());
    }

    @Test
    void directSelfGrowthRequestStillCraftsNewOutputDespiteStoredCopies() throws Exception {
        AEKey seed = mock(AEKey.class);
        AEKey fuel = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(seed, fuel))
            when(key.getAmountPerByte()).thenReturn(8);
        var growth = staticPattern(0, seed, 2L, new GenericStack(seed, 1L), new GenericStack(fuel, 1L));
        var network = new CompiledNetwork(
            seed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth),
                fuel,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            2);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 23L);
        stock.add(fuel, 28L);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 28L, true, ECOCancellation.NONE);

        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertTrue(
            outcome.state()
                .missingItems()
                .isEmpty());
        assertEquals(
            28L,
            outcome.state()
                .patternTimes()
                .get(growth.details()));
    }

    @Test
    void delegatedCycleCoveredByStockDoesNotLeaveAnUnscheduledCycleSupplier() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey dust = mock(AEKey.class);
        AEKey fuel = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, dust, fuel))
            when(key.getAmountPerByte()).thenReturn(8);
        var productGrowth = staticPattern(0, product, 2L, new GenericStack(product, 1L), new GenericStack(dust, 1L));
        var dustGrowth = staticPattern(1, dust, 2L, new GenericStack(dust, 1L), new GenericStack(fuel, 1L));
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(productGrowth),
                dust,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(dustGrowth),
                fuel,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            4);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(product, 1L);
        stock.add(dust, 3L);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 2L, true, ECOCancellation.NONE);

        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        var dustComponent = outcome.components()
            .stream()
            .filter(
                component -> component.requiredOutputs()
                    .containsKey(dust))
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        assertEquals(CycleExecutionDisposition.STOCK_SATISFIED, dustComponent.cycleDisposition());
        var provenance = outcome.state()
            .executionProvenance();
        provenance.requireComplete();
        assertTrue(
            provenance.allocations()
                .stream()
                .anyMatch(allocation -> {
                    var demand = provenance.demands()
                        .get(allocation.demandId());
                    return demand.kind() == MaterialDemand.Kind.CYCLE_BOUNDARY && demand.key()
                        .equals(dust) && allocation.source() == MaterialSource.Stock.INSTANCE;
                }));
        assertTrue(
            provenance.allocations()
                .stream()
                .noneMatch(
                    allocation -> allocation.source()
                        .equals(new MaterialSource.CycleOutput(dustComponent.componentId()))));
        var schedule = ECOExecutionSchedule.from(
            outcome.components(),
            outcome.executionComponentOrder(),
            outcome.state()
                .patternTimes(),
            provenance);
        assertTrue(
            schedule.phases()
                .stream()
                .noneMatch(phase -> phase.componentId() == dustComponent.componentId()));
    }

    @Test
    void missingAcyclicAlternativeRetriesTheStockedGrowthRecipe() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey seed = mock(AEKey.class);
        AEKey fuel = mock(AEKey.class);
        AEKey unavailable = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, seed, fuel, unavailable))
            when(key.getAmountPerByte()).thenReturn(8);
        var consumer = staticPattern(0, product, 1L, seed, 51L);
        var growth = staticPattern(1, seed, 2L, new GenericStack(seed, 1L), new GenericStack(fuel, 1L));
        var alternative = staticPattern(2, seed, 1L, unavailable, 1L);
        var stock = new KeyCounter();
        stock.add(seed, 23L);
        stock.add(fuel, 28L);
        for (var producers : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth, alternative),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(alternative, growth))) {
            var network = new CompiledNetwork(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                    product,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                    seed,
                    producers,
                    fuel,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                    unavailable,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
                3,
                4);
            var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
            var condensation = CondensationGraph
                .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
            var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
            var preferred = planner.selectRoutes(condensation, true, ECOCancellation.NONE);
            assertTrue(preferred.acyclic());

            var outcome = planner.planWithCycleFallback(
                network,
                condensation,
                preferred,
                stock,
                PlannerInventorySnapshot.of(stock),
                1L,
                false,
                ECOCancellation.NONE);

            assertEquals(
                cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
                outcome.status(),
                outcome.trace()
                    .diagnostics()
                    .toString());
            assertTrue(
                outcome.state()
                    .missingItems()
                    .isEmpty());
            assertEquals(
                28L,
                outcome.state()
                    .patternTimes()
                    .get(growth.details()));
            assertFalse(
                outcome.cycles()
                    .isEmpty());
        }
    }

    @Test
    void committedCycleDoesNotBlockAnUnrepresentableParentOrder() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey seed = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, seed))
            when(key.getAmountPerByte()).thenReturn(8);
        var consumer = staticPattern(0, product, 1L, seed, 4L);
        var growth = staticPattern(1, seed, 2L, seed, 1L);
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 1L);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 2L, true, ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertTrue(
            outcome.state()
                .missingAmounts()
                .isEmpty());
        outcome.state()
            .executionProvenance()
            .requireComplete();
        cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.from(
            outcome.components(),
            outcome.executionComponentOrder(),
            outcome.state()
                .patternTimes(),
            outcome.state()
                .executionProvenance());
        assertTrue(
            outcome.state()
                .plannerPatternTimes()
                .get(growth.details())
                .signum() > 0);
        var cycle = outcome.components()
            .stream()
            .filter(
                component -> component.type()
                    == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        assertNotEquals(CycleExecutionDisposition.BLOCKED, cycle.cycleDisposition());
        var result = new cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            new AE2CraftingPlanBridge().unsupported(product, 2L),
            outcome.trace(),
            outcome.cycles(),
            outcome.components(),
            outcome.executionComponentOrder(),
            0L);
        assertTrue(
            cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission.allows(result, false),
            "A committed cycle must not retain a diagnostic-only status: " + cycle.status());
    }

    @Test
    void wideCycleDemandRetainsConsumedStockAsStartupSeed() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey seed = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, seed))
            when(key.getAmountPerByte()).thenReturn(8);
        var consumer = staticPattern(0, product, 1L, seed, 2L);
        var growth = staticPattern(1, seed, 2L, seed, 1L);
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 221_670L);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, Long.MAX_VALUE, true, ECOCancellation.NONE);

        assertEquals(
            PlannerAmount.of(221_670L),
            outcome.state()
                .usedAmounts()
                .get(seed));
        var cycle = outcome.components()
            .stream()
            .filter(
                candidate -> candidate.type()
                    == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new)
            .cycleResult();
        assertEquals(
            CycleSolveStatus.UNREPRESENTABLE,
            cycle.status(),
            cycle.diagnostics()
                .toString());
        assertTrue(
            cycle.seedShortfall()
                .isEmpty(),
            cycle.diagnostics()
                .toString());
        assertTrue(
            cycle.diagnostics()
                .stream()
                .anyMatch(
                    diagnostic -> diagnostic.code()
                        == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic.Code.SEED_COVERED_BY_STOCK));
    }

    @Test
    void compressedStockSuppliesLargeDownstreamDemandWithoutLooseSeed() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey crystal = mock(AEKey.class);
        AEKey block = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product, crystal, block))
            when(key.getAmountPerByte()).thenReturn(8);
        var finalRecipe = staticPattern(0, product, 1L, crystal, 400_000L);
        var unpack = staticPattern(1, crystal, 4L, block, 1L);
        var pack = staticPattern(2, block, 1L, crystal, 4L);
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finalRecipe),
                crystal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unpack),
                block,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pack)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            3,
            3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var inventory = new KeyCounter();
        inventory.add(block, 100_000L);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, 1L, true, ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertTrue(
            outcome.state()
                .missingItems()
                .isEmpty());
        assertEquals(
            100_000L,
            outcome.state()
                .usedItems()
                .get(block));
        assertEquals(
            100_000L,
            outcome.state()
                .patternTimes()
                .get(unpack.details()));
        assertFalse(
            outcome.state()
                .patternTimes()
                .containsKey(pack.details()));
    }

    @Test
    void compressedIngredientRemainsIndependentOfFourGrowthSeeds() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey crystal = mock(AEKey.class);
        AEKey block = mock(AEKey.class);
        var seeds = java.util.stream.IntStream.range(0, 4)
            .mapToObj(i -> mock(AEKey.class))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var producers = new java.util.LinkedHashMap<AEKey, List<CompiledPattern>>();
        var finalInputs = new java.util.ArrayList<GenericStack>();
        finalInputs.add(new GenericStack(crystal, 400_000L));
        var inventory = new KeyCounter();
        // Four seeds each grow from one to four: 12 crystals, exactly three extra blocks.
        inventory.add(block, 100_003L);
        for (int i = 0; i < seeds.size(); i++) {
            AEKey seed = seeds.get(i);
            inventory.add(seed, 1L);
            finalInputs.add(new GenericStack(seed, 4L));
            producers.put(
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(staticPattern(3 + i, seed, 2L, new GenericStack(seed, 1L), new GenericStack(crystal, 1L))));
        }
        var unpack = staticPattern(1, crystal, 4L, block, 1L);
        var pack = staticPattern(2, block, 1L, crystal, 4L);
        producers.put(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(staticPattern(0, product, 1L, finalInputs.toArray(new GenericStack[0]))));
        producers.put(crystal, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unpack));
        producers.put(block, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pack));
        producers.keySet()
            .forEach(key -> when(key.getAmountPerByte()).thenReturn(8));
        var network = new CompiledNetwork(
            product,
            producers,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            7,
            15);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, 1L, true, ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertTrue(
            outcome.state()
                .missingItems()
                .isEmpty());
        assertEquals(
            100_003L,
            outcome.state()
                .usedItems()
                .get(block));
        assertEquals(
            0L,
            outcome.state()
                .usedItems()
                .get(crystal));
        assertEquals(
            100_003L,
            outcome.state()
                .patternTimes()
                .get(unpack.details()));
        for (AEKey seed : seeds) {
            assertEquals(
                3L,
                outcome.state()
                    .patternTimes()
                    .get(
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(producers.get(seed))
                            .details()));
        }
        assertFalse(
            outcome.state()
                .patternTimes()
                .containsKey(pack.details()));
        outcome.state()
            .executionProvenance()
            .requireComplete();
        cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.from(
            outcome.components(),
            outcome.executionComponentOrder(),
            outcome.state()
                .patternTimes(),
            outcome.state()
                .executionProvenance());
    }

    private static CompiledPattern staticPattern(int id, AEKey outputKey, long outputAmount, AEKey inputKey,
        long inputAmount) {
        return staticPattern(id, outputKey, outputAmount, new GenericStack(inputKey, inputAmount));
    }

    private static CompiledPattern staticPattern(int id, AEKey outputKey, long outputAmount, GenericStack... inputs) {
        IPatternDetails details = mock(IPatternDetails.class);
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(outputKey, outputAmount));
        when(details.getOutputs()).thenReturn(outputs);
        when(details.getPrimaryOutput()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(outputs));
        var rawInputs = java.util.Arrays.stream(inputs)
            .map(stack -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getPossibleInputs()).thenReturn(new GenericStack[] { stack });
                when(input.getMultiplier()).thenReturn(1L);
                when(input.isValid(any(), any())).thenAnswer(
                    call -> stack.what()
                        .equals(call.getArgument(0)));
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
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
            outputKey,
            PlannerAmount.of(outputAmount),
            java.util.Arrays.stream(inputs)
                .map(input -> new CompiledInput(null, input.what(), input.amount(), true, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            outputs,
            true,
            null,
            false,
            semantics);
    }

    @Test
    void unproducibleInternalSeedReachesAe2MissingSummary() throws Exception {
        AEKey seed = mock(AEKey.class);
        when(seed.getAmountPerByte()).thenReturn(8);
        IPatternDetails details = mock(IPatternDetails.class);
        var output = new GenericStack(seed, 2L);
        when(details.getOutputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output));
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        var pattern = new CompiledPattern(
            0,
            details,
            seed,
            PlannerAmount.of(2),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new CompiledInput(null, seed, 1L, true, null)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output),
            true,
            null,
            false,
            semantics);
        var network = new CompiledNetwork(
            seed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .mapOf(seed, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            1);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var shortage = new CycleSolveResult(
            CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            CycleSolveMetrics.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> shortage);

        var outcome = planner.plan(network, condensation, new KeyCounter(), 100L, true, ECOCancellation.NONE);

        assertEquals(
            1L,
            outcome.state()
                .missingItems()
                .get(seed));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(
                outcome.trace()
                    .cycles())
                .solveResult()
                .seedShortfall());
        assertEquals(
            CycleExecutionDisposition.BLOCKED,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(outcome.components())
                .cycleDisposition());
        assertTrue(
            outcome.state()
                .patternTimes()
                .isEmpty(),
            "Failed cycle must remain uncommitted");
        var plan = new AE2CraftingPlanBridge().partial(seed, 100L, false, outcome.state());
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        var summary = CraftingPlanSummary.fromJob(grid, mock(IActionSource.class), plan);
        assertTrue(summary.isSimulation());
        assertEquals(
            1,
            summary.getEntries()
                .size());
        assertEquals(
            seed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(summary.getEntries())
                .getWhat());
        assertEquals(
            1L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(summary.getEntries())
                .getMissingAmount());
    }

    @Test
    void largeSeededTemplateOrderReportsMissingNetherrackInsteadOfUnresolvedCycle() throws Exception {
        AEKey template = mock(AEKey.class, "netherite_upgrade_smithing_template");
        AEKey diamond = mock(AEKey.class, "diamond"), netherrack = mock(AEKey.class, "netherrack");
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(template, diamond, netherrack))
            when(key.getAmountPerByte()).thenReturn(8);
        var growth = staticPattern(
            0,
            template,
            2L,
            new GenericStack(template, 1L),
            new GenericStack(diamond, 7L),
            new GenericStack(netherrack, 1L));
        var network = new CompiledNetwork(
            template,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                template,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growth),
                diamond,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                netherrack,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var inventory = new KeyCounter();
        long amount = 12_312_312L;
        inventory.add(template, 1L);
        inventory.add(diamond, 7L * amount);
        inventory.add(netherrack, 48_180L);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        var outcome = planner.planWithCycleFallback(
            network,
            condensation,
            planner.selectRoutes(condensation, true, ECOCancellation.NONE),
            inventory,
            PlannerInventorySnapshot.of(inventory),
            amount,
            false,
            ECOCancellation.NONE);

        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertEquals(
            12_264_132L,
            outcome.state()
                .missingItems()
                .get(netherrack));
        assertEquals(
            0L,
            outcome.state()
                .missingItems()
                .get(template));
        assertEquals(
            0L,
            outcome.state()
                .missingItems()
                .get(diamond));
        var component = outcome.components()
            .stream()
            .filter(
                c -> c.type() == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        assertEquals(
            CycleSolveStatus.SUCCESS,
            component.cycleResult()
                .status());
        assertEquals(
            PlannerAmount.of(amount),
            component.cycleResult()
                .plannerTotalFirings());
        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus.MISSING,
            component.externalDemandStatus());
        assertEquals(CycleExecutionDisposition.BLOCKED, component.cycleDisposition());
        assertTrue(
            outcome.state()
                .patternTimes()
                .isEmpty(),
            "Missing cycle must remain uncommitted");
        var missingPlan = new AE2CraftingPlanBridge().success(template, amount, true, false, outcome.state());
        assertTrue(missingPlan.simulation());
        assertEquals(
            12_264_132L,
            missingPlan.missingItems()
                .get(netherrack));
        assertEquals(48_180L, inventory.get(netherrack), "Planning must not consume real inventory");

        inventory.add(netherrack, 12_264_132L);
        var supplied = planner.plan(network, condensation, inventory, amount, true, ECOCancellation.NONE);
        assertEquals(cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.SUCCESS, supplied.status());
        assertTrue(
            supplied.state()
                .missingItems()
                .isEmpty());
        assertEquals(
            amount,
            supplied.state()
                .patternTimes()
                .get(growth.details()));
        assertEquals(
            amount,
            supplied.state()
                .usedItems()
                .get(netherrack));
        assertEquals(
            7L * amount,
            supplied.state()
                .usedItems()
                .get(diamond));
        supplied.state()
            .executionProvenance()
            .requireComplete();
    }

    @Test
    void provenShortageDoesNotHideAnotherCyclesUnfinishedSearch() throws Exception {
        AEKey goal = mock(AEKey.class, "goal"), a = mock(AEKey.class, "a"), b = mock(AEKey.class, "b");
        AEKey fuel = mock(AEKey.class, "fuel");
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(goal, a, b, fuel))
            when(key.getAmountPerByte()).thenReturn(8);
        var finish = staticPattern(0, goal, 1L, new GenericStack(a, 2L), new GenericStack(b, 2L));
        var growthA = staticPattern(1, a, 2L, a, 1L);
        var growthB = staticPattern(2, b, 2L, new GenericStack(b, 1L), new GenericStack(fuel, 1L));
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finish),
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growthA),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(growthB),
                fuel,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            3,
            5);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(a, 1L);
        stock.add(b, 1L);
        var bounded = new BoundedCycleSolver();
        var planner = new ComponentPlanner(
            new AcyclicCraftingSolver(),
            (request, cancellation) -> request.component()
                .members()
                .contains(a)
                    ? CycleSolveResult.failure(
                        CycleSolveStatus.UNKNOWN_BUDGET,
                        cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED,
                        "Unfinished search in the other required cycle")
                    : bounded.solve(request, cancellation));
        var outcome = planner.plan(network, condensation, stock, 1L, true, ECOCancellation.NONE);

        assertEquals(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.PARTIAL,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertEquals(
            1L,
            outcome.state()
                .missingItems()
                .get(fuel));
        assertTrue(
            outcome.components()
                .stream()
                .anyMatch(
                    c -> c.cycleStatus()
                        == cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus.UNKNOWN_BUDGET));
        assertFalse(
            outcome.state()
                .patternTimes()
                .containsKey(growthA.details()));
        assertFalse(
            outcome.state()
                .patternTimes()
                .containsKey(growthB.details()));
    }

    @Test
    void seededGrowthReportsEveryExternalDeficit() throws Exception {
        AEKey seed = mock(AEKey.class);
        AEKey diamond = mock(AEKey.class);
        AEKey netherrack = mock(AEKey.class);
        for (AEKey key : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(seed, diamond, netherrack))
            when(key.getAmountPerByte()).thenReturn(8);
        IPatternDetails details = mock(IPatternDetails.class);
        var output = new GenericStack(seed, 2L);
        when(details.getOutputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output));
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        var pattern = new CompiledPattern(
            0,
            details,
            seed,
            PlannerAmount.of(2),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new CompiledInput(null, seed, 1L, true, null),
                new CompiledInput(null, diamond, 7L, true, null),
                new CompiledInput(null, netherrack, 1L, true, null)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output),
            true,
            null,
            false,
            semantics);
        var network = new CompiledNetwork(
            seed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                seed,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern),
                diamond,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                netherrack,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        for (long storedDiamonds : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0L, 10L)) {
            KeyCounter inventory = new KeyCounter();
            inventory.add(seed, 1L);
            inventory.add(diamond, storedDiamonds);
            var outcome = planner.plan(network, condensation, inventory, 4L, true, ECOCancellation.NONE);
            assertEquals(
                cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS,
                outcome.status(),
                outcome.trace()
                    .diagnostics()
                    .toString());
            assertEquals(
                28L - storedDiamonds,
                outcome.state()
                    .missingItems()
                    .get(diamond),
                outcome.trace()
                    .diagnostics()
                    .toString());
            assertEquals(
                4L,
                outcome.state()
                    .missingItems()
                    .get(netherrack));
            assertEquals(
                0L,
                outcome.state()
                    .missingItems()
                    .get(seed));
            assertTrue(
                outcome.state()
                    .patternTimes()
                    .isEmpty());
            var plan = new AE2CraftingPlanBridge().partial(seed, 4L, false, outcome.state());
            var summary = CraftingPlanSummary
                .fromJob(mock(GridView.class, RETURNS_DEEP_STUBS), mock(IActionSource.class), plan);
            assertTrue(summary.isSimulation());
            assertEquals(
                2L,
                summary.getEntries()
                    .stream()
                    .filter(entry -> entry.getMissingAmount() > 0)
                    .count());
            var result = new cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult(
                outcome.status(),
                plan,
                outcome.trace(),
                outcome.cycles(),
                outcome.components(),
                outcome.executionComponentOrder(),
                0L);
            var snapshot = cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory
                .create(result);
            assertEquals(
                java.math.BigInteger.valueOf(28L - storedDiamonds),
                snapshot.nodes()
                    .stream()
                    .filter(
                        node -> node.key()
                            .equals(diamond))
                    .findFirst()
                    .orElseThrow(java.util.NoSuchElementException::new)
                    .missingBigInteger());
            assertEquals(
                java.math.BigInteger.valueOf(4L),
                snapshot.nodes()
                    .stream()
                    .filter(
                        node -> node.key()
                            .equals(netherrack))
                    .findFirst()
                    .orElseThrow(java.util.NoSuchElementException::new)
                    .missingBigInteger());
        }
    }
}
