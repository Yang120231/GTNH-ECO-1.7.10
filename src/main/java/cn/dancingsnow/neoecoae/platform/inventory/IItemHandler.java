package cn.dancingsnow.neoecoae.platform.inventory;

import net.minecraft.item.ItemStack;

/** The item-handler operations consumed by upstream business, backed by Forge inventories. */
public interface IItemHandler {

    int getSlots();

    ItemStack getStackInSlot(int slot);

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);
}
