package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.inventory.IItemHandlerModifiable;

/** Real delegate, preserving the source handler's validation, simulation, copy and mutation callbacks. */
public final class NativeItemHandler implements com.cleanroommc.modularui.utils.item.IItemHandlerModifiable {

    private final IItemHandlerModifiable delegate;
    private final java.util.function.BooleanSupplier canExtract;

    public NativeItemHandler(IItemHandlerModifiable delegate) {
        this(delegate, () -> true);
    }

    public NativeItemHandler(IItemHandlerModifiable delegate, java.util.function.BooleanSupplier canExtract) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
        this.canExtract = canExtract;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return canExtract.getAsBoolean() ? delegate.extractItem(slot, amount, simulate) : null;
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        delegate.setStackInSlot(slot, stack);
    }
}
