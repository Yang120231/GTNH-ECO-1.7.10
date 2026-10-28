package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;

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
        assertFalse(ECOCraftConfirmRules.isStartable(cyclic, true));
        assertEquals(ECOCraftConfirmError.FORCE_REJECTED, ECOCraftConfirmRules.rejectStart(cyclic, true));
        for (PlanningStatus status : new PlanningStatus[] { PlanningStatus.CYCLE_UNRESOLVED, PlanningStatus.UNSUPPORTED,
            PlanningStatus.INTERNAL_ERROR, PlanningStatus.PARTIAL }) {
            var state = ready(status, true, false);
            assertFalse(ECOCraftConfirmRules.isStartable(state, true), status.name());
            assertEquals(ECOCraftConfirmError.FORCE_REJECTED, ECOCraftConfirmRules.rejectStart(state, true));
        }
        var unrepresentable = ready(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, false);
        assertFalse(ECOCraftConfirmRules.isStartable(unrepresentable, true));
        assertEquals(ECOCraftConfirmError.INCOMPLETE_PLAN, ECOCraftConfirmRules.rejectStart(unrepresentable, false));
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
