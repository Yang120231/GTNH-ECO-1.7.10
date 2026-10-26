package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

@com.github.bsideup.jabel.Desugar
public record CraftingSubmitResult(boolean successful, @Nullable ICraftingLink link,
    @Nullable CraftingSubmitErrorCode errorCode, @Nullable GenericStack missingIngredient)
    implements ICraftingSubmitResult {

    public static final CraftingSubmitResult CPU_BUSY = failed(CraftingSubmitErrorCode.CPU_BUSY);
    public static final CraftingSubmitResult CPU_OFFLINE = failed(CraftingSubmitErrorCode.CPU_OFFLINE);
    public static final CraftingSubmitResult CPU_TOO_SMALL = failed(CraftingSubmitErrorCode.CPU_TOO_SMALL);
    public static final CraftingSubmitResult INCOMPLETE_PLAN = failed(CraftingSubmitErrorCode.INCOMPLETE_PLAN);

    private static CraftingSubmitResult failed(CraftingSubmitErrorCode error) {
        return new CraftingSubmitResult(false, null, error, null);
    }

    public static CraftingSubmitResult successful(@Nullable ICraftingLink link) {
        return new CraftingSubmitResult(true, link, null, null);
    }

    public static CraftingSubmitResult missingIngredient(GenericStack missing) {
        return new CraftingSubmitResult(false, null, CraftingSubmitErrorCode.MISSING_INGREDIENT, missing);
    }
}
