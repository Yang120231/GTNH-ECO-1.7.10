package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlanBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

class ECOCrystalCycleBatchTest {

    private final AEKey crystal = AEItemKey.of(TestItems.DIAMOND);
    private final AEKey dust = AEItemKey.of(TestItems.EMERALD);
    private final AEKey seed = AEItemKey.of(TestItems.CLAY_BALL);
    private final AEKey sand = AEItemKey.of(TestItems.BUCKET);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void craftingSeedsDoNotReduceProcessingToOneCrystalPerLap() {
        var setup = setup(false, false);
        var runtime = setup.runtime();
        var candidate = runtime.candidates()
            .get(0);
        assertEquals(1000L, candidate.maxDispatchCount());

        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(crystal, 64L, Actionable.MODULATE);
        assertEquals(
            1000L,
            runtime.limitCycleBatch(candidate, inputs(crystal), inventory, candidate.maxDispatchCount()));

        // Accept 64 crushed crystals ahead of the witness, then replay the seed/growth steps one lap at a time.
        setup.progress()[0].accept(64L);
        runtime.onAccepted(candidate, 64L, inputs(crystal));
        for (int lap = 0; lap < 64; lap++) {
            accept(setup, 1, 1L, dust);
            accept(setup, 2, 2L, seed);
        }
        var next = runtime.candidates()
            .get(0);
        assertEquals(0, next.taskId());
        assertEquals(936L, next.maxDispatchCount());
        assertEquals(936L, setup.progress()[0].value);
        assertEquals(936L, setup.progress()[1].value);
        assertEquals(1872L, setup.progress()[2].value);
    }

    @Test
    void substitutionsKeepTheOriginalOrderedWitness() {
        var setup = setup(true, false);
        assertEquals(
            1L,
            setup.runtime()
                .candidates()
                .get(0)
                .maxDispatchCount());
    }

    @Test
    void containerReturnsKeepTheOriginalOrderedWitness() {
        var setup = setup(false, true);
        assertEquals(
            1L,
            setup.runtime()
                .candidates()
                .get(0)
                .maxDispatchCount());
    }

    @Test
    void allProcessingCircuitStillBatches() {
        var setup = setup(false, false);
        when(
            setup.patterns()
                .get(1)
                .supportsPushInputsToExternalInventory()).thenReturn(true);
        var runtime = new ECOExecutionRuntime(
            setup.runtime()
                .plan(),
            setup.patterns()
                .toArray(new IPatternDetails[0]),
            setup.progress());
        assertEquals(
            1000L,
            runtime.candidates()
                .get(0)
                .maxDispatchCount());
    }

