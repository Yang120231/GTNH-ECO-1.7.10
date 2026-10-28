package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageHelper;

/**
 * AE2 ME IO Port-style full-key transfer with a small round-robin key budget.
 * <p>
 * The source is listed once per round (every queued key visited once), not every tick: a host domain or network can
 * hold 100k keys while only five move per tick. Amounts come from that round's listing; a stale amount only bounds
 * what one attempt moves, because insertion is simulated and extraction reports what actually left the source.
 */
public final class ECOIOPortTransfer {

    private static final int KEYS_PER_TICK = 5;
    private final ArrayDeque<AEKey> queue = new ArrayDeque<>();
    private final Set<AEKey> queued = new HashSet<>();
    private MEStorage previousSource;
    private KeyCounter round;
    private int keysUntilRelist;

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
        return StorageBusTransferFilter.transfer(
            excludeStorageBuses,
            () -> transferContents(
                grid,
                hostStorage,
                fillHost,
                ignoreCreativeInput,
                excludeStorageBuses,
                source,
                filter));
    }

    private long transferContents(GridView grid, MEStorage hostStorage, boolean fillHost, boolean ignoreCreativeInput,
        boolean excludeStorageBuses, IActionSource source, Predicate<AEKey> filter) {
        MEStorage networkStorage = grid.getStorageService()
            .getInventory();
        MEStorage from = fillHost ? networkStorage : hostStorage;
        MEStorage destination = fillHost ? hostStorage : networkStorage;
        if (from != previousSource) {
            previousSource = from;
            queue.clear();
            queued.clear();
            round = null;
        }
        if (round == null || keysUntilRelist <= 0) {
            if (!fillHost) {
                round = new KeyCounter();
                hostStorage.getAvailableStacks(round);
            } else if (excludeStorageBuses) {
                // The shared cache includes bus inventories and must stay unchanged for ordinary network users.
                round = new KeyCounter();
                networkStorage.getAvailableStacks(round);
            } else {
                round = grid.getStorageService()
                    .getCachedInventory();
            }
            for (var entry : round) {
                if (entry.getLongValue() > 0L && filter.test(entry.getKey()) && queued.add(entry.getKey())) {
                    queue.addLast(entry.getKey());
                }
            }
            keysUntilRelist = Math.max(1, queue.size());
        }
        KeyCounter available = round;

        long moved = 0L;
        int keysRemaining = Math.min(KEYS_PER_TICK, queue.size());
        while (keysRemaining-- > 0) {
            var key = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.removeFirst(queue);
            queued.remove(key);
            keysUntilRelist--;
            // Also recheck pending keys after a filter or interface change.
            if (!filter.test(key)) continue;
            long amount = available.get(key);
            if (amount <= 0L) continue;

            long possible = destination.insert(key, amount, Actionable.SIMULATE, source);
            if (possible <= 0L) {
                enqueue(key);
                continue;
            }

            long extracted = fillHost && ignoreCreativeInput
                ? ECOCreativeExtractionFilter.extract(from, key, possible, source)
                : from.extract(key, possible, Actionable.MODULATE, source);
            if (extracted <= 0L) {
                continue;
            }

            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), destination, key, extracted, source);
            if (inserted < extracted) {
                from.insert(key, extracted - inserted, Actionable.MODULATE, source);
            }
            if (inserted > 0L) {
                moved = inserted > Long.MAX_VALUE - moved ? Long.MAX_VALUE : moved + inserted;
            }
            if (inserted < amount) {
                enqueue(key);
            }
        }
        return moved;
    }

    private void enqueue(AEKey key) {
        if (queued.add(key)) queue.addLast(key);
    }

}
