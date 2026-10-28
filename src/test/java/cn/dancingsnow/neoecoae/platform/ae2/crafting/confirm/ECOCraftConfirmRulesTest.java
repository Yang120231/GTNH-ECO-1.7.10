package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode;

class ECOCraftConfirmRulesTest {

    private static ECOCraftConfirmState ready(PlanningStatus status, boolean simulation, boolean cycleDetected) {
        return new ECOCraftConfirmState(
            true,
            true,
            true,
            cycleDetected,
            1_000L,
            BigInteger.TEN,
            status,
            "",
            0,
            simulation,
            10L,
            1L,
            null,
            "");
    }

    @Test
    void completePlanStartsOnlyWithAMatchingCpu() {
        var state = ready(PlanningStatus.SUCCESS, false, false);
        assertEquals(ECOCraftConfirmRules.StartGate.READY, ECOCraftConfirmRules.startGate(state, false, false));
        assertEquals(
            ECOCraftConfirmRules.StartGate.NO_MATCHING_CPU,
            ECOCraftConfirmRules.startGate(state, false, true));
        assertFalse(ECOCraftConfirmRules.isForceStart(state, true), "A complete plan is never force-started");
        assertNull(ECOCraftConfirmRules.rejectStart(state, false));
    }

    @Test
    void calculatingReportWaitsForThePlan() {
        var calculating = ECOCraftConfirmReports.calculating(true);
        assertTrue(calculating.plannerAvailable);
        assertEquals(
            ECOCraftConfirmRules.StartGate.WAITING_FOR_PLAN,
            ECOCraftConfirmRules.startGate(calculating, true, false));
        assertEquals(ECOCraftConfirmError.NO_PLAN, ECOCraftConfirmRules.rejectStart(calculating, true));
    }

    @Test
    void missingMaterialsNeedShiftForceStart() {
        var missing = ready(PlanningStatus.MISSING_ITEMS, true, false);
        assertEquals(
            ECOCraftConfirmRules.StartGate.PLAN_NOT_STARTABLE,
            ECOCraftConfirmRules.startGate(missing, false, false));
        assertEquals(ECOCraftConfirmRules.StartGate.READY, ECOCraftConfirmRules.startGate(missing, true, false));
        assertTrue(ECOCraftConfirmRules.isForceStart(missing, true));
        assertEquals(ECOCraftConfirmError.INCOMPLETE_PLAN, ECOCraftConfirmRules.rejectStart(missing, false));
        assertNull(ECOCraftConfirmRules.rejectStart(missing, true));
    }

    @Test
    void cyclicOrUnsupportedSimulationsCannotBeForced() {
        // A missing cycle has no executable phase schedule; 1.21 sends it to a big order instead.
        var cyclic = ready(PlanningStatus.MISSING_ITEMS, true, true);
        assertFalse(ECOCraftConfirmRules.isStartable(cyclic, false));
        assertTrue(ECOCraftConfirmRules.isStartable(cyclic, true), "A forced cycle shortage becomes a big order");
        assertEquals(
            ECOCraftConfirmError.FORCE_REJECTED,
            ECOCraftConfirmRules.rejectStart(cyclic, true),
            "The plain start path still refuses a cyclic force start");
        for (PlanningStatus status : new PlanningStatus[] { PlanningStatus.CYCLE_UNRESOLVED, PlanningStatus.UNSUPPORTED,
            PlanningStatus.INTERNAL_ERROR, PlanningStatus.PARTIAL }) {
            var state = ready(status, true, false);
            assertFalse(ECOCraftConfirmRules.isStartable(state, true), status.name());
            assertEquals(ECOCraftConfirmError.FORCE_REJECTED, ECOCraftConfirmRules.rejectStart(state, true));
        }
        var unrepresentable = ready(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, false);
        assertTrue(ECOCraftConfirmRules.isStartable(unrepresentable, false), "Started as a big order");
        assertEquals(ECOCraftConfirmError.INCOMPLETE_PLAN, ECOCraftConfirmRules.rejectStart(unrepresentable, false));
    }

