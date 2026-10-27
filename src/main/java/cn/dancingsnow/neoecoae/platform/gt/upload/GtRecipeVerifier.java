package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.ArrayList;
import java.util.Iterator;
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

/**
 * Two-step recipe check. GT's lenient query only proposes candidates; each candidate is then re-checked with the
 * pattern's full quantities, which must be one consistent integer multiple of the recipe. Amounts stay in long.
 */
final class GtRecipeVerifier {

    enum Verdict {
        EXACT,
        PROBABILISTIC,
        RATIO_MISMATCH,
        NONE
    }

    private static final int CERTAIN = 10000;

    private GtRecipeVerifier() {}

    /** {@code circuit} is the non-consumed selector supplied by the machine or NEI when the pattern omits it. */
    static Verdict verify(RecipeMap<?> map, ICraftingPatternDetails details, ItemStack circuit) {
        Pattern pattern = Pattern.of(details, circuit);
        if (pattern == null || pattern.outputs.isEmpty()) return Verdict.NONE;
        Verdict best = Verdict.NONE;
        Iterator<GTRecipe> candidates;
        try {
            candidates = map.findRecipeQuery()
                .items(pattern.queryItems())
                .fluids(pattern.queryFluids())
                .notUnificated(true)
                .dontCheckStackSizes(true)
                .filter(recipe -> outputKindsPresent(recipe, pattern))
                .findAll()
                .iterator();
        } catch (RuntimeException failure) {
            return Verdict.NONE;
        }
        int inspected = 0;
        while (candidates.hasNext() && inspected++ < 64) {
            Verdict verdict = ratio(candidates.next(), pattern);
            if (verdict.ordinal() < best.ordinal()) best = verdict;
            if (best == Verdict.EXACT) break;
        }
        return best;
    }

    private static boolean outputKindsPresent(GTRecipe recipe, Pattern pattern) {
        for (ItemStack output : pattern.outputs) if (recipeItemOutput(recipe, output) < 0) return false;
        for (FluidStack output : pattern.fluidOutputs) if (recipeFluidOutput(recipe, output) < 0) return false;
        return true;
    }

    private static Verdict ratio(GTRecipe recipe, Pattern pattern) {
        long[] multiple = { 0 };
        boolean[] used = new boolean[pattern.items.size()];
        if (recipe.mInputs != null) for (ItemStack input : recipe.mInputs) {
            if (input == null) continue;
            long total = 0;
            for (int index = 0; index < pattern.items.size(); index++) {
                if (!sameItem(input, pattern.items.get(index))) continue;
                used[index] = true;
                total += pattern.itemAmounts.get(index);
            }
            if (input.stackSize <= 0) {
                if (total <= 0) return Verdict.RATIO_MISMATCH;
                continue;
            }
            if (!scale(multiple, total, input.stackSize)) return Verdict.RATIO_MISMATCH;
        }
        // Every encoded ingredient must be consumed (or be a non-consumed selector) by this recipe.
        for (int index = 0; index < used.length; index++)
            if (!used[index] && !GtCircuits.isCircuit(pattern.items.get(index))) return Verdict.RATIO_MISMATCH;
        boolean[] fluidUsed = new boolean[pattern.fluids.size()];
        if (recipe.mFluidInputs != null) for (FluidStack input : recipe.mFluidInputs) {
            if (input == null) continue;
            long total = 0;
            for (int index = 0; index < pattern.fluids.size(); index++) {
                if (!GTUtility.areFluidsEqual(input, pattern.fluids.get(index), true)) continue;
                fluidUsed[index] = true;
                total += pattern.fluidAmounts.get(index);
            }
            if (input.amount <= 0) continue;
            if (!scale(multiple, total, input.amount)) return Verdict.RATIO_MISMATCH;
        }
        for (boolean value : fluidUsed) if (!value) return Verdict.RATIO_MISMATCH;

        boolean probabilistic = false;
        for (int index = 0; index < pattern.outputs.size(); index++) {
            int slot = recipeItemOutput(recipe, pattern.outputs.get(index));
            if (slot < 0) return Verdict.RATIO_MISMATCH;
            if (recipe.mOutputChances != null && slot < recipe.mOutputChances.length
                && recipe.mOutputChances[slot] < CERTAIN) probabilistic = true;
            if (!scale(multiple, pattern.outputAmounts.get(index), recipe.mOutputs[slot].stackSize))
                return Verdict.RATIO_MISMATCH;
        }
        for (int index = 0; index < pattern.fluidOutputs.size(); index++) {
            int slot = recipeFluidOutput(recipe, pattern.fluidOutputs.get(index));
            if (slot < 0 || !scale(multiple, pattern.fluidOutputAmounts.get(index), recipe.mFluidOutputs[slot].amount))
                return Verdict.RATIO_MISMATCH;
        }
        if (multiple[0] <= 0) return Verdict.RATIO_MISMATCH;
        return probabilistic ? Verdict.PROBABILISTIC : Verdict.EXACT;
    }

