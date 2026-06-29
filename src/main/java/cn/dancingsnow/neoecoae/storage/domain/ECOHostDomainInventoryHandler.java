package cn.dancingsnow.neoecoae.storage.domain;

import java.util.Map;

import net.minecraft.world.World;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.storage.ae2.ECOAE2KeyConverter;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public class ECOHostDomainInventoryHandler implements IMEInventoryHandler<IAEItemStack> {

    private final TileECOController controller;
    private long cachedRevision = Long.MIN_VALUE;
    private IItemList<IAEItemStack> cachedAvailableItems;

    public ECOHostDomainInventoryHandler(TileECOController controller) {
        this.controller = controller;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
        if (input == null || input.getStackSize() <= 0L || !this.controller.canUseHostDomainStorage()) {
            return input;
        }
        ECOStorageBackend backend = this.getBackend();
        if (backend == null) {
            return input;
        }
        ECOAmount inserted = backend.insert(ECOAE2KeyConverter.toKey(input), ECOAmount.of(input.getStackSize()),
            type == Actionable.SIMULATE);
        if (inserted.isZero()) {
            return input;
        }
        if (type == Actionable.MODULATE) {
            this.markChanged();
        }
        long remaining = input.getStackSize() - inserted.toLongSaturated();
        return remaining <= 0L ? null : input.copy().setStackSize(remaining);
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        if (request == null || request.getStackSize() <= 0L || !this.controller.canUseHostDomainStorage()) {
            return null;
        }
        ECOStorageBackend backend = this.getBackend();
        if (backend == null) {
            return null;
        }
        ECOAmount extracted = backend.extract(ECOAE2KeyConverter.toKey(request), ECOAmount.of(request.getStackSize()),
            mode == Actionable.SIMULATE);
        if (extracted.isZero()) {
            return null;
        }
        if (mode == Actionable.MODULATE) {
            this.markChanged();
        }
        return request.copy().setStackSize(extracted.toLongSaturated());
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
        IItemList<IAEItemStack> snapshot = this.getCachedAvailableItems();
        for (IAEItemStack stack : snapshot) {
            out.addStorage(stack.copy());
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        ECOStorageBackend backend = this.getBackend();
        return backend != null && input != null && backend.getAmount(ECOAE2KeyConverter.toKey(input)).compareTo(ECOAmount.ZERO) > 0;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return input != null && input.getChannel() == StorageChannel.ITEMS && this.controller.canUseHostDomainStorage();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IMEInventory<IAEItemStack> getInternal() {
        return this;
    }

    private ECOStorageBackend getBackend() {
        World world = this.controller.getWorldObj();
        if (world == null || this.controller.getHostDomainId() == null) {
            return null;
        }
        return ECOStorageDomainData.get(world).getOrCreateDomain(this.controller.getHostDomainId());
    }

    private IItemList<IAEItemStack> getCachedAvailableItems() {
        ECOStorageBackend backend = this.getBackend();
        if (backend == null) {
            return StorageChannel.ITEMS.createList();
        }
        long revision = backend.getRevision();
        if (this.cachedAvailableItems != null && this.cachedRevision == revision) {
            return this.cachedAvailableItems;
        }
        IItemList<IAEItemStack> out = StorageChannel.ITEMS.createList();
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : backend.snapshot().getEntries().entrySet()) {
            IAEStack stack = ECOAE2KeyConverter.toItemStack(entry.getKey(), entry.getValue().toLongSaturated());
            if (stack instanceof IAEItemStack) {
                out.addStorage((IAEItemStack) stack);
            }
        }
        this.cachedRevision = revision;
        this.cachedAvailableItems = out;
        return out;
    }

    private void markChanged() {
        World world = this.controller.getWorldObj();
        if (world != null) {
            ECOStorageDomainData.get(world).markDirty();
        }
        this.cachedRevision = Long.MIN_VALUE;
        this.cachedAvailableItems = null;
        this.controller.onHostDomainContentChanged();
    }
}
