package cn.dancingsnow.neoecoae.platform.ae2.storage;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Native remaining-insert / actual-extraction convention, backed by the imported business inventory. */
public final class NativeStorageInventory<T extends IAEStack> implements IMEInventoryHandler<T>, DelegatingStorage {

    private final ECOStorageCell cell;
    private final StorageChannel channel;

    public NativeStorageInventory(ItemStack stack, appeng.api.storage.ISaveProvider host, StorageChannel channel) {
        this.channel = channel;
        cell = ((cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem) stack.getItem())
            .createCellInventory(stack, host == null ? null : () -> host.saveChanges(this));
    }

    public ECOStorageCell business() {
        return cell;
    }

    @Override
    public MEStorage neoecoae$getDelegate() {
        return cell;
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource source) {
        if (input == null) return null;
        AEKey key = AEKey.fromNative(input);
        if (key == null || key.getType()
            .nativeChannel() != channel) return input;
        long amount = input.getStackSize();
        if (amount <= 0) return input;
        long inserted = cell.insert(key, amount, mode, IActionSource.of(source));
        if (inserted == amount) return null;
        return (T) input.copy()
            .setStackSize(amount - inserted);
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource source) {
        if (request == null || request.getStackSize() <= 0) return null;
        AEKey key = AEKey.fromNative(request);
        if (key == null || key.getType()
            .nativeChannel() != channel) return null;
        long amount = cell.extract(key, request.getStackSize(), mode, IActionSource.of(source));
        return amount == 0 ? null
            : (T) request.copy()
                .setStackSize(amount);
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        KeyCounter available = cell.getAvailableStacks();
        for (var entry : available) if (entry.getKey()
            .getType()
            .nativeChannel() == channel && entry.getLongValue() > 0) NativeStackAmounts.add(
                out,
                (T) entry.getKey()
                    .toNative(entry.getLongValue()));
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        AEKey key = AEKey.fromNative(input);
        return key != null && cell.isPreferredStorageFor(key, IActionSource.empty());
    }

    @Override
    public boolean canAccept(T input) {
        AEKey key = AEKey.fromNative(input);
        return key != null && key.getType()
            .nativeChannel() == channel && cell.insert(key, 1, Actionable.SIMULATE, IActionSource.empty()) == 1;
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
    public boolean validForPass(int pass) {
        return pass == 1 || pass == 2;
    }
}
