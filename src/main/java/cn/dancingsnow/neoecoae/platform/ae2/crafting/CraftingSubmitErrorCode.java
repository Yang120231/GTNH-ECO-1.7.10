package cn.dancingsnow.neoecoae.platform.ae2.crafting;

/** Source submission outcomes, separate from a nullable native link. */
public enum CraftingSubmitErrorCode {
    CPU_BUSY,
    CPU_OFFLINE,
    CPU_TOO_SMALL,
    INCOMPLETE_PLAN,
    MISSING_INGREDIENT,
    /** No CPU thread could take the job, matching AE2's NO_CPU_FOUND. */
    NO_CPU_FOUND
}
