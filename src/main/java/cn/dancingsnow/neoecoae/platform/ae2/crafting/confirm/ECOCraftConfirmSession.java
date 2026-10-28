package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

/**
 * Server-side ECO state of one open confirmation container, independent of AE2 and Minecraft classes. The tracker
 * feeds it what the container holds each tick; it decides what the client still has to receive. The header is
 * resent whenever it changes, the material/cycle report only once per plan.
 */
public final class ECOCraftConfirmSession {

    /** One pending client update. {@code report} is {@code null} when the client already holds it. */
    public static final class Outgoing {

        public final ECOCraftConfirmState state;
        @Nullable
        public final ECOCraftConfirmReport report;

        Outgoing(ECOCraftConfirmState state, @Nullable ECOCraftConfirmReport report) {
            this.state = state;
            this.report = report;
        }
    }

    private boolean eco;
    private boolean cyclePlanningEnabled;
    private Object observedResult;
    private long nextVersion = 1L;
    private ECOCraftConfirmState base = ECOCraftConfirmState.NONE;
    private ECOCraftConfirmReport report = ECOCraftConfirmReport.EMPTY;
    private int cpuTier;
    @Nullable
    private ECOCraftConfirmError error;
    private String errorDetail = "";
    @Nullable
    private ECOCraftConfirmState sentState;
    private long sentReportVersion = -1L;

    public boolean isEco() {
        return eco;
    }

    /** The request was routed to ECO: the report replaces the native screen and shows its calculating state. */
    public void begin(boolean cyclePlanningEnabled) {
        if (eco) return;
        eco = true;
        this.cyclePlanningEnabled = cyclePlanningEnabled;
        base = ECOCraftConfirmReports.calculating(cyclePlanningEnabled);
    }

    public boolean cyclePlanningEnabled() {
        return cyclePlanningEnabled;
    }

    /**
     * Records the container's current result. A new result identity rebuilds the report and clears a stale error;
     * the builder is only invoked for a changed ECO result.
     *
     * @param builder builds the report of an ECO result, or {@code null} when the result is not ECO's
     */
    public void observeResult(@Nullable Object result,
        @Nullable java.util.function.LongFunction<ECOCraftConfirmReports.Built> builder) {
        if (!eco || result == observedResult) return;
        observedResult = result;
        if (result == null || builder == null) return;
        ECOCraftConfirmReports.Built built = builder.apply(nextVersion++);
        base = built.state;
        report = built.report;
        error = null;
        errorDetail = "";
    }

    /** Planning ended without a result (AE2 closes the container on a planner exception). */
    public void planningFailed(Supplier<String> message) {
        if (!eco || base.reportReady) return;
        ECOCraftConfirmReports.Built built = ECOCraftConfirmReports
            .failed(message.get(), cyclePlanningEnabled, nextVersion++);
        base = built.state;
        report = built.report;
    }

    public void setCpuTier(int tier) {
        cpuTier = tier;
    }

    public void fail(ECOCraftConfirmError error, @Nullable String detail) {
        this.error = error;
        this.errorDetail = detail == null ? "" : detail;
    }

    public void clearError() {
        error = null;
        errorDetail = "";
    }

    /** The header as the client should currently see it. */
    public ECOCraftConfirmState state() {
        if (!eco) return ECOCraftConfirmState.NONE;
        return base.withCpuTier(cpuTier)
            .withError(error, errorDetail);
    }

    public ECOCraftConfirmReport report() {
        return report;
    }

    /** The update the client is missing, or {@code null} when it is current. Marks it as sent. */
    public @Nullable Outgoing drain() {
        if (!eco) return null;
        ECOCraftConfirmState state = state();
        boolean reportChanged = state.reportReady && report.version != sentReportVersion;
        if (!reportChanged && state.equals(sentState)) return null;
        sentState = state;
        if (reportChanged) sentReportVersion = report.version;
        return new Outgoing(state, reportChanged ? report : null);
    }
}
