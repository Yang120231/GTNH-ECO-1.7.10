package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import cn.dancingsnow.neoecoae.impl.storage.StorageListingChanges;
import cn.dancingsnow.neoecoae.impl.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Native monitors belong to the same actual grid; no secondary inventory or ledger. */
public final class NativeNetworkStorage implements MEStorage, StorageListingDelta {

    private final IStorageGrid grid;
    private final java.util.Map<IAEStackType<?>, ChannelSnapshot> channels = new java.util.IdentityHashMap<>();
    private KeyCounter snapshot;
    private final StorageListingChanges changes = new StorageListingChanges();

    public NativeNetworkStorage(IStorageGrid grid) {
        this.grid = grid;
    }

    private void refreshChannels() {
        if (StorageBusTransferFilter.isActive()) {
            StorageBusTransferFilter.withoutExclusion(this::refreshChannels);
            return;
        }
        boolean changed = false;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IMEMonitor<?> monitor = grid.getMEMonitor(type);
            if (monitor == null) {
                if (channels.remove(type) != null) {
                    changes.invalidate();
                    changed = true;
                }
            } else {
                changed |= channels.computeIfAbsent(type, ignored -> new ChannelSnapshot())
                    .update(monitor, changes);
            }
        }
        if (changed) snapshot = null;
    }

    @Override
    public long neoecoae$listingVersion() {
        if (!cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) return UNKNOWN;
        refreshChannels();
        return changes.version();
    }

    @Override
    public boolean neoecoae$visitListingChanges(long since, long current,
        java.util.function.ObjLongConsumer<AEKey> visitor) {
        if (neoecoae$listingVersion() != current || current == UNKNOWN) return false;
        return changes.visitSince(since, current, key -> {
            ChannelSnapshot channel = channels.get(
                key.getType()
                    .nativeType());
            visitor.accept(
                key,
                StorageBusTransferFilter.isActive() ? getAvailableAmount(key)
                    : channel == null ? 0 : channel.amounts.get(key));
        });
    }

    private MEStorage channel(AEKey key) {
        if (key == null || key.getType()
            .nativeType() == null) return null;
        IMEMonitor<?> monitor = grid.getMEMonitor(
            key.getType()
                .nativeType());
        return monitor == null ? null : new NativeInventoryStorage(monitor);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        MEStorage inventory = channel(key);
        return inventory == null ? 0 : inventory.insert(key, amount, mode, source);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        MEStorage inventory = channel(key);
        return inventory == null ? 0 : inventory.extract(key, amount, mode, source);
    }

    @Override
    public long getAvailableAmount(AEKey key) {
        MEStorage inventory = channel(key);
        return inventory == null ? 0 : inventory.getAvailableAmount(key);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (StorageBusTransferFilter.isActive() || !cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) {
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                IMEMonitor<?> monitor = grid.getMEMonitor(type);
                if (monitor != null) new NativeInventoryStorage(monitor).getAvailableStacks(out);
            }
            return;
        }
        for (var entry : getAvailableStacks()) cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator
            .add(out, entry.getKey(), entry.getLongValue());
    }

    @Override
    public KeyCounter getAvailableStacks() {
        if (StorageBusTransferFilter.isActive() || !cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) {
            KeyCounter result = new KeyCounter();
            getAvailableStacks(result);
            return result;
        }
        refreshChannels();
        if (snapshot == null) {
            KeyCounter next = new KeyCounter();
            for (ChannelSnapshot channel : channels.values()) next.addAll(channel.amounts);
            snapshot = next.freeze();
        }
        return snapshot;
    }

    /** Same revision as the bounded snapshot; only wide or unbounded entries need this side channel. */
    public java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> exactAmounts() {
        getAvailableStacks();
        // Exact-only changes can leave a saturated native projection unchanged. Re-read just those keys
        // at the planning boundary instead of assuming every third-party source posts visible deltas.
        java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> result = new java.util.HashMap<>();
        for (ChannelSnapshot channel : channels.values()) {
            channel.refreshExact();
            result.putAll(channel.exact);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final class ChannelSnapshot {

        private appeng.api.storage.IMEMonitor monitor;
        private long version = Long.MIN_VALUE;
        private KeyCounter amounts = new KeyCounter();
        private final java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> exact = new java.util.HashMap<>();

        boolean update(appeng.api.storage.IMEMonitor current, StorageListingChanges changes) {
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
                changes.invalidate();
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
                    changes.record(key);
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
                        monitor.getStackType()
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
