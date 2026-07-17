package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.item.ItemECOStorageRecoveryTerminal;

/** Server/client holder for the storage recovery terminal in the active hotbar slot. */
public class ContainerECOStorageRecoveryTerminal extends Container {

    private final EntityPlayer player;
    private final int itemSlot;

    public ContainerECOStorageRecoveryTerminal(EntityPlayer player) {
        this.player = player;
        this.itemSlot = player.inventory.currentItem;
        this.addSlotToContainer(new Slot(player.inventory, this.itemSlot, 8, 8) {

            @Override
            public boolean canTakeStack(EntityPlayer ignored) {
                return false;
            }

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemECOStorageRecoveryTerminal;
            }
        });
    }

    public ItemStack getTerminalStack() {
        if (this.player == null || this.player.inventory == null) {
            return null;
        }
        ItemStack stack = this.player.inventory.getStackInSlot(this.itemSlot);
        return stack != null && stack.getItem() instanceof ItemECOStorageRecoveryTerminal ? stack : null;
    }

    public int getItemSlot() {
        return this.itemSlot;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack stack = this.getTerminalStack();
        return player == this.player && stack != null && stack.getItem() instanceof ItemECOStorageRecoveryTerminal;
    }
}
