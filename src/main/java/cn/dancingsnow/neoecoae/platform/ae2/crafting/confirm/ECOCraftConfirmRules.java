package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode;

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
        NO_MATCHING_CPU,
        /** A big order needs an idle, online ECO CPU (1.21's NO_ELIGIBLE_ECO_CPU). */
        NO_ELIGIBLE_ECO_CPU
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

    /**
     * Whether a start goes to an ECO CPU as a big order instead of AE2's submission (1.21's usesBigOrderSubmission):
     * an amount beyond AE2's long limits, or a forced shortage that contains a cycle. Ordinary shortages keep the
     * selected CPU and the plain force start; a missing cycle has no executable phase schedule yet and must be
     * replanned by an ECO parent order.
     */
    public static boolean usesBigOrderSubmission(ECOCraftConfirmState state, boolean forced) {
        if (!state.reportReady) return false;
        return state.status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
            || forced && state.status == PlanningStatus.MISSING_ITEMS && state.cycleDetected;
    }

    /** The report has a plan the start button may submit, ignoring CPU availability. */
    public static boolean isStartable(ECOCraftConfirmState state, boolean shiftDown) {
        if (!state.reportReady) return false;
        if (state.status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) return true;
        if (!state.simulation) return true;
        boolean force = isForceStart(state, shiftDown);
        return force && (canForceStart(state.status, state.cycleDetected) || usesBigOrderSubmission(state, true));
    }

    public static StartGate startGate(ECOCraftConfirmState state, boolean shiftDown, boolean noCpu) {
        if (!state.reportReady) return StartGate.WAITING_FOR_PLAN;
        if (!isStartable(state, shiftDown)) return StartGate.PLAN_NOT_STARTABLE;
        if (usesBigOrderSubmission(state, isForceStart(state, shiftDown)))
            return state.bigOrderCpuAvailable ? StartGate.READY : StartGate.NO_ELIGIBLE_ECO_CPU;
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

    /** Server-side check of a big-order request; {@code null} means the order may go on to admission. */
    public static @Nullable ECOCraftConfirmError rejectBigOrderStart(ECOCraftConfirmState state, boolean forced) {
        if (!state.reportReady) return ECOCraftConfirmError.NO_PLAN;
        return usesBigOrderSubmission(state, forced) ? null : ECOCraftConfirmError.INCOMPLETE_PLAN;
    }

    /** The report's error page for a failed big-order submission (1.21 shows AE2's submit result there). */
    public static ECOCraftConfirmError submitError(@Nullable CraftingSubmitErrorCode code) {
        if (code == null) return ECOCraftConfirmError.SUBMIT_FAILED;
        switch (code) {
            case CPU_BUSY:
                return ECOCraftConfirmError.CPU_BUSY;
            case CPU_OFFLINE:
                return ECOCraftConfirmError.CPU_OFFLINE;
            case CPU_TOO_SMALL:
                return ECOCraftConfirmError.CPU_TOO_SMALL;
            case INCOMPLETE_PLAN:
                return ECOCraftConfirmError.INCOMPLETE_PLAN;
            case MISSING_INGREDIENT:
                return ECOCraftConfirmError.MISSING_INGREDIENT;
            case NO_CPU_FOUND:
                return ECOCraftConfirmError.BIG_ORDER_NO_CPU;
            default:
                return ECOCraftConfirmError.SUBMIT_FAILED;
        }
    }

    /**
     * The ECO CPU a big order goes to (1.21's resolveBigOrderCpu): an explicitly selected CPU only when it is a
     * published idle ECO CPU, otherwise the first published idle ECO CPU whose selection mode accepts the source.
     *
     * @param selected the CPU chosen in the confirmation's CPU list, or {@code null} for automatic selection
     * @param idleEco  an online ECO CPU without a job that can still take one
     */
    public static <C> @Nullable C pickBigOrderCpu(@Nullable C selected, Iterable<? extends C> published,
        Predicate<? super C> idleEco, Predicate<? super C> acceptsSource) {
        if (selected != null) {
            for (C cpu : published) if (cpu == selected) return idleEco.test(cpu) ? cpu : null;
            return null;
        }
        for (C cpu : published) if (idleEco.test(cpu) && acceptsSource.test(cpu)) return cpu;
        return null;
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
