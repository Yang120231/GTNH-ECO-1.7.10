package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/**
 * Server-thread memo across recipe view rebuilds of one world. Candidates follow AE2 rv3's own substitution lookup
 * ({@code ItemList.findFuzzy} with {@code IGNORE_ALL}): keys sharing an ore dictionary entry with the encoded input,
 * or, for an input without one, the same item (any damage if damageable, any NBT). Keys are bucketed by those
 * identities, so an input only ever matches its own few candidates against the native recipe, each exactly once.
 */
final class NativeSubstitutionIndex implements NativePatternDetails.Substitutes {

    /** Native recipe check for one candidate; replaceable so tests need no AE2 item stacks. */
    interface Validator {

        boolean accepts(ICraftingPatternDetails pattern, GenericStack[] sparse, AEKey primary, AEKey candidate);
    }

    private static final int MIN_RESET_SIZE = 4096;
    private final World world;
    private final Validator validator;
    private final Function<ItemStack, int[]> oreIds;
    private final Set<AEKey> known = new HashSet<>();
    private final Map<Integer, List<AEKey>> byOre = new HashMap<>();
    private final Map<Map.Entry<Item, Integer>, List<AEKey>> byItemDamage = new HashMap<>();
    private final Map<Item, List<AEKey>> byItem = new IdentityHashMap<>();
    private final Map<ICraftingPatternDetails, List<Input>> inputs = new IdentityHashMap<>();
    private final Map<ICraftingPatternDetails, NativePatternDetails> details = new IdentityHashMap<>();
    private Set<AEKey> available = Collections.emptySet();

    NativeSubstitutionIndex(World world) {
        this(
            world,
            (pattern, sparse, primary, candidate) -> NativePatternDetails
                .acceptsSubstitute(pattern, world, sparse, primary, candidate),
            net.minecraftforge.oredict.OreDictionary::getOreIDs);
    }

    NativeSubstitutionIndex(World world, Validator validator, Function<ItemStack, int[]> oreIds) {
        this.world = world;
        this.validator = validator;
        this.oreIds = oreIds;
    }

    void begin(Set<AEKey> available) {
        if (known.size() > Math.max(MIN_RESET_SIZE, available.size() * 4L)) {
            // Long-gone keys dominate; restart rather than keep validating stock that left the network.
            known.clear();
            byOre.clear();
            byItemDamage.clear();
            byItem.clear();
            inputs.clear();
        }
        for (AEKey key : available) if (known.add(key) && key instanceof AEItemKey) {
            ItemStack stack = ((AEItemKey) key).getReadOnlyStack();
            for (int ore : oreIds.apply(stack)) byOre.computeIfAbsent(ore, ignored -> new ArrayList<>())
                .add(key);
            byItemDamage.computeIfAbsent(itemDamage(stack), ignored -> new ArrayList<>())
                .add(key);
            byItem.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>())
                .add(key);
        }
        this.available = available;
    }

    /** Patterns without substitution never depend on stock, so their frozen details are reused as-is. */
    NativePatternDetails details(ICraftingPatternDetails pattern) {
        NativePatternDetails previous = details.get(pattern);
        if (previous != null && !(pattern.isCraftable() && pattern.canSubstitute())) return previous;
        NativePatternDetails captured = NativePatternDetails.capture(pattern, world, this);
        details.put(pattern, captured);
        return captured;
    }

    void retain(Set<ICraftingPatternDetails> live) {
        inputs.keySet()
            .retainAll(live);
        details.keySet()
            .retainAll(live);
    }

    @Override
    public Iterable<AEKey> accepted(ICraftingPatternDetails pattern, int index, AEKey primary, GenericStack[] sparse) {
        if (!(primary instanceof AEItemKey)) return Collections.emptyList();
        List<Input> slots = inputs.computeIfAbsent(pattern, ignored -> new ArrayList<>());
        while (slots.size() <= index) slots.add(null);
        Input input = slots.get(index);
        if (input == null || !input.primary.equals(primary))
            slots.set(index, input = new Input(primary, candidateBuckets(((AEItemKey) primary).getReadOnlyStack())));
        for (int bucket = 0; bucket < input.buckets.size(); bucket++) {
            List<AEKey> keys = input.buckets.get(bucket);
            // Buckets only grow, so each input visits every entry once across all rebuilds.
            for (int size = keys.size(); input.cursors[bucket] < size; input.cursors[bucket]++) {
                AEKey candidate = keys.get(input.cursors[bucket]);
                if (candidate.equals(primary) || !input.tested.add(candidate)) continue;
                if (validator.accepts(pattern, sparse, primary, candidate)) input.valid.add(candidate);
            }
        }
        List<AEKey> result = new ArrayList<>();
        for (AEKey candidate : input.valid) if (available.contains(candidate)) result.add(candidate);
        return result;
    }

    private List<List<AEKey>> candidateBuckets(ItemStack primary) {
        List<List<AEKey>> buckets = new ArrayList<>();
        int[] ores = oreIds.apply(primary);
        if (ores.length > 0) {
            for (int ore : ores) buckets.add(byOre.computeIfAbsent(ore, ignored -> new ArrayList<>()));
        } else if (primary.getItem()
            .isDamageable()) {
                buckets.add(byItem.computeIfAbsent(primary.getItem(), ignored -> new ArrayList<>()));
            } else buckets.add(byItemDamage.computeIfAbsent(itemDamage(primary), ignored -> new ArrayList<>()));
        return buckets;
    }

    private static Map.Entry<Item, Integer> itemDamage(ItemStack stack) {
        return new AbstractMap.SimpleImmutableEntry<>(stack.getItem(), stack.getItemDamage());
    }

    private static final class Input {

        final AEKey primary;
        final List<List<AEKey>> buckets;
        final int[] cursors;
        final Set<AEKey> tested = new HashSet<>();
        final List<AEKey> valid = new ArrayList<>();

        Input(AEKey primary, List<List<AEKey>> buckets) {
            this.primary = primary;
            this.buckets = buckets;
            cursors = new int[buckets.size()];
        }
    }
}