    @Test
    void bigOrderEligibilityMirrorsTheSourceScreen() {
        var unrepresentable = ready(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, true, true);
        assertTrue(ECOCraftConfirmRules.usesBigOrderSubmission(unrepresentable, false));
        assertTrue(ECOCraftConfirmRules.usesBigOrderSubmission(unrepresentable, true));

        var cyclicShortage = ready(PlanningStatus.MISSING_ITEMS, true, true);
        assertFalse(ECOCraftConfirmRules.usesBigOrderSubmission(cyclicShortage, false));
        assertTrue(ECOCraftConfirmRules.usesBigOrderSubmission(cyclicShortage, true));

        var acyclicShortage = ready(PlanningStatus.MISSING_ITEMS, true, false);
        assertFalse(
            ECOCraftConfirmRules.usesBigOrderSubmission(acyclicShortage, true),
            "An ordinary shortage keeps the selected CPU and the plain force start");
        assertFalse(ECOCraftConfirmRules.usesBigOrderSubmission(ready(PlanningStatus.SUCCESS, false, true), true));
        assertFalse(
            ECOCraftConfirmRules.usesBigOrderSubmission(ready(PlanningStatus.CYCLE_UNRESOLVED, true, true), true));
        assertFalse(ECOCraftConfirmRules.usesBigOrderSubmission(ECOCraftConfirmReports.calculating(true), true));
    }

    @Test
    void bigOrderStartNeedsAnEligibleEcoCpuNotTheNativeCpuList() {
        var unrepresentable = ready(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, false);
        assertEquals(
            ECOCraftConfirmRules.StartGate.NO_ELIGIBLE_ECO_CPU,
            ECOCraftConfirmRules.startGate(unrepresentable, false, false));
        assertEquals(
            ECOCraftConfirmRules.StartGate.READY,
            ECOCraftConfirmRules.startGate(unrepresentable.withBigOrderCpu(true), false, true),
            "AE2's empty CPU table does not matter for an ECO big order");

        var cyclic = ready(PlanningStatus.MISSING_ITEMS, true, true);
        assertEquals(
            ECOCraftConfirmRules.StartGate.PLAN_NOT_STARTABLE,
            ECOCraftConfirmRules.startGate(cyclic, false, false));
        assertEquals(
            ECOCraftConfirmRules.StartGate.NO_ELIGIBLE_ECO_CPU,
            ECOCraftConfirmRules.startGate(cyclic, true, false));
        assertEquals(
            ECOCraftConfirmRules.StartGate.READY,
            ECOCraftConfirmRules.startGate(cyclic.withBigOrderCpu(true), true, true));

        // An ordinary plan ignores the big-order CPU flag.
        var success = ready(PlanningStatus.SUCCESS, false, false).withBigOrderCpu(true);
        assertEquals(
            ECOCraftConfirmRules.StartGate.NO_MATCHING_CPU,
            ECOCraftConfirmRules.startGate(success, false, true));
    }

    @Test
    void serverOnlyAcceptsBigOrdersForEligiblePlans() {
        assertNull(
            ECOCraftConfirmRules
                .rejectBigOrderStart(ready(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, false), false));
        assertNull(ECOCraftConfirmRules.rejectBigOrderStart(ready(PlanningStatus.MISSING_ITEMS, true, true), true));
        assertEquals(
            ECOCraftConfirmError.INCOMPLETE_PLAN,
            ECOCraftConfirmRules.rejectBigOrderStart(ready(PlanningStatus.MISSING_ITEMS, true, true), false));
        assertEquals(
            ECOCraftConfirmError.INCOMPLETE_PLAN,
            ECOCraftConfirmRules.rejectBigOrderStart(ready(PlanningStatus.SUCCESS, false, false), false));
        assertEquals(
            ECOCraftConfirmError.NO_PLAN,
            ECOCraftConfirmRules.rejectBigOrderStart(ECOCraftConfirmReports.calculating(true), true));
    }

