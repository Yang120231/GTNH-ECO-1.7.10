package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

public interface ICraftingSubmitResult {

    boolean successful();

    @Nullable
    ICraftingLink link();

    @Nullable
    CraftingSubmitErrorCode errorCode();

    @Nullable
    GenericStack missingIngredient();
}
