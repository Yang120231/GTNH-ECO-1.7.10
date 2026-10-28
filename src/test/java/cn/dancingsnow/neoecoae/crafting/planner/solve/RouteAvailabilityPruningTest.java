package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class RouteAvailabilityPruningTest {

    @Test
    void provenMissingLeafDoesNotEnumerate4096UnhelpfulRoutes() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .listOf(
                TestItems.RAW_IRON,
                TestItems.STONE,
                TestItems.DIRT,
                TestItems.SAND,
                TestItems.GLASS,
                TestItems.COBBLESTONE,
                TestItems.NETHERRACK,
                TestItems.END_STONE,
                TestItems.OBSIDIAN,
                TestItems.CLAY_BALL,
                TestItems.FLINT,
                TestItems.COAL,
                TestItems.CHARCOAL)
            .stream()
            .map(AEItemKey::of)
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < keys.size() - 1; i++) {
            index.put(
                keys.get(i),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(pattern(keys.get(i), keys.get(i + 1)), pattern(keys.get(i), keys.get(i + 1))));
        }
        var goal = AEItemKey.of(TestItems.BRICK);
        var missing = AEItemKey.of(TestItems.DIAMOND);
        index.put(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(pattern(goal, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(keys), missing)));
        var svc = mock(ICraftingService.class);
        when(svc.getCraftingFor(any())).thenAnswer(
            call -> index.getOrDefault(call.getArgument(0), cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var stock = new KeyCounter();
        stock.add(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.last(keys), 1);
        var session = new ECOCraftingPlannerService().createSession(svc, goal, stock, true);
        var field = session.getClass()
            .getDeclaredField("planningBudget");
        field.setAccessible(true);
        field.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 10_000, Long.MAX_VALUE, () -> 0));
        var result = session.plan(1, false, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        assertEquals(
            PlannerAmount.ONE,
            result.exactMissingItems()
                .get(missing));
        assertTrue(
            result.plan()
                .bytes() > 0);
        assertTrue(
            result.trace()
                .diagnostics()
                .stream()
                .anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_PROVEN_UNREACHABLE));
        assertFalse(
            result.trace()
                .diagnostics()
                .stream()
                .anyMatch(d -> d.code() == PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED));
    }

    @Test
    void positiveButInsufficientLeafDoesNotEnumerate4096UnhelpfulRoutes() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .listOf(
                TestItems.RAW_IRON,
                TestItems.STONE,
                TestItems.DIRT,
                TestItems.SAND,
                TestItems.GLASS,
                TestItems.COBBLESTONE,
                TestItems.NETHERRACK,
                TestItems.END_STONE,
                TestItems.OBSIDIAN,
                TestItems.CLAY_BALL,
                TestItems.FLINT,
                TestItems.COAL,
                TestItems.CHARCOAL)
            .stream()
            .map(AEItemKey::of)
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < keys.size() - 1; i++) {
            index.put(
                keys.get(i),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(pattern(keys.get(i), keys.get(i + 1)), pattern(keys.get(i), keys.get(i + 1))));
        }
        var goal = AEItemKey.of(TestItems.BRICK);
        var scarce = AEItemKey.of(TestItems.DIAMOND);
        index.put(goal, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern(goal, keys.get(0), scarce)));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(
            call -> index.getOrDefault(call.getArgument(0), cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var stock = new KeyCounter();
        stock.add(keys.get(keys.size() - 1), 1_000_000_000L);
        stock.add(scarce, 1L);
        var session = new ECOCraftingPlannerService().createSession(service, goal, stock, true);
        var budget = session.getClass()
            .getDeclaredField("planningBudget");
        budget.setAccessible(true);
        budget.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 10_000, Long.MAX_VALUE, () -> 0));
        var result = session.plan(1_000_000_000L, false, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        assertEquals(
            PlannerAmount.of(999_999_999L),
            result.exactMissingItems()
                .get(scarce));
        assertTrue(
            result.plan()
                .bytes() > 0);
        assertTrue(
            result.trace()
                .diagnostics()
                .stream()
                .anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND));
        assertFalse(
            result.trace()
                .diagnostics()
                .stream()
                .anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
        assertEquals(1L, stock.get(scarce));
    }

    @Test
    void billionOrderWithSubstitutionKeepsWideMissingReportWithoutEnumeratingRoutes() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .listOf(
                TestItems.RAW_IRON,
                TestItems.STONE,
                TestItems.DIRT,
                TestItems.SAND,
                TestItems.GLASS,
                TestItems.COBBLESTONE,
                TestItems.NETHERRACK,
                TestItems.END_STONE,
                TestItems.OBSIDIAN,
                TestItems.CLAY_BALL,
                TestItems.FLINT,
                TestItems.COAL,
                TestItems.CHARCOAL)
            .stream()
            .map(AEItemKey::of)
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < keys.size() - 1; i++) index.put(
            keys.get(i),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(pattern(keys.get(i), keys.get(i + 1)), pattern(keys.get(i), keys.get(i + 1))));
        var goal = AEItemKey.of(TestItems.BRICK);
        var scarce = AEItemKey.of(TestItems.DIAMOND);
        var finish = pattern(goal, keys.get(0), scarce);
        when(finish.getInputs()[0].getPossibleInputs())
            .thenReturn(new GenericStack[] { new GenericStack(keys.get(0), 1), new GenericStack(keys.get(1), 1) });
        when(finish.getInputs()[1].getMultiplier()).thenReturn(Long.MAX_VALUE);
        index.put(goal, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(finish));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(
            call -> index.getOrDefault(call.getArgument(0), cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var stock = new KeyCounter();
        stock.add(keys.get(keys.size() - 1), 1_000_000_000L);
        stock.add(scarce, 1L);
        var session = new ECOCraftingPlannerService().createSession(service, goal, stock, true);
        var budget = session.getClass()
            .getDeclaredField("planningBudget");
        budget.setAccessible(true);
        budget.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 50_000, Long.MAX_VALUE, () -> 0));
        var result = session.plan(1_000_000_000L, false, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        var expected = PlannerAmount.of(Long.MAX_VALUE)
            .multiply(1_000_000_000L)
            .subtract(PlannerAmount.ONE);
        assertEquals(
            expected,
            result.exactMissingItems()
                .get(scarce));
        assertTrue(
            result.plan()
                .simulation());
        assertTrue(
            result.trace()
                .diagnostics()
                .stream()
                .anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND));
        assertFalse(
            result.trace()
                .diagnostics()
                .stream()
                .anyMatch(
                    d -> d.code() == PlannerDiagnostic.Code.INTERNAL_ERROR
                        || d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
    }

    private static IPatternDetails pattern(AEKey output, AEKey... inputs) {
        var pattern = mock(IPatternDetails.class);
        var raw = new ArrayList<IPatternDetails.IInput>();
        for (AEKey key : inputs) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(key, 1) });
            when(input.getMultiplier()).thenReturn(1L);
            raw.add(input);
        }
        when(pattern.getInputs()).thenReturn(raw.toArray(new IPatternDetails.IInput[0]));
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(output, 1));
        return pattern;
    }
}