    @Test
    void submitResultsMapToTheErrorPage() {
        assertEquals(ECOCraftConfirmError.CPU_BUSY, ECOCraftConfirmRules.submitError(CraftingSubmitErrorCode.CPU_BUSY));
        assertEquals(
            ECOCraftConfirmError.CPU_OFFLINE,
            ECOCraftConfirmRules.submitError(CraftingSubmitErrorCode.CPU_OFFLINE));
        assertEquals(
            ECOCraftConfirmError.CPU_TOO_SMALL,
            ECOCraftConfirmRules.submitError(CraftingSubmitErrorCode.CPU_TOO_SMALL));
        assertEquals(
            ECOCraftConfirmError.INCOMPLETE_PLAN,
            ECOCraftConfirmRules.submitError(CraftingSubmitErrorCode.INCOMPLETE_PLAN));
        assertEquals(
            ECOCraftConfirmError.MISSING_INGREDIENT,
            ECOCraftConfirmRules.submitError(CraftingSubmitErrorCode.MISSING_INGREDIENT));
        assertEquals(
            ECOCraftConfirmError.BIG_ORDER_NO_CPU,
            ECOCraftConfirmRules.submitError(CraftingSubmitErrorCode.NO_CPU_FOUND));
        assertEquals(ECOCraftConfirmError.SUBMIT_FAILED, ECOCraftConfirmRules.submitError(null));
    }

    @Test
    void bigOrderCpuFollowsTheSelectionOrTheFirstIdleEcoCpu() {
        Object nativeCpu = new Object();
        Object busy = new Object();
        Object machineOnly = new Object();
        Object idle = new Object();
        Object second = new Object();
        List<Object> published = Arrays.asList(nativeCpu, busy, machineOnly, idle, second);
        Predicate<Object> idleEco = cpu -> cpu != nativeCpu && cpu != busy;
        Predicate<Object> acceptsPlayer = cpu -> cpu != machineOnly;

        assertSame(idle, ECOCraftConfirmRules.pickBigOrderCpu(null, published, idleEco, acceptsPlayer));
        assertSame(second, ECOCraftConfirmRules.pickBigOrderCpu(second, published, idleEco, acceptsPlayer));
        assertSame(
            machineOnly,
            ECOCraftConfirmRules.pickBigOrderCpu(machineOnly, published, idleEco, acceptsPlayer),
            "An explicitly selected idle ECO CPU is used like 1.21");
        assertNull(
            ECOCraftConfirmRules.pickBigOrderCpu(nativeCpu, published, idleEco, acceptsPlayer),
            "A selected native CPU cannot run a big order");
        assertNull(ECOCraftConfirmRules.pickBigOrderCpu(busy, published, idleEco, acceptsPlayer));
        assertNull(
            ECOCraftConfirmRules.pickBigOrderCpu(new Object(), published, idleEco, acceptsPlayer),
            "A selection that is no longer published is rejected");
        assertNull(ECOCraftConfirmRules.pickBigOrderCpu(null, Arrays.asList(nativeCpu, busy), idleEco, acceptsPlayer));
    }

    @Test
    void exactMaterialTableFollowsTheSourceReport() {
        assertTrue(ECOCraftConfirmRules.useExactMaterialTable(PlanningStatus.UNSUPPORTED, true, true, false, false));
        assertFalse(
            ECOCraftConfirmRules.useExactMaterialTable(PlanningStatus.UNSUPPORTED, true, false, true, false),
            "A native fallback with its own rows keeps the summary table");
        assertTrue(ECOCraftConfirmRules.useExactMaterialTable(PlanningStatus.MISSING_ITEMS, true, false, true, false));
        assertFalse(ECOCraftConfirmRules.useExactMaterialTable(PlanningStatus.MISSING_ITEMS, true, false, false, true));
        assertTrue(
            ECOCraftConfirmRules
                .useExactMaterialTable(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, false, true, false));
        assertTrue(ECOCraftConfirmRules.useExactMaterialTable(PlanningStatus.SUCCESS, false, false, true, true));
        assertFalse(ECOCraftConfirmRules.useExactMaterialTable(PlanningStatus.SUCCESS, false, false, true, false));
        assertFalse(ECOCraftConfirmRules.useExactMaterialTable(null, false, false, false, false));
    }

    @Test
    void cpuTierGlyphsMatchTheReportTexture() {
        assertEquals(4, ECOCraftConfirmRules.cpuTierGlyph(1));
        assertEquals(6, ECOCraftConfirmRules.cpuTierGlyph(2));
        assertEquals(9, ECOCraftConfirmRules.cpuTierGlyph(3));
        assertEquals(0, ECOCraftConfirmRules.cpuTierGlyph(0));
    }
}
