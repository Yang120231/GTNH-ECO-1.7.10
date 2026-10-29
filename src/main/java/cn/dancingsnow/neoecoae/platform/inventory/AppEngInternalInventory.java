package cn.dancingsnow.neoecoae.platform.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.sync.ManagedValue;

/** One authoritative inventory for native automation, MUI2 slots and persisted managed fields. */
public class AppEngInternalInventory implements IInventory, ManagedValue {

    private final InternalInventoryHost host;
    private final ItemStack[] contents;
    private final int limit;
    private final IItemHandlerModifiable itemHandler = new ItemHandler();

    public AppEngInternalInventory(InternalInventoryHost host, int slots, int limit) {
        if (slots < 0 || limit < 1) throw new IllegalArgumentException("Invalid inventory size");
        this.host = host;
        contents = new ItemStack[slots];
        this.limit = limit;
    }

    public IItemHandlerModifiable toItemHandler() {
        return itemHandler;
    }

    public int size() {
        return contents.length;
    }

    public int getSlots() {
        return contents.length;
    }

    private void check(int slot) {
        if (slot < 0 || slot >= contents.length) throw new IndexOutOfBoundsException("slot " + slot);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        check(slot);
        return contents[slot];
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        check(slot);
        contents[slot] = ItemStacks.isEmpty(stack) ? null : stack;
        host.onChangeInventory(this, slot);
    }

    /** Restores persisted contents while the host is still loading, so no change callback fires. */
    public void loadStack(int slot, ItemStack stack) {
        check(slot);
        contents[slot] = ItemStacks.isEmpty(stack) ? null : stack;
    }

    public int getSlotLimit(int slot) {
        check(slot);
        return limit;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        check(slot);
        return !ItemStacks.isEmpty(stack);
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        check(slot);
        if (ItemStacks.isEmpty(stack) || !isItemValid(slot, stack)) return stack;
        ItemStack current = contents[slot];
        if (!ItemStacks.isEmpty(current) && !ItemStacks.isSameItemSameComponents(current, stack)) return stack;
        int accepted = Math
            .min(stack.stackSize, Math.max(0, Math.min(limit, stack.getMaxStackSize()) - ItemStacks.count(current)));
        if (accepted <= 0) return stack;
        if (!simulate) setStackInSlot(slot, ItemStacks.copyWithCount(stack, ItemStacks.count(current) + accepted));
        return ItemStacks.copyWithCount(stack, stack.stackSize - accepted);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        check(slot);
        ItemStack current = contents[slot];
        int extracted = Math.min(Math.max(0, amount), ItemStacks.count(current));
        if (extracted == 0) return null;
        ItemStack result = ItemStacks.copyWithCount(current, extracted);
        if (!simulate) setStackInSlot(slot, ItemStacks.copyWithCount(current, current.stackSize - extracted));
        return result;
    }

    @Override
    public CompoundTag writeManaged() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (int slot = 0; slot < contents.length; slot++) if (!ItemStacks.isEmpty(contents[slot])) {
            CompoundTag entry = new CompoundTag(contents[slot].writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            entry.putInt("slot", slot);
            entries.add(entry);
        }
        tag.put("inventory", entries);
        return tag;
    }

    @Override
    public void readManaged(CompoundTag tag) {
        readFromNBT(tag, "inventory", null);
    }

    public void readFromNBT(CompoundTag tag, String name, HolderLookup.Provider registries) {
        ItemStack[] loaded = new ItemStack[contents.length];
        for (Tag value : tag.getList(name, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            int slot = entry.getInt("slot");
            check(slot);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry.nativeTag());
            if (ItemStacks.isEmpty(stack)) throw new IllegalStateException("Unresolved inventory item at " + slot);
            if (loaded[slot] != null) throw new IllegalStateException("Duplicate inventory slot " + slot);
            loaded[slot] = stack;
        }
        System.arraycopy(loaded, 0, contents, 0, contents.length);
    }

    @Override
    public int getSizeInventory() {
        return size();
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return extractItem(slot, amount, false);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return extractItem(slot, Integer.MAX_VALUE, false);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        setStackInSlot(slot, stack);
    }

    @Override
    public String getInventoryName() {
        return "container.neoecoae";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return limit;
    }

    @Override
    public void markDirty() {
        host.saveChangedInventory(this);
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return isItemValid(slot, stack);
    }

    // IInventory.getStackInSlot is renamed to func_70301_a in a release JAR. A separate
    // adapter keeps the custom interface's method name intact while its delegate call remaps.
    private final class ItemHandler implements IItemHandlerModifiable {

        @Override
        public int getSlots() {
            return AppEngInternalInventory.this.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return AppEngInternalInventory.this.getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            AppEngInternalInventory.this.setStackInSlot(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return AppEngInternalInventory.this.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AppEngInternalInventory.this.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return AppEngInternalInventory.this.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return AppEngInternalInventory.this.extractItem(slot, amount, simulate);
        }
    }
}
