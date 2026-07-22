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
import gregtech.common.items.ItemIntegratedCircuit;

final class PatternRecipeMatcher {

    private PatternRecipeMatcher() {}

    static boolean matches(RecipeMap<?> recipeMap, ICraftingPatternDetails details) {
        return matches(recipeMap, details, null);
    }

    /**
     * Matches a pattern against a GT recipe map. NEI deliberately omits non-consumable virtual
     * circuits from AE2 processing patterns, so {@code contextualCircuit} is added only when the
     * encoded pattern does not already contain one.
     */
    static boolean matches(RecipeMap<?> recipeMap, ICraftingPatternDetails details, ItemStack contextualCircuit) {
        if (recipeMap == null || details == null || details.isCraftable()) return false;
        final List<ItemStack> itemInputs = new ArrayList<>();
        final List<FluidStack> fluidInputs = new ArrayList<>();
        boolean hasCircuit = false;
        IAEStack<?>[] inputs = details.getCondensedAEInputs();
        if (inputs == null) return false;
        for (IAEStack<?> input : inputs) {
            if (input == null) continue;
            if (input instanceof IAEItemStack) {
                ItemStack stack = PatternCircuitCompat.normalizeForRecipe(((IAEItemStack) input).getItemStack());
                // An empty Programmable Hatch circuit is a reset marker, not a recipe ingredient.
                if (stack == null) continue;
                stack.stackSize = boundedAmount(input.getStackSize());
                itemInputs.add(stack);
                hasCircuit |= PatternCircuitCompat.isVirtualCircuit(((IAEItemStack) input).getItemStack());
            } else if (input instanceof IAEFluidStack) {
                FluidStack stack = ((IAEFluidStack) input).getFluidStack()
                    .copy();
                stack.amount = boundedAmount(input.getStackSize());
                fluidInputs.add(stack);
            }
        }
        if (!hasCircuit && validCircuit(contextualCircuit)) {
            ItemStack circuit = PatternCircuitCompat.normalizeForRecipe(contextualCircuit);
            if (circuit == null) return false;
            circuit.stackSize = 1;
            itemInputs.add(circuit);
        }
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs == null || outputs.length == 0) return false;
        GTRecipe recipe = recipeMap.findRecipeQuery()
            .items(itemInputs.toArray(new ItemStack[itemInputs.size()]))
            .fluids(fluidInputs.toArray(new FluidStack[fluidInputs.size()]))
            // A pattern may represent several executions of one GT recipe. The route decision only
            // needs the recipe shape, so do not reject a valid map because of a batch amount.
            .notUnificated(true)
            .dontCheckStackSizes(true)
            .filter(candidate -> outputsMatch(candidate, outputs))
            .find();
        return recipe != null;
    }

    /**
     * Matches the map while allowing GT to choose the recipe's virtual circuit. This is used only
     * for display/discovery when NEI omitted the circuit and no route context is available. Exact
     * uploads still require the target's circuit to match the encoded recipe.
     */
    static boolean matchesAnyIntegratedCircuit(RecipeMap<?> recipeMap, ICraftingPatternDetails details) {
        return matchesAnyCircuit(recipeMap, details, null);
    }

    static boolean matchesAnyCircuit(RecipeMap<?> recipeMap, ICraftingPatternDetails details, ItemStack preferred) {
        if (preferred != null && matches(recipeMap, details, preferred)) return true;
        if (ItemIntegratedCircuit.NON_ZERO_VARIANTS != null) {
            for (ItemStack variant : ItemIntegratedCircuit.NON_ZERO_VARIANTS) {
                if (variant != null && matches(recipeMap, details, variant)) return true;
            }
            if (!ItemIntegratedCircuit.NON_ZERO_VARIANTS.isEmpty()) {
                ItemStack zero = ItemIntegratedCircuit.NON_ZERO_VARIANTS.get(0)
                    .copy();
                zero.setItemDamage(0);
                if (matches(recipeMap, details, zero)) return true;
            }
        }
        return matches(recipeMap, details, null);
    }

    private static boolean outputsMatch(GTRecipe recipe, IAEStack<?>[] outputs) {
        if (recipe == null || outputs == null || outputs.length == 0) return false;
        for (IAEStack<?> output : outputs) {
            if (output == null) continue;
            boolean found = false;
            if (output instanceof IAEItemStack) {
                ItemStack expected = ((IAEItemStack) output).getItemStack();
                if (expected == null || recipe.mOutputs == null) return false;
                for (ItemStack actual : recipe.mOutputs) {
                    if (actual != null && GTUtility.areStacksEqual(expected, actual, true)) {
                        found = true;
                        break;
                    }
                }
            } else if (output instanceof IAEFluidStack) {
                FluidStack expected = ((IAEFluidStack) output).getFluidStack();
                if (expected == null || recipe.mFluidOutputs == null) return false;
                for (FluidStack actual : recipe.mFluidOutputs) {
                    if (GTUtility.areFluidsEqual(expected, actual, true)) {
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

    private static boolean validCircuit(ItemStack stack) {
        if (!PatternCircuitCompat.isVirtualCircuit(stack)) return false;
        ItemStack normalized = PatternCircuitCompat.unwrap(stack);
        if (normalized == null || !(normalized.getItem() instanceof ItemIntegratedCircuit)) return true;
        int damage = normalized.getItemDamage();
        return damage >= 0 && damage <= ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER;
    }
}
