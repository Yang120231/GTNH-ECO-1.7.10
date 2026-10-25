package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ExactMaterialFlowTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void cycleTotalsUseExactFiringsEvenWhenAe2ShellIsEmpty() {
        verifyFlow(BigInteger.TEN.pow(25), true);
    }

    @Test
    void ordinaryPlansStillDeriveFlowFromTheirExecutableTasks() {
        verifyFlow(BigInteger.valueOf(4), false);
    }

    @Test
    void traceAndPlanReservationsAreNotCountedTwice() {
        AEKey iron = AEItemKey.of(TestItems.IRON_INGOT);
        var used = new KeyCounter();
        used.add(iron, 3);
        var trace = new ECOPlanTrace();
        trace.addNode(
            new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode(
                cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Kind.MATERIAL,
                iron,
                null,
                3,
                3,
                0,
                0,
                0,
                cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Selection.NOT_APPLICABLE,
                null));
        var plan = new CraftingPlan(
            new GenericStack(AEItemKey.of(TestItems.IRON_BLOCK), 1),
            0,
            false,
            false,
            used,
            new KeyCounter(),
            new KeyCounter(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var result = new ECOPlanningResult(
            PlanningStatus.SUCCESS,
            plan,
            trace,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            0);
        result.setExactMaterials(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(iron, PlannerAmount.of(3)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var node = CraftingGraphSnapshotFactory.create(result)
            .nodes()
            .stream()
            .filter(
                material -> material.key()
                    .equals(iron))
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        assertEquals(BigInteger.valueOf(3), node.fromInventoryBigInteger());
        assertEquals(BigInteger.valueOf(3), node.requestedBigInteger());
    }

    @Test
    void exactReservationsWithoutTraceSurviveAnEmptyCompatibilityShell() {
        AEKey iron = AEItemKey.of(TestItems.IRON_INGOT);
        var amount = BigInteger.TEN.pow(25);
        var plan = new CraftingPlan(
            new GenericStack(AEItemKey.of(TestItems.IRON_BLOCK), 1),
            0,
            true,
            false,
            new KeyCounter(),
            new KeyCounter(),
            new KeyCounter(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var result = new ECOPlanningResult(
            PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            plan,
            new ECOPlanTrace(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            0);
        result.setExactMaterials(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(iron, PlannerAmount.of(amount)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var node = CraftingGraphSnapshotFactory.create(result)
            .nodes()
            .stream()
            .filter(
                material -> material.key()
                    .equals(iron))
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        assertEquals(amount, node.fromInventoryBigInteger());
        assertEquals(amount, node.requestedBigInteger());
        assertEquals(Long.MAX_VALUE, node.fromInventory());
        assertEquals(BigInteger.ZERO, node.toCraftBigInteger());
    }

    private static void verifyFlow(BigInteger count, boolean shell) {
        AEKey seed = AEItemKey.of(TestItems.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        AEKey diamond = AEItemKey.of(TestItems.DIAMOND);
        var pattern = mock(IPatternDetails.class);
        var inputs = new IPatternDetails.IInput[] { input(seed, 1), input(diamond, 7) };
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(seed, 2)));
        var plan = new CraftingPlan(
            new GenericStack(seed, 1),
            0,
            shell,
            false,
            new KeyCounter(),
            new KeyCounter(),
            new KeyCounter(),
            shell ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf()
                : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, count.longValueExact()));
        var result = new ECOPlanningResult(
            shell ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE : PlanningStatus.SUCCESS,
            plan,
            new ECOPlanTrace(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            0);
        if (shell) result.setExactPatternTimes(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, PlannerAmount.of(count)));
        var snapshot = CraftingGraphSnapshotFactory.create(result);
        var seedNode = snapshot.nodes()
            .stream()
            .filter(
                node -> node.key()
                    .equals(seed))
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        var diamondNode = snapshot.nodes()
            .stream()
            .filter(
                node -> node.key()
                    .equals(diamond))
            .findFirst()
            .orElseThrow(java.util.NoSuchElementException::new);
        assertEquals(count, seedNode.consumedBigInteger());
        assertEquals(count.multiply(BigInteger.valueOf(2)), seedNode.producedBigInteger());
        assertEquals(count.multiply(BigInteger.valueOf(7)), diamondNode.consumedBigInteger());
        assertEquals(BigInteger.ZERO, diamondNode.producedBigInteger());
        var row = new ECOCycleItemList.Entry(
            seed,
            seedNode.consumedBigInteger(),
            seedNode.producedBigInteger(),
            BigInteger.ONE,
            count,
            ExecutionCountKnowledge.EXACT,
            CycleSolveStatus.SUCCESS,
            1);
        assertEquals(count.multiply(BigInteger.valueOf(2)), row.displayedTotal());
    }

    private static IPatternDetails.IInput input(AEKey key, long amount) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(key, amount) });
        when(input.getMultiplier()).thenReturn(1L);
        return input;
    }
}
