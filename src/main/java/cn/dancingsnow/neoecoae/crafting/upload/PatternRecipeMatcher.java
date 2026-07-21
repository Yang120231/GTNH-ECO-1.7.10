package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

final class PatternRecipeMatcher {

    private PatternRecipeMatcher() {}

    static boolean matches(RecipeMap<?> recipeMap, ICraftingPatternDetails details) {
        if (recipeMap == null || details == null || details.isCraftable()) return false;
        final List<ItemStack> itemInputs = new ArrayList<>();
        final List<FluidStack> fluidInputs = new ArrayList<>();
        for (IAEStack<?> input : details.getCondensedAEInputs()) {
            if (input instanceof IAEItemStack) {
                ItemStack stack = ((IAEItemStack) input).getItemStack()
                    .copy();
                stack.stackSize = boundedAmount(input.getStackSize());
                itemInputs.add(stack);
            } else if (input instanceof IAEFluidStack) {
                FluidStack stack = ((IAEFluidStack) input).getFluidStack()
                    .copy();
                stack.amount = boundedAmount(input.getStackSize());
                fluidInputs.add(stack);
            }
        }
        GTRecipe recipe = recipeMap.findRecipeQuery()
            .items(itemInputs.toArray(new ItemStack[itemInputs.size()]))
            .fluids(fluidInputs.toArray(new FluidStack[fluidInputs.size()]))
            .filter(candidate -> outputsMatch(candidate, details.getCondensedAEOutputs()))
            .find();
        return recipe != null;
    }

    private static boolean outputsMatch(GTRecipe recipe, IAEStack<?>[] outputs) {
        for (IAEStack<?> output : outputs) {
            boolean found = false;
            if (output instanceof IAEItemStack) {
                ItemStack expected = ((IAEItemStack) output).getItemStack();
                for (ItemStack actual : recipe.mOutputs) {
                    if (actual != null && GTUtility.areStacksEqual(expected, actual)
                        && actual.stackSize >= boundedAmount(output.getStackSize())) {
                        found = true;
                        break;
                    }
                }
            } else if (output instanceof IAEFluidStack) {
                FluidStack expected = ((IAEFluidStack) output).getFluidStack();
                for (FluidStack actual : recipe.mFluidOutputs) {
                    if (actual != null && actual.isFluidEqual(expected)
                        && actual.amount >= boundedAmount(output.getStackSize())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return false;
        }
        return outputs.length > 0;
    }

    private static int boundedAmount(long amount) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, amount));
    }
}
