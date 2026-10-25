package cn.dancingsnow.neoecoae.util;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.inventory.IItemHandler;

public class CellHostItemHandler implements IItemHandler {

    private final ICellHost host;

    public CellHostItemHandler(ICellHost host) {
        this.host = host;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return host.getCellStack() != null ? host.getCellStack() : null;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (host.getCellStack() != null) {
            return stack;
        } else {
            if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
                return null;
            }
            if (!host.isItemValid(stack)) {
                return stack;
            }
            if (!simulate) {
                host.setCellStack(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(stack, 1));
            }
            ItemStack copy = stack.copy();
            cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.shrink(copy, 1);
            return copy;
        }
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (host.getCellStack() == null) {
            return null;
        }
        if (amount <= 0) {
            return null;
        }
        if (!host.canExtractCell()) {
            return null;
        }
        ItemStack copy = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(host.getCellStack(), 1);
        if (!simulate) {
            host.setCellStack(null);
        }
        return copy;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return host.isItemValid(stack);
    }
}
