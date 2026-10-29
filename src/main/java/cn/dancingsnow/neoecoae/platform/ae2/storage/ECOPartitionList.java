package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.FluidList;
import appeng.util.item.ItemList;
import appeng.util.prioitylist.FuzzyPriorityList;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/**
 * Cell partition filter. Exact matches use the key set; fuzzy cards delegate to AE2's own
 * {@link FuzzyPriorityList}, so ECO cells match the same stacks an AE2 cell would.
 */
public final class ECOPartitionList {

    private final Set<AEKey> keys;
    private final FuzzyMode fuzzy;
    private final FuzzyPriorityList<IAEItemStack> fuzzyItems;
    private final FuzzyPriorityList<IAEFluidStack> fuzzyFluids;
    /** Keys of channels AE2 has no typed list for; compared one by one as before. */
    private final List<AEKey> otherKeys = new ArrayList<>();

    private ECOPartitionList(Set<AEKey> keys, FuzzyMode fuzzy) {
        this.keys = Collections.unmodifiableSet(new HashSet<>(keys));
        this.fuzzy = fuzzy;
        if (fuzzy == null) {
            fuzzyItems = null;
            fuzzyFluids = null;
            return;
        }
        ItemList items = new ItemList();
        FluidList fluids = new FluidList();
        for (AEKey key : this.keys) {
            IAEStack<?> stack = key.toNative(1);
            if (stack instanceof IAEItemStack) items.add((IAEItemStack) stack);
            else if (stack instanceof IAEFluidStack) fluids.add((IAEFluidStack) stack);
            else otherKeys.add(key);
        }
        fuzzyItems = new FuzzyPriorityList<>(items, fuzzy);
        fuzzyFluids = new FuzzyPriorityList<>(fluids, fuzzy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean matchesFilter(AEKey key, IncludeExclude mode) {
        if (keys.isEmpty()) return true;
        boolean match = keys.contains(key) || fuzzy != null && key != null && fuzzyMatch(key);
        return mode == IncludeExclude.WHITELIST ? match : !match;
    }

    private boolean fuzzyMatch(AEKey key) {
        IAEStack<?> stack = key.toNative(1);
        if (stack instanceof IAEItemStack) return fuzzyItems.isListed((IAEItemStack) stack);
        if (stack instanceof IAEFluidStack) return fuzzyFluids.isListed((IAEFluidStack) stack);
        for (AEKey candidate : otherKeys) if (candidate.getType() == key.getType() && candidate.toNative(1)
            .fuzzyComparison(stack, fuzzy)) return true;
        return false;
    }

    public static final class Builder {

        private final Set<AEKey> keys = new HashSet<>();
        private FuzzyMode fuzzy;

        public Builder fuzzyMode(FuzzyMode mode) {
            fuzzy = mode;
            return this;
        }

        public Builder addAll(Collection<AEKey> values) {
            keys.addAll(values);
            return this;
        }

        public ECOPartitionList build() {
            return new ECOPartitionList(keys, fuzzy);
        }
    }
}
