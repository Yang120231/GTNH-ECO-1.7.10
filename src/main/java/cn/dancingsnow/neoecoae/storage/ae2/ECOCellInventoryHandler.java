package cn.dancingsnow.neoecoae.storage.ae2;

import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;

public class ECOCellInventoryHandler<StackType extends IAEStack> implements IMEInventoryHandler<StackType> {

    private final ItemStack cellStack;
    private final ISaveProvider saveProvider;
    private final StorageChannel channel;
    private ECOStorageBackend backend;
    private long cachedRevision = Long.MIN_VALUE;
    private IItemList<StackType> cachedAvailableItems;

    public ECOCellInventoryHandler(ItemStack cellStack, ISaveProvider saveProvider, StorageChannel channel) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.channel = channel;
        this.backend = ECOStorageCellAccess.load(cellStack);
    }

    @Override
    public StackType injectItems(StackType input, Actionable type, BaseActionSource src) {
        if (input == null || input.getStackSize() <= 0L || input.getChannel() != this.channel) {
            return input;
        }
        ECOAmount requested = ECOAmount.of(input.getStackSize());
        ECOAmount inserted = this.backend.insert(ECOAE2KeyConverter.toKey(input), requested, type == Actionable.SIMULATE);
        if (inserted.isZero()) {
            return input;
        }
        if (type == Actionable.MODULATE) {
            this.saveChanges();
        }
        long remaining = input.getStackSize() - inserted.toLongSaturated();
        return remaining <= 0L ? null : (StackType) input.copy()
            .setStackSize(remaining);
    }

    @Override
    public StackType extractItems(StackType request, Actionable mode, BaseActionSource src) {
        if (request == null || request.getStackSize() <= 0L || request.getChannel() != this.channel) {
            return null;
        }
        ECOAmount extracted = this.backend.extract(
            ECOAE2KeyConverter.toKey(request),
            ECOAmount.of(request.getStackSize()),
            mode == Actionable.SIMULATE);
        if (extracted.isZero()) {
            return null;
        }
        if (mode == Actionable.MODULATE) {
            this.saveChanges();
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
        return this.canAccept(input) && this.backend.getAmount(ECOAE2KeyConverter.toKey(input))
            .compareTo(ECOAmount.ZERO) > 0;
    }

    @Override
    public boolean canAccept(StackType input) {
        return input != null && input.getChannel() == this.channel;
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
    public IMEInventory<StackType> getInternal() {
        return this;
    }

    public ECOStorageBackend getBackend() {
        return this.backend;
    }

    private void saveChanges() {
        ECOStorageCellAccess.save(this.cellStack, this.backend);
        this.cachedRevision = Long.MIN_VALUE;
        this.cachedAvailableItems = null;
        if (this.saveProvider != null) {
            this.saveProvider.saveChanges(this);
        }
    }

    private IItemList<StackType> getCachedAvailableItems() {
        long revision = this.backend.getRevision();
        if (this.cachedAvailableItems != null && this.cachedRevision == revision) {
            return this.cachedAvailableItems;
        }
        IItemList<StackType> out = this.channel.createList();
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : this.backend.snapshot()
            .getEntries()
            .entrySet()) {
            long amount = entry.getValue()
                .toLongSaturated();
            StackType stack = this.toAEStack(entry.getKey(), amount);
            if (stack != null) {
                out.addStorage(stack);
            }
        }
        this.cachedAvailableItems = out;
        this.cachedRevision = revision;
        return out;
    }

    private StackType toAEStack(ECOStorageKey key, long amount) {
        if (this.channel == StorageChannel.ITEMS) {
            return (StackType) ECOAE2KeyConverter.toItemStack(key, amount);
        }
        return (StackType) ECOAE2KeyConverter.toFluidStack(key, amount);
    }
}
