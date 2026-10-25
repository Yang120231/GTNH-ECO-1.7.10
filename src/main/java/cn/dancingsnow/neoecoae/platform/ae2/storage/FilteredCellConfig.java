package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Native phantom configuration with the imported item's slot bound and exact-key predicate. */
public final class FilteredCellConfig extends IAEStackInventory {

    private final ItemStack stack;
    private final Predicate<AEKey> allowed;

    public FilteredCellConfig(ItemStack stack, int slots, Predicate<AEKey> allowed) {
        super(null, slots);
        this.stack = stack;
        this.allowed = allowed;
        readFromNBT(stack.getTagCompound(), "list");
    }

    @Override
    public void putAEStackInSlot(int slot, IAEStack<?> value) {
        if (value == null || allowed.test(AEKey.fromNative(value))) super.putAEStackInSlot(slot, value);
    }

    @Override
    public void markDirty() {
        writeToNBT(stack, "list");
    }
}
