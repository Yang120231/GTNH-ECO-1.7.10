package cn.dancingsnow.neoecoae.storage.ae2;

import java.util.Map;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;

public final class ECOAvailableItemsCache<StackType extends IAEStack> {

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
