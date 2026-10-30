package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.common.items.ItemIntegratedCircuit;

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
        Pattern base = Pattern.of(details);
        return lookup(map, base == null ? null : base.withSelector(circuit));
    }

    private static Verdict lookup(RecipeMap<?> map, Pattern pattern) {
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

    /**
     * Memoized verdicts for one evaluation pass. A verdict is a pure function of (recipe map, decoded pattern, selector
     * circuit) and recipe maps are fixed after load, so it is shared by every target of the pass that exposes the same
     * map and circuit. Machine state (which map, which circuit) is never cached here: callers read it from the live
     * host. Entries live at most for the server tick they were computed in, are dropped on every discovery, and are
     * bounded in count; without a running server nothing is cached. Server thread only.
     */
    static final class Cache {

        private static final int MAX_PATTERNS = 32;
        private static final int MAX_VERDICTS = 4096;
        private static final Object NO_CIRCUIT = new Object();

        private final Map<ICraftingPatternDetails, PatternEntry> patterns = new IdentityHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int verdicts;

        void clear() {
            patterns.clear();
            verdicts = 0;
            tick = Long.MIN_VALUE;
        }

        Verdict verify(RecipeMap<?> map, ICraftingPatternDetails details, ItemStack circuit) {
            Object key = circuitKey(circuit);
            PatternEntry entry = key == null || map == null ? null : entry(details);
            if (entry == null) return GtRecipeVerifier.verify(map, details, circuit);
            Map<Object, Verdict> known = entry.verdicts(map);
            Verdict verdict = known.get(key);
            if (verdict == null) {
                verdict = lookup(map, entry.pattern == null ? null : entry.pattern.withSelector(circuit));
                known.put(key, verdict);
                verdicts++;
            }
            return verdict;
        }

        /** Whether some integrated circuit 0..MAX makes the pattern a recipe of {@code map}. */
        boolean matchesWithAnyCircuit(RecipeMap<?> map, ICraftingPatternDetails details) {
            PatternEntry entry = map == null ? null : entry(details);
            Boolean known = entry == null ? null : entry.anyCircuit.get(map);
            if (known != null) return known;
            boolean found = false;
            for (int number = 0; number <= ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER && !found; number++) {
                ItemStack circuit = GTUtility.getIntegratedCircuit(number);
                found = circuit != null && verify(map, details, circuit) != Verdict.NONE;
            }
            // Re-read: a bound overflow inside verify may have started a fresh pass.
            entry = map == null ? null : entry(details);
            if (entry != null) entry.anyCircuit.put(map, found);
            return found;
        }

        private PatternEntry entry(ICraftingPatternDetails details) {
            if (details == null) return null;
            long now = currentTick();
            if (now == Long.MIN_VALUE) {
                clear();
                return null;
            }
            if (now != tick || verdicts >= MAX_VERDICTS
                || patterns.size() >= MAX_PATTERNS && !patterns.containsKey(details)) {
                clear();
                tick = now;
            }
            PatternEntry entry = patterns.get(details);
            if (entry == null) {
                entry = new PatternEntry(Pattern.of(details));
                patterns.put(details, entry);
            }
            return entry;
        }

        private static long currentTick() {
            MinecraftServer server = MinecraftServer.getServer();
            return server == null ? Long.MIN_VALUE : server.getTickCounter();
        }

        /**
         * A selector as {@link Pattern#withSelector} uses it: absent, or an untagged stack by item and damage (the
         * selector is always copied to size 1). Tagged stacks return null and are verified uncached.
         */
        private static Object circuitKey(ItemStack circuit) {
            if (circuit == null) return NO_CIRCUIT;
            if (circuit.getItem() == null || circuit.hasTagCompound()) return null;
            return new CircuitKey(circuit.getItem(), circuit.getItemDamage());
        }
    }

    private static final class PatternEntry {

        /** Null when the details decode to no processing pattern; every verdict is then NONE. */
        final Pattern pattern;
        final Map<RecipeMap<?>, Map<Object, Verdict>> verdicts = new IdentityHashMap<>();
        final Map<RecipeMap<?>, Boolean> anyCircuit = new IdentityHashMap<>();

        PatternEntry(Pattern pattern) {
            this.pattern = pattern;
        }

        Map<Object, Verdict> verdicts(RecipeMap<?> map) {
            Map<Object, Verdict> known = verdicts.get(map);
            if (known == null) {
                known = new HashMap<>();
                verdicts.put(map, known);
            }
            return known;
        }
    }

    private static final class CircuitKey {

        final Item item;
        final int damage;

        CircuitKey(Item item, int damage) {
            this.item = item;
            this.damage = damage;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CircuitKey && ((CircuitKey) other).item == item
                && ((CircuitKey) other).damage == damage;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(item) * 31 + damage;
        }
    }

    /** Decoded pattern amounts; built once per pattern and not mutated afterwards. */
    private static final class Pattern {

        final List<ItemStack> items;
        final List<Long> itemAmounts;
        final List<FluidStack> fluids;
        final List<Long> fluidAmounts;
        final List<ItemStack> outputs;
        final List<Long> outputAmounts;
        final List<FluidStack> fluidOutputs;
        final List<Long> fluidOutputAmounts;
        boolean hasCircuit;

        private Pattern() {
            items = new ArrayList<>();
            itemAmounts = new ArrayList<>();
            fluids = new ArrayList<>();
            fluidAmounts = new ArrayList<>();
            outputs = new ArrayList<>();
            outputAmounts = new ArrayList<>();
            fluidOutputs = new ArrayList<>();
            fluidOutputAmounts = new ArrayList<>();
        }

        /** Same pattern plus one selector; everything but the item inputs is shared read-only with {@code base}. */
        private Pattern(Pattern base, ItemStack selector) {
            items = new ArrayList<>(base.items);
            itemAmounts = new ArrayList<>(base.itemAmounts);
            items.add(selector);
            itemAmounts.add(1L);
            fluids = base.fluids;
            fluidAmounts = base.fluidAmounts;
            outputs = base.outputs;
            outputAmounts = base.outputAmounts;
            fluidOutputs = base.fluidOutputs;
            fluidOutputAmounts = base.fluidOutputAmounts;
            hasCircuit = base.hasCircuit;
        }

        /** The pattern as verified with {@code circuit} supplied by the machine or NEI when it encodes none itself. */
        Pattern withSelector(ItemStack circuit) {
            if (hasCircuit || circuit == null) return this;
            ItemStack selector = circuit.copy();
            selector.stackSize = 1;
            return new Pattern(this, selector);
        }

        static Pattern of(ICraftingPatternDetails details) {
            if (details == null || details.isCraftable()) return null;
            Pattern pattern = new Pattern();
            IAEStack<?>[] inputs = details.getCondensedAEInputs();
            IAEStack<?>[] outputs = details.getCondensedAEOutputs();
            if (inputs == null || outputs == null) return null;
            for (IAEStack<?> input : inputs) {
                if (input instanceof IAEItemStack) {
                    ItemStack raw = ((IAEItemStack) input).getItemStack();
                    ItemStack stack = GtCircuits.normalizeForRecipe(raw);
                    // An empty programming circuit is a reset marker, not an ingredient.
                    if (stack == null) continue;
                    pattern.hasCircuit |= GtCircuits.isCircuit(stack);
                    pattern.items.add(stack);
                    pattern.itemAmounts.add(Math.max(1L, input.getStackSize()));
                } else if (input instanceof IAEFluidStack) {
                    FluidStack stack = ((IAEFluidStack) input).getFluidStack();
                    if (stack == null) continue;
                    pattern.fluids.add(stack.copy());
                    pattern.fluidAmounts.add(Math.max(1L, input.getStackSize()));
                }
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