    /** {@code actual} must be the shared multiple of {@code unit}; the first pair fixes the multiple. */
    private static boolean scale(long[] multiple, long actual, long unit) {
        if (unit <= 0 || actual <= 0 || actual % unit != 0) return false;
        long factor = actual / unit;
        if (multiple[0] == 0) multiple[0] = factor;
        return multiple[0] == factor;
    }

    private static boolean sameItem(ItemStack recipe, ItemStack pattern) {
        return GTUtility.areStacksEqual(recipe, pattern, true) || GTUtility.areUnificationsEqual(recipe, pattern, true);
    }

    private static int recipeItemOutput(GTRecipe recipe, ItemStack output) {
        if (recipe.mOutputs == null) return -1;
        for (int slot = 0; slot < recipe.mOutputs.length; slot++)
            if (recipe.mOutputs[slot] != null && GTUtility.areStacksEqual(output, recipe.mOutputs[slot], true))
                return slot;
        return -1;
    }

    private static int recipeFluidOutput(GTRecipe recipe, FluidStack output) {
        if (recipe.mFluidOutputs == null) return -1;
        for (int slot = 0; slot < recipe.mFluidOutputs.length; slot++) if (recipe.mFluidOutputs[slot] != null
            && GTUtility.areFluidsEqual(output, recipe.mFluidOutputs[slot], true)) return slot;
        return -1;
    }

    private static final class Pattern {

        final List<ItemStack> items = new ArrayList<>();
        final List<Long> itemAmounts = new ArrayList<>();
        final List<FluidStack> fluids = new ArrayList<>();
        final List<Long> fluidAmounts = new ArrayList<>();
        final List<ItemStack> outputs = new ArrayList<>();
        final List<Long> outputAmounts = new ArrayList<>();
        final List<FluidStack> fluidOutputs = new ArrayList<>();
        final List<Long> fluidOutputAmounts = new ArrayList<>();

        static Pattern of(ICraftingPatternDetails details, ItemStack circuit) {
            if (details == null || details.isCraftable()) return null;
            Pattern pattern = new Pattern();
            IAEStack<?>[] inputs = details.getCondensedAEInputs();
            IAEStack<?>[] outputs = details.getCondensedAEOutputs();
            if (inputs == null || outputs == null) return null;
            boolean hasCircuit = false;
            for (IAEStack<?> input : inputs) {
                if (input instanceof IAEItemStack) {
                    ItemStack raw = ((IAEItemStack) input).getItemStack();
                    ItemStack stack = GtCircuits.normalizeForRecipe(raw);
                    // An empty programming circuit is a reset marker, not an ingredient.
                    if (stack == null) continue;
                    hasCircuit |= GtCircuits.isCircuit(stack);
                    pattern.items.add(stack);
                    pattern.itemAmounts.add(Math.max(1L, input.getStackSize()));
                } else if (input instanceof IAEFluidStack) {
                    FluidStack stack = ((IAEFluidStack) input).getFluidStack();
                    if (stack == null) continue;
                    pattern.fluids.add(stack.copy());
                    pattern.fluidAmounts.add(Math.max(1L, input.getStackSize()));
                }
            }
            if (!hasCircuit && circuit != null) {
                ItemStack selector = circuit.copy();
                selector.stackSize = 1;
                pattern.items.add(selector);
                pattern.itemAmounts.add(1L);
            }
            for (IAEStack<?> output : outputs) {
                if (output instanceof IAEItemStack) {
                    ItemStack stack = ((IAEItemStack) output).getItemStack();
                    if (stack == null) continue;
                    pattern.outputs.add(stack);
                    pattern.outputAmounts.add(Math.max(1L, output.getStackSize()));
                } else if (output instanceof IAEFluidStack) {
                    FluidStack stack = ((IAEFluidStack) output).getFluidStack();
                    if (stack == null) continue;
                    pattern.fluidOutputs.add(stack.copy());
                    pattern.fluidOutputAmounts.add(Math.max(1L, output.getStackSize()));
                }
            }
            return pattern;
        }

        ItemStack[] queryItems() {
            ItemStack[] result = new ItemStack[items.size()];
            for (int index = 0; index < result.length; index++) {
                result[index] = items.get(index)
                    .copy();
                result[index].stackSize = (int) Math.min(Integer.MAX_VALUE, itemAmounts.get(index));
            }
            return result;
        }

        FluidStack[] queryFluids() {
            FluidStack[] result = new FluidStack[fluids.size()];
            for (int index = 0; index < result.length; index++) {
                result[index] = fluids.get(index)
                    .copy();
                result[index].amount = (int) Math.min(Integer.MAX_VALUE, fluidAmounts.get(index));
            }
            return result;
        }
    }
}
