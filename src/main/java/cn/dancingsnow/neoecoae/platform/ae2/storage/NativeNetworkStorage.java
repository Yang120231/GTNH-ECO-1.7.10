package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Both native monitors belong to the same actual grid; no secondary inventory or ledger. */
public final class NativeNetworkStorage implements MEStorage {

    private final IStorageGrid grid;
    private final ChannelSnapshot items = new ChannelSnapshot();
    private final ChannelSnapshot fluids = new ChannelSnapshot();
    private KeyCounter snapshot;

    public NativeNetworkStorage(IStorageGrid grid) {
        this.grid = grid;
    }

    private MEStorage channel(AEKey key) {
        return new NativeInventoryStorage(
            key.getType() == cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType.items() ? grid.getItemInventory()
                : grid.getFluidInventory());
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return key == null ? 0 : channel(key).insert(key, amount, mode, source);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return key == null ? 0 : channel(key).extract(key, amount, mode, source);
    }

    @Override
    public long getAvailableAmount(AEKey key) {
        return key == null ? 0 : channel(key).getAvailableAmount(key);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter.isActive()
            || !cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) {
            new NativeInventoryStorage(grid.getItemInventory()).getAvailableStacks(out);
            new NativeInventoryStorage(grid.getFluidInventory()).getAvailableStacks(out);
            return;
        }
        for (var entry : getAvailableStacks()) cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator
            .add(out, entry.getKey(), entry.getLongValue());
    }

    @Override
    public KeyCounter getAvailableStacks() {
        if (cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter.isActive()
            || !cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) {
            KeyCounter result = new KeyCounter();
            getAvailableStacks(result);
            return result;
        }
        boolean changed = items.update(grid.getItemInventory());
        changed |= fluids.update(grid.getFluidInventory());
        if (snapshot == null || changed) {
            KeyCounter next = items.amounts.copy();
            next.addAll(fluids.amounts);
            snapshot = next.freeze();
        }
        return snapshot;
    }

    /** Same revision as the bounded snapshot; only wide or unbounded entries need this side channel. */
    public java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> exactAmounts() {
        getAvailableStacks();
        // Exact-only changes can leave a saturated native projection unchanged. Re-read just those keys
        // at the planning boundary instead of assuming every third-party source posts visible deltas.
        items.refreshExact();
        fluids.refreshExact();
        java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> result = new java.util.HashMap<>(
            items.exact);
        result.putAll(fluids.exact);
        return java.util.Collections.unmodifiableMap(result);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final class ChannelSnapshot {

        private appeng.api.storage.IMEMonitor monitor;
        private long version = Long.MIN_VALUE;
        private KeyCounter amounts = new KeyCounter();
        private final java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> exact = new java.util.HashMap<>();

        boolean update(appeng.api.storage.IMEMonitor current) {
            // getStorageList also processes full invalidations and periodic repair of third-party listings.
            appeng.api.storage.data.IItemList listed = current.getStorageList();
            NativeNetworkReads.Listing revisions = current instanceof NativeNetworkReads.Listing
                ? (NativeNetworkReads.Listing) current
                : null;
            long nextVersion = revisions == null ? cn.dancingsnow.neoecoae.platform.network.ServerThread.tick()
                : revisions.neoecoae$listingVersion();
            if (monitor == current && revisions != null && version == nextVersion) return false;
            java.util.Set<AEKey> changed = new java.util.HashSet<>();
            boolean delta = monitor == current && revisions != null
                && revisions.neoecoae$listingChangesSince(version, changed);
            if (!delta) {
                amounts = new KeyCounter();
                exact.clear();
                for (Object value : listed) {
                    appeng.api.storage.data.IAEStack stack = (appeng.api.storage.data.IAEStack) value;
                    AEKey key = AEKey.fromNative(stack);
                    if (key != null && stack.getStackSize() > 0) {
                        amounts.set(key, stack.getStackSize());
                        changed.add(key);
                    }
                }
            } else {
                for (AEKey key : changed) {
                    appeng.api.storage.data.IAEStack stack = listed.findPrecise(key.toNative(1));
                    amounts.set(key, stack == null ? 0 : Math.max(0L, stack.getStackSize()));
                    exact.remove(key);
                }
            }
            monitor = current;
            version = nextVersion;
            return true;
        }

        void refreshExact() {
            exact.clear();
            java.util.Set<AEKey> wide = new java.util.HashSet<>();
            for (var entry : amounts) if (entry.getLongValue() == Long.MAX_VALUE) wide.add(entry.getKey());
            if (!wide.isEmpty()) {
                // Keep AE2's real nested filters and subnet deduplication. Only overflow keys need an exact pass.
                NativeStackAmounts.beginExact();
                try {
                    appeng.api.storage.data.IItemList precise = monitor.getAvailableItems(
                        monitor.getChannel()
                            .createList(),
                        appeng.util.IterationCounter.fetchNewId(),
                        java.util.Optional.of(
                            (java.util.function.Predicate<appeng.api.storage.data.IAEStack>) stack -> wide
                                .contains(AEKey.fromNative(stack))));
                    for (Object value : precise) {
                        appeng.api.storage.data.IAEStack stack = (appeng.api.storage.data.IAEStack) value;
                        AEKey key = AEKey.fromNative(stack);
                        if (key != null && wide.contains(key)) exact.put(key, NativeStackAmounts.exact(stack));
                    }
                } finally {
                    NativeStackAmounts.endExact();
                }
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("ME network");
    }
}
