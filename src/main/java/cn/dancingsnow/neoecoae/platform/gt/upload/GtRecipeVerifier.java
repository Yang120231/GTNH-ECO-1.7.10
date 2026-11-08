package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cpw.mods.fml.common.Loader;
import gregtech.api.enums.CondensateType;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.BlastFurnaceGasStat;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import tectech.recipe.TecTechRecipeMaps;

/** Infers a source-free pattern's route, not its execution quantities or the machine's current inventory. */
final class GtRecipeVerifier {

    private static final int MAX_RECIPES = 100_000;
    private static boolean coolingLookedUp;
    private static Method cooling;

    private GtRecipeVerifier() {}

    static final class Inference {

        final Map<String, ItemStack> selections = new LinkedHashMap<>();
        boolean complete = true;

        boolean found() {
            return !selections.isEmpty();
        }

        boolean unique() {
            return complete && selections.size() == 1;
        }

        ItemStack circuit() {
            return unique() ? selections.values().iterator().next() : null;
        }

        boolean allows(ItemStack circuit) {
            return complete && selections.containsKey(GtCircuits.number(circuit));
        }
    }

    private static Inference lookup(RecipeMap<?> map, Pattern pattern) {
        Inference result = new Inference();
        if (map == null || pattern == null || !pattern.hasEvidence()) return result;
        int inspected = 0;
        try {
            // Inspect the entire map. A short first-match query loses gas/circuit variants and cannot prove uniqueness.
            for (GTRecipe recipe : map.getAllRecipes()) {
                if (++inspected > MAX_RECIPES) {
                    result.complete = false;
                    break;
                }
                if (recipe == null || !recipe.mEnabled || recipe.mFakeRecipe || !outputKindsPresent(recipe, pattern))
                    continue;
                ItemStack circuit = recipeCircuit(recipe);
                if (pattern.reset && circuit != null) continue;
                if (pattern.circuit != null && circuit != null && !GtCircuits.same(pattern.circuit, circuit)) continue;
                if (!inputKindsPresent(map, recipe, pattern)) continue;
                ItemStack selected = pattern.circuit != null ? pattern.circuit : circuit;
                result.selections.put(GtCircuits.number(selected), selected);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Skipping an unreadable candidate could conceal another route. Never turn a partial search into proof.
            result.complete = false;
        }
        return result;
    }

    private static ItemStack recipeCircuit(GTRecipe recipe) {
        ItemStack selected = null;
        if (recipe.mInputs != null) for (ItemStack input : recipe.mInputs) {
            ItemStack circuit = GtCircuits.unwrap(input);
            if (circuit == null) continue;
            if (selected != null && !GtCircuits.same(selected, circuit))
                throw new IllegalArgumentException("Recipe has multiple selection circuits");
            selected = circuit;
        }
        return selected;
    }

    private static boolean inputKindsPresent(RecipeMap<?> map, GTRecipe recipe, Pattern pattern) {
        List<ItemStack> items = new ArrayList<>(pattern.items);
        List<FluidStack> fluids = new ArrayList<>();
        GTRecipe.RecipeItemInput[] recipeItems = recipe.getCachedCombinedItemInputs();
        for (ItemStack encoded : pattern.items) {
            if (GtCircuits.isCircuit(encoded)) continue;
            var data = GTOreDictUnificator.getItemData(encoded);
            boolean known = false;
            for (GTRecipe.RecipeItemInput input : recipeItems) if (input.matchesRecipe(data, encoded)) {
                known = true;
                break;
            }
            if (!known) return false;
        }
        // NEE omits zero-count tools and selectors. Supplement only the recognition view, never the AE pattern.
        if (recipe.mInputs != null) for (ItemStack input : recipe.mInputs) if (input != null && input.stackSize <= 0) {
            ItemStack tool = input.copy();
            tool.stackSize = 1;
            items.add(tool);
        }
        boolean bec = map == TecTechRecipeMaps.condensateAssemblingRecipes;
        FluidStack[] condensates = bec ? recipe.getMetadata(GTRecipeConstants.CONDENSATE_INPUT) : null;
        for (FluidStack encoded : pattern.fluids) {
            if (bec && condensateInput(condensates, encoded)) continue;
            if (!fluidInput(recipe, encoded)) return false;
            fluids.add(encoded.copy());
        }
        // A gasless EBF pattern can describe a supplied-from-subnet gas variant. Keep those variants in the search
        // instead of "proving" the dry circuit from the absence of gas. An explicitly supplied gas remains evidence.
        if (map == RecipeMaps.blastFurnaceRecipes && !pattern.hasProtectiveGas()
            && recipe.mFluidInputs != null) for (FluidStack input : recipe.mFluidInputs)
                if (protectiveGas(input)) fluids.add(input.copy());
        return recipe.isRecipeInputEqual(false, true, fluids.toArray(new FluidStack[0]), items.toArray(new ItemStack[0]));
    }

    private static boolean fluidInput(GTRecipe recipe, FluidStack encoded) {
        if (recipe.mFluidInputs == null) return false;
        for (int slot = 0; slot < recipe.mFluidInputs.length; slot++) {
            if (GTUtility.areFluidsEqual(encoded, recipe.mFluidInputs[slot], true)) return true;
            if (recipe.mAltFluidInputs == null || slot >= recipe.mAltFluidInputs.length
                || recipe.mAltFluidInputs[slot] == null) continue;
            for (FluidStack alternative : recipe.mAltFluidInputs[slot])
                if (GTUtility.areFluidsEqual(encoded, alternative, true)) return true;
        }
        return false;
    }

    private static boolean condensateInput(FluidStack[] required, FluidStack encoded) {
        if (required == null || encoded == null) return false;
        CondensateType type = CondensateType.getCondensateType(encoded.getFluid());
        if (type == null) type = CondensateType.getCondensateTypeBySource(encoded.getFluid());
        for (FluidStack input : required) if (input != null && (GTUtility.areFluidsEqual(encoded, input, true)
            || type != null && type == CondensateType.getCondensateType(input.getFluid()))) return true;
        return false;
    }

    private static boolean protectiveGas(FluidStack fluid) {
        if (fluid == null) return false;
        for (BlastFurnaceGasStat gas : BlastFurnaceGasStat.BlastFurnaceGasStats)
            if (GTUtility.areFluidsEqual(fluid, gas.gas, true)) return true;
        return false;
    }

    private static boolean outputKindsPresent(GTRecipe recipe, Pattern pattern) {
        // Omitted byproducts, probability and edited quantities do not change the upload route.
        for (ItemStack encoded : pattern.outputs) {
            boolean found = false;
            if (recipe.mOutputs != null) for (ItemStack output : recipe.mOutputs) if (output != null
                && (GTUtility.areStacksEqual(encoded, output, true)
                    || GTUtility.areStacksEqual(encoded, cooled(output), true))) {
                found = true;
                break;
            }
            if (!found) return false;
        }
        for (FluidStack encoded : pattern.fluidOutputs) {
            boolean found = false;
            if (recipe.mFluidOutputs != null) for (FluidStack output : recipe.mFluidOutputs)
                if (GTUtility.areFluidsEqual(encoded, output, true)) {
                    found = true;
                    break;
                }
            if (!found) return false;
        }
        return true;
    }

    /** Reuse the installed GTNL conversion; do not maintain a second OreDictionary cooling implementation. */
    private static ItemStack cooled(ItemStack output) {
        if (!coolingLookedUp) {
            coolingLookedUp = true;
            if (Loader.isModLoaded("sciencenotleisure")) try {
                cooling = Class.forName("com.science.gtnl.utils.item.ItemUtils")
                    .getMethod("coolHotIngot", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                cooling = null;
            }
        }
        if (cooling != null) try {
            Object result = cooling.invoke(null, output.copy());
            if (result instanceof ItemStack) return (ItemStack) result;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Original output is still valid. */ }
        return output;
    }

    /** Per-pass, per-tick inference shared by all receivers of a map; no target circuit enters the cache key. */
    static final class Cache {

        private static final int MAX_PATTERNS = 32, MAX_VERDICTS = 4096;
        private final Map<PatternContext, PatternEntry> patterns = new IdentityHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int verdicts;

        void clear() {
            patterns.clear();
            verdicts = 0;
            tick = Long.MIN_VALUE;
        }

        Inference infer(RecipeMap<?> map, PatternContext context) {
            PatternEntry entry = entry(context);
            if (entry == null) return lookup(map, Pattern.of(context));
            Inference known = entry.verdicts.get(map);
            if (known == null) {
                known = lookup(map, entry.pattern);
                entry.verdicts.put(map, known);
                verdicts++;
            }
            return known;
        }

        private PatternEntry entry(PatternContext context) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || context == null) {
                clear();
                return null;
            }
            long now = server.getTickCounter();
            if (now != tick || verdicts >= MAX_VERDICTS
                || patterns.size() >= MAX_PATTERNS && !patterns.containsKey(context)) {
                clear();
                tick = now;
            }
            return patterns.computeIfAbsent(context, ignored -> new PatternEntry(Pattern.of(context)));
        }
    }

