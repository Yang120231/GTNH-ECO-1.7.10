package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Native contract for the pinned AE2 transform algorithm; quantities are one item per ingredient. */
@com.github.bsideup.jabel.Desugar
public record NativeTransformRecipe(ResourceLocation id, List<SourceIngredient> ingredients, ItemStack result,
    boolean explosion, SourceFluidIngredient fluid) {

    public NativeTransformRecipe {
        java.util.Objects.requireNonNull(id);
        ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
        if (ingredients.isEmpty() || result == null || result.stackSize <= 0)
            throw new IllegalArgumentException("Empty native world transformation");
        if (!explosion && (fluid == null || fluid.isEmpty()))
            throw new IllegalArgumentException("Missing world transformation fluid");
        result = result.copy();
    }

    public ItemStack result() {
        return result.copy();
    }

    public boolean acceptsAnchor(ItemStack stack) {
        if (!explosion) return ingredients.get(0)
            .test(stack);
        for (SourceIngredient ingredient : ingredients) if (ingredient.test(stack)) return true;
        return false;
    }

    public boolean inFluid(Fluid identity) {
        return !explosion && fluid.accepts(identity);
    }

    /** Simulates the source's ordered ingredient claims without touching live stacks on failure. */
    public int[] claimed(List<ItemStack> provided, ItemStack anchor) {
        if (!acceptsAnchor(anchor)) return null;
        boolean[] missing = new boolean[ingredients.size()];
        java.util.Arrays.fill(missing, true);
        int[] consumed = new int[provided.size()];
        int left = missing.length;
        for (int i = 0; i < provided.size(); i++) {
            ItemStack stack = provided.get(i);
            if (stack == null || stack.stackSize <= 0) continue;
            for (int j = 0; j < missing.length; j++) {
                if (missing[j] && ingredients.get(j)
                    .test(stack) && stack.stackSize - consumed[i] > 0) {
                    consumed[i]++;
                    missing[j] = false;
                    left--;
                }
            }
        }
        return left == 0 ? consumed : null;
    }
}
