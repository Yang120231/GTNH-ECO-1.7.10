package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotInfiniteComponent extends Slot {

    public SlotInfiniteComponent(IInventory inventory, int slot, int x, int y) {
        super(inventory, slot, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return this.inventory.isItemValidForSlot(this.getSlotIndex(), stack);
    }

    @Override
    public int getSlotStackLimit() {
        return this.inventory.getInventoryStackLimit();
    }
}

