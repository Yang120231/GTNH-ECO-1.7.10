package cn.dancingsnow.neoecoae.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

public class TileCraftingHatch extends TileCraftingMember implements IInventory {

    private static final String TAG_INPUT = "Input";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_STACK = "Stack";
    private static final int SLOTS = 27;

    private boolean input;
    private final ItemStack[] items = new ItemStack[SLOTS];

    public TileCraftingHatch() {
        this(true);
    }

    public TileCraftingHatch(boolean input) {
        this.input = input;
    }

    public boolean isInputHatch() {
        return this.input;
    }

    public boolean isInput() {
        return this.input;
    }

    public int getOccupiedSlotCount() {
        return this.usedSlots();
    }

    public int getCachedItemCount() {
        int count = 0;
        for (ItemStack stack : this.items) {
            if (stack != null) {
                count = saturatedAdd(count, Math.max(0, stack.stackSize));
            }
        }
        return count;
    }

    public boolean insertOutput(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] == null) {
                this.items[i] = remaining;
                this.onInventoryChanged();
                return true;
            }
            if (this.canMerge(this.items[i], remaining)) {
                int limit = Math.min(this.getInventoryStackLimit(), this.items[i].getMaxStackSize());
                int moved = Math.min(limit - this.items[i].stackSize, remaining.stackSize);
                if (moved > 0) {
                    this.items[i].stackSize += moved;
                    remaining.stackSize -= moved;
                    if (remaining.stackSize <= 0) {
                        this.onInventoryChanged();
                        return true;
                    }
                }
            }
        }
        this.onInventoryChanged();
        return false;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : this.items) {
            if (stack != null) {
                used++;
            }
        }
        return used;
    }

    @Override
    public int getSizeInventory() {
        return this.items.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < this.items.length ? this.items[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < 0 || slot >= this.items.length || this.items[slot] == null || amount <= 0) {
            return null;
        }
        ItemStack removed;
        if (this.items[slot].stackSize <= amount) {
            removed = this.items[slot];
            this.items[slot] = null;
        } else {
            removed = this.items[slot].splitStack(amount);
            if (this.items[slot].stackSize <= 0) {
                this.items[slot] = null;
            }
        }
        this.onInventoryChanged();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= this.items.length) {
            return null;
        }
        ItemStack stack = this.items[slot];
        this.items[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.items.length) {
            return;
        }
        this.items[slot] = stack;
        if (this.items[slot] != null && this.items[slot].stackSize > this.getInventoryStackLimit()) {
            this.items[slot].stackSize = this.getInventoryStackLimit();
        }
        this.onInventoryChanged();
    }

    @Override
    public String getInventoryName() {
        return this.input ? "container.neoecoae.crafting_input_hatch" : "container.neoecoae.crafting_output_hatch";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.worldObj != null && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
            && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean(TAG_INPUT, this.input);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte(TAG_SLOT, (byte) i);
                NBTTagCompound stackTag = new NBTTagCompound();
                this.items[i].writeToNBT(stackTag);
                entry.setTag(TAG_STACK, stackTag);
                list.appendTag(entry);
            }
        }
        tag.setTag(TAG_ITEMS, list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.input = !tag.hasKey(TAG_INPUT) || tag.getBoolean(TAG_INPUT);
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }
        NBTTagList list = tag.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte(TAG_SLOT) & 255;
            if (slot >= 0 && slot < this.items.length) {
                this.items[slot] = ItemStack.loadItemStackFromNBT(entry.getCompoundTag(TAG_STACK));
            }
        }
    }

    private void onInventoryChanged() {
        this.markDirty();
        this.notifyCraftingControllerChanged();
    }

    private boolean canMerge(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.getItem() == second.getItem()
            && first.getItemDamage() == second.getItemDamage()
            && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static int saturatedAdd(int left, int right) {
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }
}
