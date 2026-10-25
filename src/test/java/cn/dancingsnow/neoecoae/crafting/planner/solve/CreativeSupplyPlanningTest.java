package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class CreativeSupplyPlanningTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void onlyOnlineMountedEcoCreativeCellsConferUnlimitedSupply() {
        AEKey key = AEItemKey.of(TestItems.IRON_INGOT);
        KeyCounter stock = new KeyCounter();
        stock.set(key, Long.MAX_VALUE);
        GridView grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        when(
            grid.getStorageService()
                .getInventory()
                .getAvailableStacks()).thenReturn(stock);
        when(grid.getMachines(ECODriveBlockEntity.class))
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf());
        assertFalse(
            counter(ECOPlannerInventory.capture(grid)).isUnbounded(key),
            "A Long.MAX_VALUE listing alone, including a vanilla drive, is never the capability");
        var drive = mock(ECODriveBlockEntity.class);
        var cell = mock(ECOCreativeCell.class);
        when(cell.configuredKeys()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(key));
        when(drive.getCellInventory()).thenReturn(cell);
        when(grid.getMachines(ECODriveBlockEntity.class))
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(drive));
        when(drive.isMounted()).thenReturn(true);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        when(drive.isOnline()).thenReturn(true);
        var captured = ECOPlannerInventory.capture(grid);
        assertTrue(counter(captured).isUnbounded(key));
        assertEquals(
            Long.MAX_VALUE,
            captured.toKeyCounter()
                .get(key));
        when(drive.isMounted()).thenReturn(false);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        when(drive.isMounted()).thenReturn(true);
        when(drive.getCellInventory()).thenReturn(null);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        assertTrue(counter(captured).isUnbounded(key), "An async calculation owns an immutable snapshot");
    }

    @Test
    void demandAboveLongMaxIsReservedExactlyAndStillRequiresSegmentation() throws Exception {
        AEKey key = AEItemKey.of(TestItems.IRON_INGOT);
        var infinite = solve(Long.MAX_VALUE, snapshot(key, true));
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, infinite.status());
        assertTrue(
            infinite.state()
                .missingAmounts()
                .isEmpty());
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(2)),
            infinite.state()
                .usedAmounts()
                .get(key)
                .toBigInteger());
        assertNull(
            ECOPlanMaterialValidator.firstDeficit(infinite.state(), AEItemKey.of(TestItems.DIAMOND), Long.MAX_VALUE));
        assertFalse(
            infinite.state()
                .executionAmountIssues()
                .isEmpty());

        var finite = solve(Long.MAX_VALUE, snapshot(key, false));
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE),
            finite.state()
                .missingAmounts()
                .get(key)
                .toBigInteger());
    }

    @Test
    void bigOrderSearchBuildsACompleteLongChildFromUnlimitedStock() throws Exception {
        var stock = snapshot(AEItemKey.of(TestItems.IRON_INGOT), true);
        var bridge = new AE2CraftingPlanBridge();
        var answer = ECOBigOrderPlanner.search(Long.MAX_VALUE, Long.MAX_VALUE, amount -> {
            var solved = solve(amount, stock);
            var goal = AEItemKey.of(TestItems.DIAMOND);
            var plan = solved.status() == PlanningStatus.SUCCESS
                ? bridge.success(goal, amount, false, false, solved.state())
                : bridge.unsupported(goal, amount);
            return new ECOPlanningResult(
                solved.status(),
                plan,
                solved.trace(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                0);
        });
        assertFalse(answer.fatal());
        assertEquals(
            PlanningStatus.SUCCESS,
            answer.result()
                .status());
        var plan = answer.result()
            .plan();
        assertTrue(
            plan.finalOutput()
                .amount() < Long.MAX_VALUE);
        assertEquals(
            Math.multiplyExact(
                2,
                plan.finalOutput()
                    .amount()),
            plan.usedItems()
                .get(AEItemKey.of(TestItems.IRON_INGOT)));
        assertTrue(
            plan.missingItems()
                .isEmpty());
        // A new child/probe must not inherit the preceding child's reservations.
        assertEquals(
            PlanningStatus.SUCCESS,
            solve(
                plan.finalOutput()
                    .amount(),
                stock).status());
    }

    @Test
    void unboundedReservationsSurviveCopiesButGoalStockCanBeExcluded() {
        AEKey key = AEItemKey.of(TestItems.IRON_INGOT);
        var original = counter(snapshot(key, true));
        var huge = PlannerAmount.of(BigInteger.TEN.pow(30));
        var copy = original.copy();
        assertEquals(huge, copy.available(key, huge));
        copy.remove(key, huge);
        assertEquals(huge, copy.available(key, huge));
        var replaced = new PlannerCounter();
        replaced.replaceFrom(copy);
        assertTrue(replaced.isUnbounded(key));
        replaced.set(key, PlannerAmount.ZERO);
        assertEquals(PlannerAmount.ZERO, replaced.available(key, huge));
        assertTrue(original.isUnbounded(key));
    }

    @Test
    void cycleBoundaryCanReserveCreativeStockAfterEarlierReservations() throws Exception {
        AEKey key = AEItemKey.of(TestItems.IRON_INGOT);
        var inventory = snapshot(key, true);
        var base = new SolveState(inventory);
        base.used.add(key, Long.MAX_VALUE);
        var network = new CompiledNetwork(
            key,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .mapOf(key, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            0,
            0);
        var cycle = mock(cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent.class);
        var outcome = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, 64L),
            inventory.toKeyCounter(),
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, Long.MAX_VALUE),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(key),
            false,
            ECOCancellation.NONE);
        assertTrue(outcome.solved());
        assertEquals(
            64,
            outcome.directReservations()
                .get(key));
        assertTrue(
            outcome.delegatedCycleDemands()
                .isEmpty(),
            "Creative supply does not need a cycle producer");
        assertTrue(
            outcome.missingLeaves()
                .isEmpty());
    }

    private static PlannerCounter counter(PlannerInventorySnapshot snapshot) {
        var result = new PlannerCounter();
        snapshot.initialize(result);
        return result;
    }

    private static PlannerInventorySnapshot snapshot(AEKey key, boolean unlimited) {
        KeyCounter stock = new KeyCounter();
        stock.set(key, Long.MAX_VALUE);
        return PlannerInventorySnapshot.of(
            stock,
            unlimited ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(key)
                : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf());
    }

    private static AcyclicCraftingSolver.Outcome solve(long amount, PlannerInventorySnapshot inventory)
        throws InterruptedException {
        return solve(amount, inventory, AEItemKey.of(TestItems.IRON_INGOT));
    }

    private static AcyclicCraftingSolver.Outcome solve(long amount, PlannerInventorySnapshot inventory, AEKey input)
        throws InterruptedException {
        AEKey output = AEItemKey.of(TestItems.DIAMOND);
        var details = mock(IPatternDetails.class);
        var ingredient = mock(IPatternDetails.IInput.class);
        when(ingredient.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(input, 2) });
        when(ingredient.getMultiplier()).thenReturn(1L);
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[] { ingredient });
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, 1));
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
        var pattern = new CompiledPattern(
            0,
            details,
            output,
            PlannerAmount.of(1),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new CompiledInput(ingredient, input, 2, false, null)),
            outputs,
            true,
            null,
            false,
            semantics);
        var network = new CompiledNetwork(
            output,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                output,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern),
                input,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            1);
        return new AcyclicCraftingSolver().solve(
            network,
            new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output, input)),
            inventory,
            amount,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            false,
            ECOCancellation.NONE);
    }
}
