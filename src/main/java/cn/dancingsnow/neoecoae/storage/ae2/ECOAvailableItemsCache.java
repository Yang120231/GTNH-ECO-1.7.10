package cn.dancingsnow.neoecoae.storage.ae2;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;

public final class ECOAvailableItemsCache<StackType extends IAEStack> {

    private static final int MAX_INCREMENTAL_DIRTY_KEYS = 128;

    private long cachedRevision = Long.MIN_VALUE;
    private IItemList<StackType> cachedAvailableItems;

    public IItemList<StackType> get(StorageChannel channel, ECOStorageBackend backend) {
        if (backend == null) {
            return channel.createList();
        }
        long revision = backend.getRevision();
        if (this.cachedAvailableItems != null && this.cachedRevision == revision) {
            return this.cachedAvailableItems;
        }
        Set<ECOStorageKey> dirtyKeys = new LinkedHashSet<ECOStorageKey>();
        if (this.cachedAvailableItems != null
            && backend.collectDirtyKeysSince(this.cachedRevision, dirtyKeys)
            && dirtyKeys.size() <= MAX_INCREMENTAL_DIRTY_KEYS) {
            // AE2's IItemList has no removal/update primitive, so the first phase still rebuilds
            // the list. Keeping this branch centralizes the future incremental index swap.
        }
        IItemList<StackType> out = channel.createList();
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : backend.getEntriesView()
            .entrySet()) {
            StackType stack = this.toAEStack(channel, entry.getKey(), entry.getValue());
            if (stack != null) {
                out.addStorage(stack);
            }
        }
        this.cachedAvailableItems = out;
        this.cachedRevision = revision;
        return out;
    }

    public void invalidate() {
        this.cachedRevision = Long.MIN_VALUE;
        this.cachedAvailableItems = null;
    }

    private StackType toAEStack(StorageChannel channel, ECOStorageKey key, ECOAmount amount) {
        if (amount == null || amount.isZero()) {
            return null;
        }
        long size = amount.toLongSaturated();
        if (channel == StorageChannel.ITEMS) {
            return (StackType) ECOAE2KeyConverter.toItemStack(key, size);
        }
        return (StackType) ECOAE2KeyConverter.toFluidStack(key, size);
    }
}
