package cn.dancingsnow.neoecoae.compat.crafttweaker;

import cn.dancingsnow.neoecoae.compat.crafttweaker.WorkstationTweakerSupport.RecipeAction;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import minetweaker.MineTweakerAPI;
import minetweaker.api.item.IIngredient;
import minetweaker.api.item.IItemStack;
import minetweaker.api.liquid.ILiquidStack;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/** Loaded only after FML confirms MineTweaker3 is present. Uses the legacy 1.7.10 API. */
@ZenClass("mods.neoecoae.LargeIntegratedWorkingStation")
public final class LargeWorkstationTweaker {

    private LargeWorkstationTweaker() {}

    public static void register() {
        MineTweakerAPI.registerClass(LargeWorkstationTweaker.class);
    }

    @ZenMethod
    public static void addRecipe(String id, IItemStack output, IIngredient[] inputs, int energy, int ticks,
        @Optional ILiquidStack inputFluid, @Optional ILiquidStack outputFluid) {
        try {
            ResourceLocation key = WorkstationTweakerSupport.recipeId(id);
            var display = WorkstationTweakerSupport.recipe(output, inputs, energy, ticks, inputFluid, outputFluid, 12);
            var recipe = new LargeWorkstationRecipe(key, display, energy, java.util.Collections.emptyList());
            MineTweakerAPI.apply(
                new RecipeAction(
                    "Add large workstation recipe " + recipe.id(),
                    () -> LargeWorkstationRecipes.addScriptRecipe(recipe)));
        } catch (IllegalArgumentException ignored) {
        }
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        try {
            ResourceLocation key = WorkstationTweakerSupport.recipeId(id);
            MineTweakerAPI.apply(
                new RecipeAction(
                    "Remove large workstation recipe " + key,
                    () -> LargeWorkstationRecipes.removeScriptRecipes(
                        recipe -> recipe.id()
                            .equals(key))));
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Matches item outputs (including script conditions), or fluid outputs passed as liquid ingredients. */
    @ZenMethod
    public static void remove(IIngredient output) {
        if (output == null) return;
        MineTweakerAPI.apply(
            new RecipeAction(
                "Remove large workstation recipes for " + output,
                () -> LargeWorkstationRecipes
                    .removeScriptRecipes(recipe -> WorkstationTweakerSupport.matchesOutput(recipe.display(), output))));
    }

    @ZenMethod
    public static void removeAll() {
        MineTweakerAPI.apply(
            new RecipeAction(
                "Remove all large workstation recipes",
                () -> LargeWorkstationRecipes.removeScriptRecipes(recipe -> true)));
    }

}
