package cn.dancingsnow.neoecoae.platform.inventory;

import net.minecraft.item.ItemStack;

public interface IItemHandlerModifiable extends IItemHandler {

    void setStackInSlot(int slot, ItemStack stack);
}
