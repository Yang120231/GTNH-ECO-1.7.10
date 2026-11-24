package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.function.Predicate;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.impl.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageHelper;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

/** AE2 ME IO Port-style full-key transfer with a small round-robin key budget. */
public final class ECOIOPortTransfer {

    private static final int KEYS_PER_TICK = 5;
    private final ObjectLinkedOpenHashSet<AEKey> queue = new ObjectLinkedOpenHashSet<>();
    private MEStorage previousSource;
    private MEStorage previousDestination;
    private long nextOutputTick = Long.MIN_VALUE;
    private long lastOutputTick = Long.MIN_VALUE;
    private boolean transferring;
    private final KeyCounter outputListing = new KeyCounter();
    private MEStorage listedSource;
    private Object listedIdentity;
    private long listedVersion = StorageListingVersion.UNKNOWN;
    private boolean listedBusScope;
    private Predicate<AEKey> previousFilter;
    private NetworkInventoryCache mountListing;
    private final Object2LongOpenHashMap<AEKey> listedAmounts = new Object2LongOpenHashMap<>();
    private GenericStack remainder;
    private final ECOTransferQuarantine uncertainExtractions = new ECOTransferQuarantine();
    private final ECOTransferQuarantine uncertainInsertions = new ECOTransferQuarantine();

    public boolean hasUncertainTransfers() {
        return !uncertainExtractions.isEmpty() || !uncertainInsertions.isEmpty();
    }

    public void saveReceipts(CompoundTag tag, HolderLookup.Provider registries) {
        uncertainExtractions.save(tag, "uncertainInterfaceExtractions", registries);
        uncertainInsertions.save(tag, "uncertainInterfaceInsertions", registries);
    }

    public void loadReceipts(CompoundTag tag, HolderLookup.Provider registries) {
        uncertainExtractions.load(tag, "uncertainInterfaceExtractions", registries);
        uncertainInsertions.load(tag, "uncertainInterfaceInsertions", registries);
    }

    public void settleRemainder(java.util.function.ToLongFunction<GenericStack> insert, Runnable changed) {
        if (remainder == null || transferring) return;
        transferring = true;
        try {
            GenericStack offered = remainder;
            long accepted = uncertainInsertions
                .attempt(offered.what(), offered.amount(), () -> insert.applyAsLong(offered));
            settleInsertion(
                offered.what(),
                offered.amount(),
                ECOTransferQuarantine.unavailable(accepted, offered.amount()),
                changed);
        } finally {
            transferring = false;
        }
    }

    private void settleInsertion(AEKey key, long extracted, long unavailable, Runnable changed) {
        if (unavailable == 0L) return;
        long left = extracted - unavailable;
        remainder = left == 0L ? null : new GenericStack(key, left);
        changed.run();
    }

    public GenericStack remainder() {
        return remainder;
    }

    public void restoreRemainder(GenericStack stack) {
        remainder = stack;
    }

    /** Resets candidate discovery without discarding a previously extracted remainder. */
    public void reset() {
        queue.clear();
        previousSource = listedSource = null;
        listedIdentity = null;
        listedVersion = StorageListingVersion.UNKNOWN;
        previousFilter = null;
        outputListing.clear();
        mountListing = null;
        listedAmounts.clear();
        previousDestination = null;
        nextOutputTick = lastOutputTick = Long.MIN_VALUE;
    }

    public long transfer(GridView grid, MEStorage hostStorage, boolean fillHost, IActionSource source) {
        return transfer(grid, hostStorage, fillHost, false, source);
    }

