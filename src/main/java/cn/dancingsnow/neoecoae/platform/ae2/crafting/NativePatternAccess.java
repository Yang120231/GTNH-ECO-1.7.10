package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.lang.reflect.Field;

import net.minecraft.item.crafting.IRecipe;

import appeng.helpers.PatternHelper;

/** The pinned native pattern stores the actual recipe privately; do not reselect a different global recipe. */
public final class NativePatternAccess {

    private static final Field RECIPE = recipeField();

    private NativePatternAccess() {}

    private static Field recipeField() {
        try {
            Field field = PatternHelper.class.getDeclaredField("standardRecipe");
            if (!IRecipe.class.isAssignableFrom(field.getType()))
                throw new IllegalStateException("Native recipe field changed type");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned native pattern recipe access changed", failure);
        }
    }

    public static boolean available() {
        return RECIPE != null;
    }

    public static IRecipe recipe(PatternHelper pattern) {
        try {
            return java.util.Objects
                .requireNonNull((IRecipe) RECIPE.get(pattern), "Native crafting pattern has no recipe");
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Native pattern recipe access denied", failure);
        }
    }
}
