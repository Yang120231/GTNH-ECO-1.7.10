package cn.dancingsnow.neoecoae.storage.domain;

import java.util.UUID;

import net.minecraft.world.World;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.storage.ae2.ECOAE2KeyConverter;
import cn.dancingsnow.neoecoae.storage.ae2.ECOAvailableItemsCache;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public class ECOHostDomainInventoryHandler<StackType extends IAEStack> implements IMEInventoryHandler<StackType> {

    private final TileECOController controller;
    private final StorageChannel channel;
    private final ECOAvailableItemsCache<StackType> availableItemsCache = new ECOAvailableItemsCache<StackType>();
    private UUID cachedDomainId;
    private ECOStorageBackend cachedBackend;

    public ECOHostDomainInventoryHandler(TileECOController controller, StorageChannel channel) {
        this.controller = controller;
        this.channel = channel;
    }

    @Override
    public StackType injectItems(StackType input, Actionable type, BaseActionSource src) {
        if (input == null || input.getStackSize() <= 0L
            || input.getChannel() != this.channel
            || !this.controller.canUseHostDomainStorage()) {
            return input;
        }
        ECOStorageBackend backend = this.getBackend();
        if (backend == null) {
            return input;
        }
        ECOAmount inserted = backend.insert(
            ECOAE2KeyConverter.toExistingKey(
                input,
                backend.getEntriesView()
                    .keySet()),
            ECOAmount.of(input.getStackSize()),
            type == Actionable.SIMULATE);
        if (inserted.isZero()) {
            return input;
        }
        if (type == Actionable.MODULATE) {
            this.markChanged();
        }
        long remaining = input.getStackSize() - inserted.toLongSaturated();
        return remaining <= 0L ? null
            : (StackType) input.copy()
                .setStackSize(remaining);
    }

    @Override
    public StackType extractItems(StackType request, Actionable mode, BaseActionSource src) {
        if (request == null || request.getStackSize() <= 0L
            || request.getChannel() != this.channel
            || !this.controller.canUseHostDomainStorage()) {
            return null;
        }
        ECOStorageBackend backend = this.getBackend();
        if (backend == null) {
            return null;
        }
        ECOAmount extracted = backend.extract(
            ECOAE2KeyConverter.toExistingKey(
                request,
                backend.getEntriesView()
                    .keySet()),
            ECOAmount.of(request.getStackSize()),
            mode == Actionable.SIMULATE);
        if (extracted.isZero()) {
            return null;
        }
        if (mode == Actionable.MODULATE) {
            this.markChanged();
        }
        return (StackType) request.copy()
            .setStackSize(extracted.toLongSaturated());
    }

    @Override
    public IItemList<StackType> getAvailableItems(IItemList<StackType> out, int iteration) {
        IItemList<StackType> snapshot = this.getCachedAvailableItems();
        for (StackType stack : snapshot) {
            out.addStorage((StackType) stack.copy());
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return this.channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(StackType input) {
        ECOStorageBackend backend = this.getBackend();
        return backend != null && input != null
            && input.getChannel() == this.channel
            && backend.getAmount(
                ECOAE2KeyConverter.toExistingKey(
                    input,
                    backend.getEntriesView()
                        .keySet()))
                .compareTo(ECOAmount.ZERO) > 0;
    }

    @Override
    public boolean canAccept(StackType input) {
        return input != null && input.getChannel() == this.channel && this.controller.canUseHostDomainStorage();
    }

    @Override
    public int getPriority() {
        return this.controller.getPriority();
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
    public IMEInventory<StackType> getInternal() {
        return this;
    }

    private ECOStorageBackend getBackend() {
        World world = this.controller.getWorldObj();
        UUID domainId = this.controller.getHostDomainId();
        if (world == null || domainId == null) {
            return null;
        }
        if (domainId.equals(this.cachedDomainId) && this.cachedBackend != null) {
            return this.cachedBackend;
        }
        this.cachedDomainId = domainId;
        this.cachedBackend = ECOStorageDomainData.get(world)
            .getOrCreateDomain(domainId);
        return this.cachedBackend;
    }

    private IItemList<StackType> getCachedAvailableItems() {
        ECOStorageBackend backend = this.getBackend();
        return this.availableItemsCache.get(this.channel, backend);
    }

    private void markChanged() {
        World world = this.controller.getWorldObj();
        if (world != null) {
            ECOStorageDomainData.get(world)
                .markDirty();
        }
        this.availableItemsCache.invalidate();
        this.controller.onHostDomainContentChanged();
    }
}
