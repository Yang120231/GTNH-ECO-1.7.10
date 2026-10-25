package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class UnsupportedRouteFallbackTest {

    private final AEKey goal = key("boundary");
    private final AEKey blocked = key("blocked");
    private final AEKey stock = key("stock");
    private final AEKey cycleKey = key("cycle");
    private final CompiledPattern preferred = pattern(
        0,
        goal,
        true,
        new GenericStack(stock, 1),
        new GenericStack(blocked, 1));
    private final CompiledPattern alternate = pattern(1, goal, true, new GenericStack(stock, 2));
    private final CompiledPattern unsupported = pattern(2, blocked, false);

    @Test
    void unsupportedChildRetriesParentAndRollsBackStock() throws Exception {
        var inventory = inventory();
        var result = new AcyclicCraftingSolver().solve(
            network(true),
            new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(goal, stock, blocked)),
            inventory,
            1,
            ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(
            2L,
            result.state()
                .usedItems()
                .get(stock));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(alternate.details(), 1L),
            result.state()
                .patternTimes());
        assertTrue(result.state().unsupported.isEmpty());
        assertEquals(2L, inventory.get(stock), "Input snapshot must remain untouched");
    }

    @Test
    void cycleBoundaryCanUseAnAlternateParentRoute() throws Exception {
        var result = external(true);
        assertEquals(CycleExternalDemandStatus.SOLVED, result.status());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(alternate.details()),
            result.selectedPatterns());
        assertEquals(
            2L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.states())
                .usedItems()
                .get(stock));
    }

    @Test
    void missingThenUnsupportedCandidateStillReachesTheThirdRoute() throws Exception {
        var missing = key("missing");
        var first = pattern(3, goal, true, new GenericStack(missing, 1));
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, preferred, alternate),
                missing,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                blocked,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unsupported),
                stock,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            4,
            4);
        var result = new AcyclicCraftingSolver().solve(
            network,
            new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(goal, missing)),
            inventory(),
            1,
            ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(alternate.details(), 1L),
            result.state()
                .patternTimes());
        assertTrue(
            result.state()
                .missingItems()
                .isEmpty());
        assertEquals(
            2L,
            result.state()
                .usedItems()
                .get(stock));
    }

    @Test
    void unsupportedLastAlternativeDoesNotEraseCompleteShortage() throws Exception {
        var missing = key("missing");
        var first = pattern(3, goal, true, new GenericStack(missing, 4));
        var network = new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, preferred),
                missing,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                blocked,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unsupported),
                stock,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            3,
            3);
        var result = new AcyclicCraftingSolver().solve(
            network,
            new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(goal, missing)),
            inventory(),
            1000,
            ECOCancellation.NONE);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(
            4000L,
            result.state()
                .missingItems()
                .get(missing));
        assertTrue(result.state().unsupported.isEmpty());
    }

    @Test
    void largeBoundaryDemandRemainsAggregatedAcrossFallback() throws Exception {
        var inventory = new KeyCounter();
        inventory.add(stock, 1_000_000_000L);
        var cycle = new CycleComponent(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(cycleKey),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            network(true),
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 500_000_000L),
            inventory,
            new SolveState(inventory),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.SOLVED, result.status());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(alternate.details(), 500_000_000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.states())
                .patternTimes());
        assertEquals(
            1_000_000_000L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.states())
                .usedItems()
                .get(stock));
    }

    @Test
    void exhaustedBoundaryReportsTheKeyAndUnderlyingReason() throws Exception {
        var result = external(false);
        assertEquals(CycleExternalDemandStatus.UNSUPPORTED, result.status());
        assertTrue(
            result.diagnostic()
                .contains("boundary"),
            result.diagnostic());
        assertTrue(
            result.diagnostic()
                .contains("blocked"),
            result.diagnostic());
        assertTrue(
            result.diagnostic()
                .contains("test unsupported contract"),
            result.diagnostic());
        assertTrue(
            result.states()
                .isEmpty(),
            "Failed plans must not be committed");
    }

    private ExternalDemandPlanner.Outcome external(boolean withAlternate) throws Exception {
        var inventory = inventory();
        var cycle = new CycleComponent(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(cycleKey),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        return new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            network(withAlternate),
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L),
            inventory,
            new SolveState(inventory),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
    }

    private CompiledNetwork network(boolean withAlternate) {
        return new CompiledNetwork(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                goal,
                withAlternate ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(preferred, alternate)
                    : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(preferred),
                blocked,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unsupported),
                stock,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            withAlternate ? 3 : 2,
            3);
    }

    private KeyCounter inventory() {
        var inventory = new KeyCounter();
        inventory.add(stock, 2);
        return inventory;
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, boolean supported, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
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
        return new CompiledPattern(
            id,
            details,
            output,
            PlannerAmount.ONE,
            java.util.Arrays.stream(inputs)
                .map(i -> new CompiledInput(null, i.what(), i.amount(), false, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            outputs,
            supported,
            supported ? null : "test unsupported contract",
            false,
            semantics);
    }
}
