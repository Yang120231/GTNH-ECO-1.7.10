package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class CreativeCycleSupplyTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void creativeTemplateDoesNotScheduleItsDuplicationRecipe() throws Exception {
        AEKey product = AEItemKey.of(TestItems.DIAMOND);
        AEKey template = AEItemKey.of(TestItems.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        var consumer = pattern(0, product, 1, template, 2);
        var duplication = pattern(1, template, 2, template, 1);
        var network = new CompiledNetwork(
            product,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                product,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(consumer),
                template,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(duplication)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            2);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.set(template, Long.MAX_VALUE);
        var inventory = PlannerInventorySnapshot
            .of(stock, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(template));
        var planner = new ComponentPlanner(
            new AcyclicCraftingSolver(),
            (request, cancellation) -> {
                throw new AssertionError("Creative-backed intermediate demand must not invoke a cycle solver");
            });
        for (boolean cyclesEnabled : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(false, true)) {
            for (long amount : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(32L, Long.MAX_VALUE)) {
                var result = planner
                    .plan(network, condensation, stock, inventory, amount, cyclesEnabled, false, ECOCancellation.NONE);
                assertEquals(
                    amount == Long.MAX_VALUE ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                        : PlanningStatus.SUCCESS,
                    result.status(),
                    result.trace()
                        .diagnostics()
                        .toString());
                assertEquals(
                    BigInteger.valueOf(amount)
                        .multiply(BigInteger.valueOf(2)),
                    result.state()
                        .usedAmounts()
                        .get(template)
                        .toBigInteger());
                assertEquals(
                    PlannerAmount.of(amount),
                    result.state()
                        .plannerPatternTimes()
                        .get(consumer.details()));
                assertFalse(
                    result.state()
                        .plannerPatternTimes()
                        .containsKey(duplication.details()));
                assertTrue(
                    result.state()
                        .missingAmounts()
                        .isEmpty());
                if (amount == Long.MAX_VALUE) {
                    var shell = new cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan(
                        new GenericStack(product, amount),
                        0,
                        true,
                        false,
                        new KeyCounter(),
                        new KeyCounter(),
                        new KeyCounter(),
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
                    var exact = new cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult(
                        result.status(),
                        shell,
                        result.trace(),
                        result.cycles(),
                        result.components(),
                        result.executionComponentOrder(),
                        0,
                        result.state()
                            .executionProvenance());
                    exact.setExactPatternTimes(
                        result.state()
                            .plannerPatternTimes());
                    exact.setExactMaterials(
                        result.state()
                            .usedAmounts(),
                        result.state()
                            .emittedAmounts(),
                        result.state()
                            .missingAmounts());
                    exact.setTheoreticalBytes(
                        result.state()
                            .plannerBytes());
                    var executable = new cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan(
                        exact,
                        false);
                    assertEquals(
                        1,
                        executable.exactTasks()
                            .size());
                    assertEquals(
                        BigInteger.valueOf(amount)
                            .multiply(BigInteger.valueOf(2)),
                        executable.deferredStock()
                            .get(template)
                            .add(
                                BigInteger.valueOf(
                                    executable.usedItems()
                                        .get(template))));
                    assertNotNull(executable.execution());
                }
            }
        }
    }

    private static CompiledPattern pattern(int id, AEKey outputKey, long outputAmount, AEKey inputKey,
        long inputAmount) {
        var details = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(inputKey, inputAmount) });
        when(input.getMultiplier()).thenReturn(1L);
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(outputKey, outputAmount));
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
            outputKey,
            PlannerAmount.of(outputAmount),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new CompiledInput(input, inputKey, inputAmount, true, null)),
            outputs,
            true,
            null,
            false,
            semantics);
    }
}
