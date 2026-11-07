package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.network.ServerThread;

/**
 * rv3's NetworkMonitor marks its cached list stale on every change, so the next {@code getStorageList()} re-lists every
 * handler of the network; with a terminal open and constant traffic that is a full network scan per tick. The changes
 * it posts can update the cached list instead, except when insertion fed crafting CPUs (handled by the monitor
 * mixin) or the report carries crafting metadata rather than a stored-stock difference. Anything that cannot be
 * applied exactly falls back to AE2's rebuild, and a periodic rebuild absorbs inaccurate reports from other mods.
 */
public final class NativeIncrementalStorageList {

    private NativeIncrementalStorageList() {}

    public static boolean eligible(IMEInventoryHandler<?> handler) {
        return PlanningSettings.Storage.incrementalNetworkStorageList && ServerThread.isCurrent()
            && handler instanceof NativeNetworkReads
            && !((NativeNetworkReads) handler).neoecoae$readsOtherNetwork();
    }

    /** Returns false when the list must be rebuilt instead; the list may then be partially updated. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static boolean apply(IItemList list, IAEStackType<?> type, Iterable changes) {
        for (Object posted : changes) {
            if (posted == null) continue;
            IAEStack<?> change = (IAEStack<?>) posted;
            if (change.getStackType() != type) return false;
            // Pattern refreshes also post changes, but their stack sizes are not newly stored items.
            if (change.isCraftable() || change.getCountRequestable() != 0) return false;
            long delta = change.getStackSize();
            if (delta == 0) continue;
            IAEStack existing = list.findPrecise(change);
            if (existing == null) {
                if (delta < 0) return false;
                list.add(
                    change.copy()
                        .reset()
                        .setStackSize(delta));
                continue;
            }
            long current = existing.getStackSize();
            if (current < 0) return false;
            long next = current + delta;
            if (delta > 0 && next < 0) next = Long.MAX_VALUE;
            if (next < 0) return false;
            existing.setStackSize(next);
        }
        return true;
    }
}
