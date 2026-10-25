package cn.dancingsnow.neoecoae.platform.inventory;

import net.minecraft.item.ItemStack;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

/** The source item-handler contract over native stacks; no FML world or registry is required. */
public class ItemStackHandler implements IItemHandlerModifiable {
    private final ItemStack[] stacks;
    public ItemStackHandler(int slots) {
        if (slots < 0) throw new IllegalArgumentException("Negative slot count"); stacks = new ItemStack[slots];
    }
    protected void onContentsChanged(int slot) {}
    @Override public int getSlots() { return stacks.length; }
    @Override public ItemStack getStackInSlot(int slot) { return stacks[slot]; }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    @Override public void setStackInSlot(int slot, ItemStack stack) { stacks[slot] = ItemStacks.isEmpty(stack) ? null : stack; onContentsChanged(slot); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (ItemStacks.isEmpty(stack) || !isItemValid(slot, stack)) return stack;
        ItemStack existing = stacks[slot];
        if (!ItemStacks.isEmpty(existing) && !ItemStacks.isSameItemSameComponents(existing, stack)) return stack;
        int stored = ItemStacks.count(existing), limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        int accepted = Math.min(stack.stackSize, Math.max(0, limit - stored));
        if (accepted == 0) return stack;
        if (!simulate) { stacks[slot] = ItemStacks.copyWithCount(stack, stored + accepted); onContentsChanged(slot); }
        return accepted == stack.stackSize ? null : ItemStacks.copyWithCount(stack, stack.stackSize - accepted);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = stacks[slot]; if (amount <= 0 || ItemStacks.isEmpty(existing)) return null;
        int extracted = Math.min(amount, Math.min(existing.stackSize, existing.getMaxStackSize()));
        ItemStack result = ItemStacks.copyWithCount(existing, extracted);
        if (!simulate) { stacks[slot] = extracted == existing.stackSize ? null : ItemStacks.copyWithCount(existing, existing.stackSize - extracted); onContentsChanged(slot); }
        return result;
    }
}
