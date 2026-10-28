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
    SUBMIT_FAILED("gui.neoecoae.crafting_report.error.submit_failed"),
    /** The grid or the chosen CPU went offline before the big order was accepted. */
    CPU_OFFLINE("gui.neoecoae.crafting_report.error.cpu_offline"),
    /** The ECO CPU already runs a job. */
    CPU_BUSY("gui.neoecoae.crafting_report.error.cpu_busy"),
    /** The ECO CPU has too little storage for the order. */
    CPU_TOO_SMALL("gui.neoecoae.crafting_report.error.cpu_too_small"),
    /** Stored materials changed since the report was built; the detail names the missing input. */
    MISSING_INGREDIENT("gui.neoecoae.crafting_report.error.missing_ingredient"),
    /** A big order needs an idle, online ECO crafting CPU and none is available. */
    BIG_ORDER_NO_CPU("gui.neoecoae.crafting_report.error.big_order_no_cpu");

    private final String translationKey;

    ECOCraftConfirmError(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
