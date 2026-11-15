package cn.dancingsnow.neoecoae.compat.crafttweaker;

import cn.dancingsnow.neoecoae.compat.crafttweaker.WorkstationTweakerSupport.RecipeAction;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;
import minetweaker.MineTweakerAPI;
import minetweaker.api.item.IIngredient;
import minetweaker.api.item.IItemStack;
import minetweaker.api.liquid.ILiquidStack;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/** Loaded only after FML confirms MineTweaker3 is present; edits the single-block catalog only. */
@ZenClass("mods.neoecoae.IntegratedWorkingStation")
public final class IntegratedWorkstationTweaker {

    private IntegratedWorkstationTweaker() {}

    public static void register() {
        MineTweakerAPI.registerClass(IntegratedWorkstationTweaker.class);
    }

    @ZenMethod
    public static void addRecipe(String id, IItemStack output, IIngredient[] inputs, int energy, int ticks,
        @Optional ILiquidStack inputFluid, @Optional ILiquidStack outputFluid) {
        try {
            ResourceLocation key = WorkstationTweakerSupport.recipeId(id);
            var recipe = WorkstationTweakerSupport.recipe(output, inputs, energy, ticks, inputFluid, outputFluid, 9);
            MineTweakerAPI.apply(
                new RecipeAction(
                    "Add integrated workstation recipe " + key,
                    () -> WorkstationRecipes.addScriptRecipe(key, recipe)));
        } catch (IllegalArgumentException invalid) {
            MineTweakerAPI.logError("Integrated workstation: " + invalid.getMessage());
        }
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        try {
            ResourceLocation key = WorkstationTweakerSupport.recipeId(id);
            MineTweakerAPI.apply(
                new RecipeAction(
                    "Remove integrated workstation recipe " + key,
                    () -> WorkstationRecipes.removeScriptRecipes((recipeId, recipe) -> recipeId.equals(key))));
        } catch (IllegalArgumentException invalid) {
            MineTweakerAPI.logError("Integrated workstation: " + invalid.getMessage());
        }
    }

    @ZenMethod
    public static void remove(IIngredient output) {
        if (output == null) {
            MineTweakerAPI.logError("Integrated workstation: removal output must not be null");
            return;
        }
        MineTweakerAPI.apply(
            new RecipeAction(
                "Remove integrated workstation recipes for " + output,
                () -> WorkstationRecipes
                    .removeScriptRecipes((id, recipe) -> WorkstationTweakerSupport.matchesOutput(recipe, output))));
    }

    @ZenMethod
    public static void removeAll() {
        MineTweakerAPI.apply(
            new RecipeAction(
                "Remove all integrated workstation recipes",
                () -> WorkstationRecipes.removeScriptRecipes((id, recipe) -> true)));
    }
}
