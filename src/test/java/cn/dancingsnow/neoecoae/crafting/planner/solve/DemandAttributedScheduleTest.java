package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class DemandAttributedScheduleTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void honeyStockAndReturnedBottleAreNotConnectedToEveryConsumer() throws Exception {
        var honey = AEItemKey.of(TestItems.HONEY_BOTTLE);
        var bottle = AEItemKey.of(TestItems.GLASS_BOTTLE);
        var sugar = AEItemKey.of(TestItems.SUGAR);
        var paper = AEItemKey.of(TestItems.PAPER);
        var emerald = AEItemKey.of(TestItems.EMERALD);
        var goal = AEItemKey.of(TestItems.DIAMOND);
        var finish = pattern(
            0,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(goal, 1)),
            sugar,
            paper,
            emerald);
        var consumeHoney = pattern(
            1,
            sugar,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new GenericStack(sugar, 1), new GenericStack(bottle, 1)),
            honey);
        var useBottle = pattern(
            2,
            paper,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new GenericStack(paper, 1), new GenericStack(honey, 1)),
            bottle);
        var otherHoneyConsumer = pattern(
            3,
            emerald,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(emerald, 1)),
            honey);
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finish));
        producers.put(sugar, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumeHoney));
        producers.put(paper, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(useBottle));
        producers.put(emerald, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(otherHoneyConsumer));
        producers.put(honey, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        producers.put(bottle, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var network = new CompiledNetwork(
            goal,
            producers,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            6,
            4);
        var inventory = new KeyCounter();
        inventory.add(honey, 1);
        var outcome = new AcyclicCraftingSolver().solve(
            network,
            new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            inventory,
            1,
            ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        var provenance = outcome.state()
            .executionProvenance();
        provenance.requireComplete();
        assertEquals(
            MaterialSource.Stock.INSTANCE,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(provenance.allocationsFor(consumeHoney.details()))
                .source());
        assertEquals(
            new MaterialSource.PatternOutput(consumeHoney.details(), false),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(provenance.allocationsFor(useBottle.details()))
                .source());
        assertEquals(
            new MaterialSource.PatternOutput(useBottle.details(), false),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .first(provenance.allocationsFor(otherHoneyConsumer.details()))
                .source());
        var schedule = ECOExecutionSchedule.from(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            outcome.state()
                .patternTimes(),
            provenance);
        assertBefore(schedule, consumeHoney.details(), useBottle.details());
        assertBefore(schedule, useBottle.details(), otherHoneyConsumer.details());
        assertBefore(schedule, otherHoneyConsumer.details(), finish.details());
        assertEquals(
            4,
            schedule.phases()
                .size());
    }

    @Test
    void groupedAcyclicProducersAreOrderedInsteadOfReportedAsSelfCycles() throws Exception {
        var raw = AEItemKey.of(TestItems.IRON_INGOT);
        var tool = AEItemKey.of(TestItems.GLASS_BOTTLE);
        var goal = AEItemKey.of(TestItems.DIAMOND);
        var supplier = pattern(
            0,
            tool,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(tool, 1)),
            raw);
        var consumer = pattern(
            1,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(goal, 1)),
            tool);
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                tool,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(supplier),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            3,
            2);
        var inventory = new KeyCounter();
        inventory.add(raw, 1);
        var outcome = new AcyclicCraftingSolver().solve(
            network,
            new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            inventory,
            1,
            ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        // Local tool/catalyst producer chains are collected in the goal's structural component.
        var component = acyclicComponent(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(consumer.details(), supplier.details()));
        var schedule = ECOExecutionSchedule.from(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(component),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0),
            outcome.state()
                .patternTimes(),
            outcome.state()
                .executionProvenance());
        assertBefore(schedule, supplier.details(), consumer.details());
        assertEquals(
            2,
            schedule.phases()
                .size());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new ECOExecutionSchedule.PhaseDependency(0, 1)),
            schedule.dependencies());
        assertEquals(
            2,
            schedule.phases()
                .stream()
                .map(p -> p.componentId())
                .distinct()
                .count());
    }

    @Test
    void locallyCraftedReusableToolBuildsAValidComponentSchedule() throws Exception {
        var raw = AEItemKey.of(TestItems.IRON_INGOT);
        var tool = AEItemKey.of(TestItems.GLASS_BOTTLE);
        var goal = AEItemKey.of(TestItems.DIAMOND);
        var supplier = pattern(
            0,
            tool,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(tool, 1)),
            raw);
        var base = pattern(
            1,
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(goal, 1)),
            tool);
        var input = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(base.inputs());
        when(
            input.source()
                .getRemainingKey(tool)).thenReturn(tool);
        var special = new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Requirement(
                    input,
                    tool,
                    cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Type.REUSABLE,
                    0,
                    0)));
        var consumer = new CompiledPattern(
            base.id(),
            base.details(),
            goal,
            PlannerAmount.ONE,
            base.inputs(),
            base.outputs(),
            true,
            null,
            false,
            base.semantics(),
            special);
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                tool,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(supplier),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            3,
            2);
        var graph = new cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder()
            .build(network, ECOCancellation.NONE);
        var condensation = cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph.build(
            graph,
            new cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE),
            ECOCancellation.NONE);
        var inventory = new KeyCounter();
        inventory.add(raw, 1);
        var outcome = new ComponentPlanner(
            new AcyclicCraftingSolver(),
            new cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver())
                .plan(network, condensation, inventory, 1, true, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            outcome.status(),
            outcome.trace()
                .diagnostics()
                .toString());
        assertTrue(
            outcome.components()
                .stream()
                .anyMatch(
                    component -> component.executionPatterns()
                        .containsAll(
                            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                                .setOf(supplier.details(), consumer.details()))));
        var schedule = ECOExecutionSchedule.from(
            outcome.components(),
            outcome.executionComponentOrder(),
            outcome.state()
                .patternTimes(),
            outcome.state()
                .executionProvenance());
        assertBefore(schedule, supplier.details(), consumer.details());
    }

    @Test
    void groupedMutualCycleStillFailsClosed() {
        var a = AEItemKey.of(TestItems.GLASS_BOTTLE);
        var b = AEItemKey.of(TestItems.HONEY_BOTTLE);
        var first = pattern(0, a, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(a, 1)), b);
        var second = pattern(1, b, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(b, 1)), a);
        var ledger = new MaterialProvenance();
        var firstInput = MaterialDemand.input(first.details(), 0, b, PlannerAmount.ONE);
        var secondInput = MaterialDemand.input(second.details(), 0, a, PlannerAmount.ONE);
        ledger.register(firstInput);
        ledger.register(secondInput);
        ledger.allocate(firstInput, b, new MaterialSource.PatternOutput(second.details(), true), PlannerAmount.ONE);
        ledger.allocate(secondInput, a, new MaterialSource.PatternOutput(first.details(), true), PlannerAmount.ONE);
        var component = acyclicComponent(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(first.details(), second.details()));
        assertThrows(
            IllegalStateException.class,
            () -> ECOExecutionSchedule.from(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(component),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(first.details(), 1L, second.details(), 1L),
                ledger.freeze()));
    }

    private static ComponentPlanningResult acyclicComponent(int id, Set<IPatternDetails> patterns) {
        return new ComponentPlanningResult(
            id,
            ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            patterns,
            null,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            null,
            null);
    }

    @Test
    void missingDemandAttributionIsAnInvariantError() {
        var key = AEItemKey.of(TestItems.GLASS_BOTTLE);
        var pattern = pattern(0, key, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1)));
        var summaryOnly = new ExecutionProvenance(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                key,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .mapOf(MaterialSource.Stock.INSTANCE, PlannerAmount.ONE)));
        assertThrows(
            IllegalStateException.class,
            () -> ECOExecutionSchedule.from(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern.details(), 1L),
                summaryOnly));
    }

    @Test
    void realSelfReturnIsNotSilentlyDroppedAsAnAcyclicEdge() {
        var key = AEItemKey.of(TestItems.GLASS_BOTTLE);
        var pattern = pattern(
            0,
            key,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 2)),
            key);
        var ledger = new MaterialProvenance();
        var input = MaterialDemand.input(pattern.details(), 0, key, PlannerAmount.ONE);
        ledger.register(input);
        ledger.credit(key, pattern.details(), PlannerAmount.ONE);
        ledger.consumeCredit(input, key, PlannerAmount.ONE);
        assertThrows(
            IllegalStateException.class,
            () -> ECOExecutionSchedule.from(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern.details(), 1L),
                ledger.freeze()));
    }

    private static void assertBefore(ECOExecutionSchedule schedule, IPatternDetails before, IPatternDetails after) {
        int first = -1, second = -1;
        for (int i = 0; i < schedule.phases()
            .size(); i++) {
            if (schedule.phases()
                .get(i)
                .patternSet()
                .contains(before)) first = i;
            if (schedule.phases()
                .get(i)
                .patternSet()
                .contains(after)) second = i;
        }
        assertTrue(first >= 0 && second > first);
    }

    private static CompiledPattern pattern(int id, AEKey key, List<GenericStack> outputs, AEKey... keys) {
        var details = mock(IPatternDetails.class);
        when(details.getOutputs()).thenReturn(outputs);
        var inputs = new ArrayList<CompiledInput>();
        var rawInputs = new IPatternDetails.IInput[keys.length];
        for (int i = 0; i < keys.length; i++) {
            var raw = mock(IPatternDetails.IInput.class);
            when(raw.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(keys[i], 1) });
            when(raw.getMultiplier()).thenReturn(1L);
            rawInputs[i] = raw;
            inputs.add(new CompiledInput(raw, keys[i], 1L, true, null));
        }
        when(details.getInputs()).thenReturn(rawInputs);
        var semanticInputs = inputs.stream()
            .map(
                input -> new PatternSemantics.Input(
                    input.source(),
                    input.key(),
                    input.amountPerPattern(),
                    null,
                    PlannerAmount.ZERO))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var semantics = new PatternSemantics(
            details,
            null,
            semanticInputs,
            outputs,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        return new CompiledPattern(id, details, key, PlannerAmount.ONE, inputs, outputs, true, null, false, semantics);
    }
}
