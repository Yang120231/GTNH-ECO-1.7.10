package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.tile.TileECOController;

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

    @Override
    public boolean canTakeStack(net.minecraft.entity.player.EntityPlayer player) {
        if (player != null && player.openContainer instanceof ContainerECOStorageController
            && this.inventory instanceof TileECOController
            && ((TileECOController) this.inventory).getWorldObj() != null
            && ((TileECOController) this.inventory).getWorldObj().isRemote) {
            return ((ContainerECOStorageController) player.openContainer).canTakeInfiniteComponent();
        }
        return !(this.inventory instanceof TileECOController)
            || ((TileECOController) this.inventory).canTakeInfiniteStorageComponent();
    }
}
