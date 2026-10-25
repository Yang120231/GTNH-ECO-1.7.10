package cn.dancingsnow.neoecoae.platform.ae2.storage;

import net.minecraft.item.ItemStack;

/** The native creative handler remains the actual storage implementation. */
public enum NativeCreativeCellHandler {

    INSTANCE;

    public boolean isCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof appeng.items.storage.ItemCreativeStorageCell;
    }

    public StorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
        return isCell(stack) ? StorageCells.getNativeCellInventory(stack, host) : null;
    }
}
