package cn.dancingsnow.neoecoae.platform.recipe;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Native directory entry: the stable recipe identity travels with its compiled definition. */
@com.github.bsideup.jabel.Desugar
public record RecipeHolder<T> (ResourceLocation id, T value) {}
