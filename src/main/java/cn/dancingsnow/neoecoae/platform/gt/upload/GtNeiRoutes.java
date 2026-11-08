package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * Reads the GT recipe map (and the virtual circuit GT's NEI overlay strips) from the NEI page being transferred.
 * Loaded only when GregTech and NEI are both present.
 */
public final class GtNeiRoutes {

    private GtNeiRoutes() {}

    @com.github.bsideup.jabel.Desugar
    public record Hint(String map, ItemStack circuit) {}

    /** Reads the selected circuit, plus a recipe map for GT handlers; null when the page cannot be identified. */
    public static Hint read(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof GTNEIDefaultHandler)) {
            if (handler == null) return null;
            ItemStack circuit = null;
            List<PositionedStack> inputs = handler.getIngredientStacks(recipeIndex);
            if (inputs != null) for (PositionedStack input : inputs) {
                circuit = circuit(input);
                if (circuit != null) break;
            }
            return new Hint("", circuit);
        }
        GTNEIDefaultHandler gt = (GTNEIDefaultHandler) handler;
        var recipeMap = gt.getRecipeMap();
        String name = recipeMap == null ? null : recipeMap.unlocalizedName;
        if (name == null || name.trim()
            .isEmpty()) return null;
        return new Hint(name, circuit(gt, recipeIndex));
    }

    /** Uses the page's own {@code arecipes[recipeIndex]}; a global cache has a different order after filtering. */
    private static ItemStack circuit(GTNEIDefaultHandler handler, int recipeIndex) {
        List<?> recipes = handler.arecipes;
        if (recipes != null && recipeIndex >= 0 && recipeIndex < recipes.size()) {
            Object recipe = recipes.get(recipeIndex);
            if (recipe instanceof GTNEIDefaultHandler.CachedDefaultRecipe) {
                var cached = (GTNEIDefaultHandler.CachedDefaultRecipe) recipe;
                if (cached.mRecipe != null && cached.mRecipe.mInputs != null)
                    for (ItemStack stack : cached.mRecipe.mInputs) if (GtCircuits.isCircuit(stack)) return stack.copy();
                if (cached.mInputs != null) for (PositionedStack positioned : cached.mInputs) {
                    ItemStack circuit = circuit(positioned);
                    if (circuit != null) return circuit;
                }
            }
        }
        List<PositionedStack> inputs = handler.getIngredientStacks(recipeIndex);
        if (inputs != null) for (PositionedStack positioned : inputs) {
            ItemStack circuit = circuit(positioned);
            if (circuit != null) return circuit;
        }
        return null;
    }

    private static ItemStack circuit(PositionedStack positioned) {
        if (positioned == null || positioned.items == null) return null;
        for (ItemStack stack : positioned.items) if (GtCircuits.isCircuit(stack)) return stack.copy();
        return null;
    }
}
