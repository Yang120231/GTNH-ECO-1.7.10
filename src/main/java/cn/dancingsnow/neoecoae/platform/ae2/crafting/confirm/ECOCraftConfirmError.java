package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

/** Submission failures shown on the ECO report's error page. */
public enum ECOCraftConfirmError {

    /** No ECO or native CPU accepted the job. */
    NO_CPU_ACCEPTED("gui.neoecoae.crafting_report.error.no_cpu"),
    /** The plan is a simulation and was not force-started. */
    INCOMPLETE_PLAN("gui.neoecoae.crafting_report.error.incomplete_plan"),
    /** Force start is only allowed for an acyclic plan that is short of materials. */
    FORCE_REJECTED("gui.neoecoae.crafting_report.error.force_rejected"),
    /** The confirmation no longer holds a plan to submit. */
    NO_PLAN("gui.neoecoae.crafting_report.error.no_plan"),
    /** Submission threw; the detail carries the exception text. */
    SUBMIT_FAILED("gui.neoecoae.crafting_report.error.submit_failed");

    private final String translationKey;

    ECOCraftConfirmError(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