    public long transfer(GridView grid, MEStorage hostStorage, boolean fillHost, boolean ignoreCreativeInput,
        IActionSource source) {
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, source, key -> true);
    }

    public long transfer(GridView grid, MEStorage hostStorage, boolean fillHost, boolean ignoreCreativeInput,
        IActionSource source, Predicate<AEKey> filter) {
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, true, source, filter);
    }

    public long transfer(GridView grid, MEStorage hostStorage, boolean fillHost, boolean ignoreCreativeInput,
        boolean excludeStorageBuses, IActionSource source, Predicate<AEKey> filter) {
        return transfer(
            grid,
            hostStorage,
            fillHost,
            ignoreCreativeInput,
            excludeStorageBuses,
            source,
            filter,
            Long.MIN_VALUE,
            1,
            Long.MAX_VALUE,
            () -> {});
    }

    /** Output batching retains all resources in the host's existing authoritative storage. */
    public long transfer(GridView grid, MEStorage hostStorage, boolean fillHost, boolean ignoreCreativeInput,
        boolean excludeStorageBuses, IActionSource source, Predicate<AEKey> filter, long tick, int outputIntervalTicks,
        long outputFlushThreshold, Runnable changed) {
        if (transferring || remainder != null) return 0;
        transferring = true;
        try {
            return StorageBusTransferFilter.transfer(
                excludeStorageBuses,
                () -> transferContents(
                    grid,
                    hostStorage,
                    fillHost,
                    ignoreCreativeInput,
                    excludeStorageBuses,
                    source,
                    filter,
                    tick,
                    Math.max(1, Math.min(20, outputIntervalTicks)),
                    Math.max(1, outputFlushThreshold),
                    changed));
        } finally {
            transferring = false;
        }
    }

    private long transferContents(GridView grid, MEStorage hostStorage, boolean fillHost, boolean ignoreCreativeInput,
        boolean excludeStorageBuses, IActionSource source, Predicate<AEKey> filter, long tick, int outputIntervalTicks,
        long outputFlushThreshold, Runnable changed) {
        MEStorage networkStorage = grid.getStorageService()
            .getInventory();
        MEStorage from;
        MEStorage destination;
        KeyCounter available;
        boolean discoverAll = true;
        boolean stableFilter = filter instanceof StorageInterfaceFilter && filter == previousFilter;
        from = fillHost ? networkStorage : hostStorage;
        destination = fillHost ? hostStorage : networkStorage;
        boolean scheduled = !fillHost && tick != Long.MIN_VALUE && outputIntervalTicks > 1;
        if (destination != previousDestination || tick < lastOutputTick) nextOutputTick = Long.MIN_VALUE;
        previousDestination = destination;
        lastOutputTick = tick;
        boolean outputDue = !scheduled || tick >= nextOutputTick;
        if (from != previousSource) {
            previousSource = from;
            nextOutputTick = Long.MIN_VALUE;
            outputDue = true;
            listedSource = null;
            queue.clear();
            stableFilter = false;
            if (mountListing != null) mountListing.invalidate();
        }

        if (from instanceof NetworkInventoryCache.Mounts mounts && refreshMountListing(mounts, stableFilter, filter)) {
            available = null;
            discoverAll = false;
        } else if (fillHost && !(from instanceof StorageListingVersion)) {
            if (excludeStorageBuses) {
                // The shared cache includes bus inventories and must stay unchanged for ordinary network users.
                available = new KeyCounter();
                networkStorage.getAvailableStacks(available);
            } else {
                available = grid.getStorageService()
                    .getCachedInventory();
            }
        } else {
            available = null;
            discoverAll = refreshOutputListing(from, stableFilter, filter);
        }
        if (discoverAll || !stableFilter) {
            int listed = 0;
            Iterable<it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey>> entries = available == null
                ? Object2LongMaps.fastIterable(listedAmounts)
                : available;
            for (var entry : entries) {
                if (entry.getLongValue() > 0L) {
                    listed++;
                    if (filter.test(entry.getKey())) queue.add(entry.getKey());
                }
            }
            // High key churn must not leave an unbounded backlog of vanished candidate IDs.
            if (queue.size() > listed) queue.removeIf(key -> availableAmount(available, key) <= 0);
        }
        previousFilter = filter;

        long moved = 0L;
        boolean attempted = false;
        int keysRemaining = Math.min(KEYS_PER_TICK, queue.size());
        while (keysRemaining-- > 0 && !queue.isEmpty()) {
            var key = queue.first();
            // Also recheck pending keys after a filter or interface change.
            if (!filter.test(key) || uncertainExtractions.contains(key)) {
                queue.removeFirst();
                continue;
            }
            long amount = availableAmount(available, key);
            if (amount <= 0L) {
                queue.removeFirst();
                continue;
            }
            // Keep the queue position while waiting: rotating on skipped ticks can starve some keys.
            if (!outputDue && amount < outputFlushThreshold) break;
            queue.removeFirst();
            attempted = true;

            appeng.api.networking.energy.IEnergySource energy;
            long possible;
            try {
                long simulated = ECOTransferQuarantine
                    .check(destination.insert(key, amount, Actionable.SIMULATE, source), amount);
                energy = grid.getEnergyService();
                possible = StorageHelper.poweredAmount(energy, key, simulated);
            } catch (RuntimeException failure) {
                enqueue(key);
                continue;
            }
            if (possible <= 0) {
                enqueue(key);
                continue;
            }

            var extraction = uncertainExtractions.attemptWithPrefix(
                key,
                possible,
                () -> fillHost && ignoreCreativeInput ? ECOCreativeExtractionFilter.extract(from, key, possible, source)
                    : from.extract(key, possible, Actionable.MODULATE, source));
            long extracted = extraction.confirmed();
            if (extraction.uncertain() > 0L && extracted == 0L) {
                changed.run();
                break;
            }
            if (extracted == 0L) {
                // Listing versions describe quantities, not whether an extraction is temporarily permitted.
                enqueue(key);
                continue;
            }

            remainder = new GenericStack(key, extracted);
            changed.run();
            enqueue(key);
            long offered;
            try {
                offered = StorageHelper.poweredAmount(energy, key, extracted);
            } catch (RuntimeException failure) {
                break;
            }
            if (offered == 0L) break;
            var insertion = uncertainInsertions
                .attemptWithPrefix(key, offered, () -> destination.insert(key, offered, Actionable.MODULATE, source));
            long inserted = insertion.confirmed();
            settleInsertion(key, extracted, inserted + insertion.uncertain(), changed);
            if (inserted > 0L) {
                moved = inserted > Long.MAX_VALUE - moved ? Long.MAX_VALUE : moved + inserted;
                try {
                    StorageHelper.chargeTransfer(energy, key, inserted);
                } catch (RuntimeException failure) {
                    break;
                }
            }
            if (extraction.uncertain() > 0L || insertion.uncertain() > 0L || remainder != null) break;
        }
        if (scheduled && attempted)
            nextOutputTick = tick > Long.MAX_VALUE - outputIntervalTicks ? Long.MAX_VALUE : tick + outputIntervalTicks;
        return moved;
    }

    private void enqueue(AEKey key) {
        queue.add(key);
    }

    private long availableAmount(KeyCounter external, AEKey key) {
        return external == null ? listedAmounts.getLong(key) : external.get(key);
    }

    private boolean refreshMountListing(NetworkInventoryCache.Mounts mounts, boolean discoverChanges,
        Predicate<AEKey> filter) {
        if (mountListing == null) {
            mountListing = new NetworkInventoryCache(false, true);
        }
        return mountListing.refresh(mounts, listedAmounts, (key, amount) -> {
            if (amount <= 0) queue.remove(key);
            else if (discoverChanges && filter.test(key)) enqueue(key);
        });
    }

    private boolean refreshOutputListing(MEStorage source, boolean discoverChanges, Predicate<AEKey> filter) {
        StorageListingVersion versioned = source instanceof StorageListingVersion value ? value : null;
        long version = versioned == null ? StorageListingVersion.UNKNOWN : versioned.neoecoae$listingVersion();
        Object identity = versioned == null ? source : versioned.neoecoae$listingIdentity();
        boolean busScope = StorageBusTransferFilter.isActive();
        boolean sameView = source == listedSource && identity == listedIdentity
            && busScope == listedBusScope
            && !ExactAmountCollector.isActive();
        if (sameView && version != StorageListingVersion.UNKNOWN) {
            if (version == listedVersion) return false;
            if (version >= 0 && listedVersion >= 0
                && source instanceof StorageListingDelta delta
                && delta.neoecoae$visitListingChanges(listedVersion, version, (key, amount) -> {
                    if (amount == 0) {
                        listedAmounts.removeLong(key);
                        queue.remove(key);
                    } else listedAmounts.put(key, amount);
                    if (discoverChanges && amount > 0 && filter.test(key)) enqueue(key);
                })) {
                listedVersion = version;
                return false;
            }
        }
        outputListing.clear();
        source.getAvailableStacks(outputListing);
        outputListing.removeZeros();
        listedAmounts.clear();
        for (var entry : outputListing) listedAmounts.put(entry.getKey(), entry.getLongValue());
        // Known delta sources do not need to retain another full grouped quantity mirror.
        if (source instanceof StorageListingDelta && version >= 0) {
            outputListing.clear();
        }
        listedSource = source;
        listedIdentity = identity;
        listedBusScope = busScope;
        // A scoped listing must not become the next ordinary caller's versioned baseline.
        listedVersion = ExactAmountCollector.isActive() ? StorageListingVersion.UNKNOWN : version;
        return true;
    }

}
