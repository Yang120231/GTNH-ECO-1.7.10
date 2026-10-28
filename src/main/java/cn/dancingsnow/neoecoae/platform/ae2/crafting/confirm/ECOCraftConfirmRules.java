package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;

/**
 * Start gating and table selection of the 1.21 ECO report, kept free of client and AE2 classes so the server
 * submission check and the screen use the same rules.
 */
public final class ECOCraftConfirmRules {

    private ECOCraftConfirmRules() {}

    public enum StartGate {
        READY,
        WAITING_FOR_PLAN,
        PLAN_NOT_STARTABLE,
        NO_MATCHING_CPU
    }

    /** Only empty simulation shells may replace the summary table with the planner's material nodes. */
    public static boolean isDiagnosticShell(@Nullable PlanningStatus status, boolean simulation, boolean empty) {
        if (status == null || !simulation || !empty) return false;
        switch (status) {
            case PARTIAL_UNSUPPORTED:
            case UNSUPPORTED:
            case INTERNAL_ERROR:
            case CYCLE_UNSUPPORTED:
            case AMOUNT_OVERFLOW:
                return true;
            default:
                return false;
        }
    }

    /** Missing ECO plans use the same quantities as the graph, including acyclic plans. */
    public static boolean hasMissingMaterialSnapshot(@Nullable PlanningStatus status, boolean hasNodes) {
        return (status == PlanningStatus.MISSING_ITEMS || status == PlanningStatus.CYCLE_UNRESOLVED) && hasNodes;
    }

    /**
     * Whether the report shows the planner's exact material nodes instead of AE2's long summary. A native fallback
     * that only explains an unsupported attempt keeps the summary table.
     */
    public static boolean useExactMaterialTable(@Nullable PlanningStatus status, boolean simulation,
        boolean summaryEmpty, boolean hasNodes, boolean hasCycleItems) {
        if (isDiagnosticShell(status, simulation, summaryEmpty)) return true;
        if (hasMissingMaterialSnapshot(status, hasNodes)) return true;
        if (status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) return true;
        if (!hasCycleItems) return false;
        return status != PlanningStatus.MISSING_ITEMS && status != PlanningStatus.PARTIAL_UNSUPPORTED
            && status != PlanningStatus.UNSUPPORTED
            && status != PlanningStatus.INTERNAL_ERROR;
    }

    /**
     * Force start turns missing inputs into waiting items. It is only sound for an acyclic plan whose sole defect is
     * a material shortage: a missing cycle has no executable phase schedule yet (1.21 sends it to a big order).
     */
    public static boolean canForceStart(@Nullable PlanningStatus status, boolean cycleDetected) {
        return (status == null || status == PlanningStatus.MISSING_ITEMS) && !cycleDetected;
    }

    public static boolean isForceStart(ECOCraftConfirmState state, boolean shiftDown) {
        return shiftDown && state.reportReady && state.simulation;
    }

    /** The report has a plan the start button may submit, ignoring CPU availability. */
    public static boolean isStartable(ECOCraftConfirmState state, boolean shiftDown) {
        if (!state.reportReady || state.status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) return false;
        if (!state.simulation) return true;
        return isForceStart(state, shiftDown) && canForceStart(state.status, state.cycleDetected);
    }

    public static StartGate startGate(ECOCraftConfirmState state, boolean shiftDown, boolean noCpu) {
        if (!state.reportReady) return StartGate.WAITING_FOR_PLAN;
        if (!isStartable(state, shiftDown)) return StartGate.PLAN_NOT_STARTABLE;
        return noCpu ? StartGate.NO_MATCHING_CPU : StartGate.READY;
    }

    /** Server-side check of a start request; {@code null} means the submission may proceed. */
    public static @Nullable ECOCraftConfirmError rejectStart(ECOCraftConfirmState state, boolean forced) {
        if (!state.reportReady) return ECOCraftConfirmError.NO_PLAN;
        if (state.status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE)
            return ECOCraftConfirmError.INCOMPLETE_PLAN;
        if (!state.simulation) return null;
        if (!forced) return ECOCraftConfirmError.INCOMPLETE_PLAN;
        return canForceStart(state.status, state.cycleDetected) ? null : ECOCraftConfirmError.FORCE_REJECTED;
    }

    /** CPU tier glyph of the report header for an ECO storage tier (1, 2, 3). */
    public static int cpuTierGlyph(int storageTier) {
        switch (storageTier) {
            case 1:
                return 4;
            case 2:
                return 6;
            case 3:
                return 9;
            default:
                return 0;
        }
    }
}