    private static final class PatternEntry {

        final Pattern pattern;
        final Map<RecipeMap<?>, Inference> verdicts = new IdentityHashMap<>();

        PatternEntry(Pattern pattern) {
            this.pattern = pattern;
        }
    }

    private static final class Pattern {

        final List<ItemStack> items = new ArrayList<>(), outputs = new ArrayList<>();
        final List<FluidStack> fluids = new ArrayList<>(), fluidOutputs = new ArrayList<>();
        ItemStack circuit;
        boolean reset;

        static Pattern of(PatternContext context) {
            if (context == null || !context.processing || context.inputError != null) return null;
            Pattern pattern = new Pattern();
            IAEStack<?>[] inputs = context.recipeInputs, outputs = context.details.getCondensedAEOutputs();
            if (inputs == null || outputs == null) return null;
            pattern.circuit = GtCircuits.patternCircuit(inputs);
            pattern.reset = GtCircuits.resetsSelector(inputs);
            for (IAEStack<?> input : inputs) {
                if (input instanceof IAEItemStack) {
                    ItemStack stack = GtCircuits.normalizeForRecipe(((IAEItemStack) input).getItemStack());
                    if (stack == null) continue;
                    stack.stackSize = 1;
                    pattern.items.add(stack);
                } else if (input instanceof IAEFluidStack) {
                    FluidStack stack = ((IAEFluidStack) input).getFluidStack();
                    if (stack == null) continue;
                    stack = stack.copy();
                    stack.amount = 1;
                    pattern.fluids.add(stack);
                }
            }
            for (IAEStack<?> output : outputs) {
                if (output instanceof IAEItemStack) {
                    ItemStack stack = ((IAEItemStack) output).getItemStack();
                    if (stack != null) pattern.outputs.add(stack.copy());
                } else if (output instanceof IAEFluidStack) {
                    FluidStack stack = ((IAEFluidStack) output).getFluidStack();
                    if (stack != null) pattern.fluidOutputs.add(stack.copy());
                }
            }
            return pattern;
        }

        boolean hasEvidence() {
            if (outputs.isEmpty() && fluidOutputs.isEmpty()) return false;
            if (!fluids.isEmpty()) return true;
            for (ItemStack item : items) if (!GtCircuits.isCircuit(item)) return true;
            return false;
        }

        boolean hasProtectiveGas() {
            for (FluidStack fluid : fluids) if (protectiveGas(fluid)) return true;
            return false;
        }
    }
}