    @Test
    void batchingReservesSharedInputsForTheSeedCraftingStep() {
        var setup = setup(false, false);
        var crush = setup.patterns()
            .get(0);
        var shared = setup.patterns()
            .get(1)
            .getInputs()[1];
        var crystalInput = crush.getInputs()[0];
        when(crush.getInputs()).thenReturn(new IPatternDetails.IInput[] { crystalInput, shared });
        var runtime = new ECOExecutionRuntime(
            setup.runtime()
                .plan(),
            setup.patterns()
                .toArray(new IPatternDetails[0]),
            setup.progress());
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(crystal, 64L, Actionable.MODULATE);
        inventory.insert(sand, 1010L, Actionable.MODULATE);
        var candidate = runtime.candidates()
            .get(0);
        // Of 1010 sand, 1000 must remain for seed crafting. Crushing can use only the other 10.
        assertEquals(
            10L,
            runtime.limitCycleBatch(
                candidate,
                new KeyCounter[] { inputs(crystal)[0], inputs(sand)[0] },
                inventory,
                candidate.maxDispatchCount()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void plannedCrystalLoopUsesGrowingStockInsteadOfOneCopyPerLap(boolean craftingSeeds) throws Exception {
        var crush = pattern(false, new GenericStack(dust, 1L), crystal);
        var craft = pattern(craftingSeeds, new GenericStack(seed, 2L), dust, sand);
        var grow = pattern(false, new GenericStack(crystal, 1L), seed);
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(dust)).thenReturn(JdkCompat.listOf(crush));
        when(service.getCraftingFor(seed)).thenReturn(JdkCompat.listOf(craft));
        when(service.getCraftingFor(crystal)).thenReturn(JdkCompat.listOf(grow));
        var network = new CraftingNetworkCompiler(JdkCompat.listOf(new AE2PatternSemanticAdapter()))
            .compile(service, crystal, true, ECOCancellation.NONE);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(crystal, 1L);
        stock.add(sand, 1000L);
        var solved = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 1000L, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, solved.status());
        var signature = PlanIdentity
            .of(new AE2CraftingPlanBridge().success(crystal, 1000L, false, false, solved.state()));
        var plan = ECOExecutionPlanBuilder.build(
            signature,
            ExecutionMode.ORDERED_CYCLE,
            solved.components(),
            solved.executionComponentOrder(),
            solved.state()
                .patternTimes());
        var patterns = new IPatternDetails[plan.tasks()
            .size()];
        var progress = new ECOExecutionTaskProgress[patterns.length];
        for (var task : plan.tasks()) {
            patterns[task.id()] = task.pattern();
            progress[task.id()] = new ECOExecutionTaskProgress();
            progress[task.id()].setExact(BigInteger.valueOf(task.totalCount()), BigInteger.valueOf(task.totalCount()));
        }
        var runtime = new ECOExecutionRuntime(plan, patterns, progress);
        var inventory = new ListCraftingInventory(ignored -> {});
        for (var entry : solved.state()
            .usedItems()) inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        long maxCrushBatch = 0L;
        int dispatches = 0;
        while (!runtime.candidates()
            .isEmpty()) {
            assertTrue(dispatches++ < 10000);
            var candidate = runtime.candidates()
                .get(0);
            var counters = new KeyCounter[candidate.pattern()
                .getInputs().length];
            for (int i = 0; i < counters.length; i++) {
                var input = candidate.pattern()
                    .getInputs()[i];
                counters[i] = new KeyCounter();
                counters[i].add(input.getPossibleInputs()[0].what(), input.getMultiplier());
            }
            long count = runtime.limitCycleBatch(candidate, counters, inventory, candidate.maxDispatchCount());
            for (var counter : counters) for (var entry : counter)
                count = Math.min(count, inventory.list.get(entry.getKey()) / entry.getLongValue());
            assertTrue(count > 0L, "The physical cycle must stay executable");
            if (candidate.pattern() == crush) maxCrushBatch = Math.max(maxCrushBatch, count);
            for (var counter : counters) for (var entry : counter)
                inventory.extract(entry.getKey(), entry.getLongValue() * count, Actionable.MODULATE);
            progress[candidate.taskId()].accept(count);
            runtime.onAccepted(candidate, count, counters);
            for (var output : candidate.pattern()
                .getOutputs()) inventory.insert(output.what(), output.amount() * count, Actionable.MODULATE);
        }
        assertTrue(inventory.list.get(crystal) >= 1000L);
        assertTrue(maxCrushBatch >= 64L, "Growing stock should fund larger batches; maximum was " + maxCrushBatch);
    }

    private void accept(Setup setup, int taskId, long count, AEKey input) {
        var candidate = setup.runtime()
            .candidates()
            .get(0);
        assertEquals(taskId, candidate.taskId());
        setup.progress()[taskId].accept(count);
        setup.runtime()
            .onAccepted(candidate, count, inputs(input));
    }

    private Setup setup(boolean substitutes, boolean returns) {
        var crush = pattern(false, new GenericStack(dust, 1L), crystal);
        var craft = pattern(true, new GenericStack(seed, 2L), dust, sand);
        var grow = pattern(false, new GenericStack(crystal, 1L), seed);
        var craftInput = craft.getInputs()[0];
        if (substitutes) when(craftInput.getPossibleInputs())
            .thenReturn(new GenericStack[] { new GenericStack(dust, 1L), new GenericStack(crystal, 1L) });
        if (returns) when(craftInput.getRemainingKey(dust)).thenReturn(sand);
        var patterns = JdkCompat.listOf(crush, craft, grow);
        var tasks = new java.util.ArrayList<ECOExecutionPlan.TaskSpec>();
        var progress = new ECOExecutionTaskProgress[3];
        for (int id = 0; id < 3; id++) {
            long count = id == 2 ? 2000L : 1000L;
            var pattern = patterns.get(id);
            tasks.add(
                new ECOExecutionPlan.TaskSpec(
                    id,
                    PlanIdentity.patternIdentityFor(pattern),
                    pattern,
                    ECOExecutionPlan.PatternRuntimeInfo.from(pattern),
                    count,
                    0,
                    ECOExecutionPlan.TaskKind.CYCLE_ORDERED));
            progress[id] = new ECOExecutionTaskProgress();
            progress[id].setExact(BigInteger.valueOf(count), BigInteger.valueOf(count));
        }
        var phase = new ECOExecutionPlan.PhaseSpec(
            0,
            0,
            ECOExecutionSchedule.Type.CYCLE,
            JdkCompat.listOf(0, 1, 2),
            JdkCompat.listOf(
                new ECOExecutionPlan.ExecutionStep(0, 1L),
                new ECOExecutionPlan.ExecutionStep(1, 1L),
                new ECOExecutionPlan.ExecutionStep(2, 2L, 3, 1000L)),
            JdkCompat.listOf());
        var signature = new PlanIdentity.Signature(
            crystal,
            1000L,
            JdkCompat.mapOf(),
            JdkCompat.mapOf(),
            JdkCompat.mapOf(),
            JdkCompat.mapOf());
        var plan = new ECOExecutionPlan(
            signature,
            ExecutionMode.ORDERED_CYCLE,
            tasks,
            JdkCompat.listOf(phase),
            new ECOExecutionSchedule(JdkCompat.listOf()));
        return new Setup(
            new ECOExecutionRuntime(plan, patterns.toArray(new IPatternDetails[0]), progress),
            progress,
            patterns);
    }

    private IPatternDetails pattern(boolean crafting, GenericStack output, AEKey... keys) {
        IPatternDetails pattern = crafting ? mock(NativeCraftingPattern.class) : mock(IPatternDetails.class);
        var inputs = new IPatternDetails.IInput[keys.length];
        for (int i = 0; i < keys.length; i++) {
            inputs[i] = mock(IPatternDetails.IInput.class);
            when(inputs[i].getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(keys[i], 1L) });
            when(inputs[i].getMultiplier()).thenReturn(1L);
        }
        when(pattern.supportsPushInputsToExternalInventory()).thenReturn(!crafting);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(JdkCompat.listOf(output));
        return pattern;
    }

    private static KeyCounter[] inputs(AEKey key) {
        var input = new KeyCounter();
        input.add(key, 1L);
        return new KeyCounter[] { input };
    }

    @com.github.bsideup.jabel.Desugar
    private record Setup(ECOExecutionRuntime runtime, ECOExecutionTaskProgress[] progress,
        List<IPatternDetails> patterns) {}
}
