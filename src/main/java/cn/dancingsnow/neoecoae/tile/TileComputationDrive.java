package cn.dancingsnow.neoecoae.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.all.NEStorageItems;

public class TileComputationDrive extends TileEntity implements IInventory {

    private static final String TAG_CELL = "Cell";
    private static final String TAG_HAS_CELL = "HasCell";
    private static final String TAG_CELL_TIER = "CellTier";

    private ItemStack cellStack;
    private boolean clientHasCell;
    private String clientCellTier = "";

    public boolean hasCell() {
        return this.cellStack != null || this.clientHasCell;
    }

    public boolean hasCellForRendering() {
        return this.hasCell() && this.getCellTierForRender()
            .length() > 0;
    }

    public ItemStack getCellStack() {
        return this.cellStack;
    }

    public String getCellTierForRender() {
        return this.cellStack != null ? getCellTier(this.cellStack) : this.clientCellTier;
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? this.cellStack : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || this.cellStack == null || amount <= 0) {
            return null;
        }
        ItemStack removed;
        if (this.cellStack.stackSize <= amount) {
            removed = this.cellStack.copy();
            this.cellStack = null;
        } else {
            removed = this.cellStack.splitStack(amount);
            if (this.cellStack.stackSize <= 0) {
                this.cellStack = null;
            }
        }
        this.onInventoryChanged();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0) {
            return null;
        }
        ItemStack stack = this.cellStack == null ? null : this.cellStack.copy();
        this.cellStack = null;
        if (stack != null) {
            this.onInventoryChanged();
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        if (stack != null && !this.isItemValidForSlot(slot, stack)) {
            return;
        }
        this.cellStack = stack;
        if (this.cellStack != null && this.cellStack.stackSize > this.getInventoryStackLimit()) {
            this.cellStack.stackSize = this.getInventoryStackLimit();
        }
        this.onInventoryChanged();
    }

    @Override
    public String getInventoryName() {
        return "container.neoecoae.computation_drive";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
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
        return slot == 0 && isComputationCell(stack);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord,
                this.yCoord,
                this.zCoord,
                this.xCoord,
                this.yCoord,
                this.zCoord);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (this.cellStack != null) {
            NBTTagCompound cellTag = new NBTTagCompound();
            this.cellStack.writeToNBT(cellTag);
            tag.setTag(TAG_CELL, cellTag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.cellStack = tag.hasKey(TAG_CELL) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_CELL)) : null;
        if (!isComputationCell(this.cellStack)) {
            this.cellStack = null;
        }
        this.clientHasCell = this.cellStack != null;
        this.clientCellTier = this.cellStack != null ? getCellTier(this.cellStack) : "";
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(TAG_HAS_CELL, this.cellStack != null);
        tag.setString(TAG_CELL_TIER, this.cellStack != null ? getCellTier(this.cellStack) : "");
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.clientHasCell = tag.getBoolean(TAG_HAS_CELL);
        this.clientCellTier = tag.getString(TAG_CELL_TIER);
        if (this.worldObj != null) {
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord,
                this.yCoord,
                this.zCoord,
                this.xCoord,
                this.yCoord,
                this.zCoord);
        }
    }

    private void onInventoryChanged() {
        this.clientHasCell = this.cellStack != null;
        this.clientCellTier = this.cellStack != null ? getCellTier(this.cellStack) : "";
        this.markDirty();
    }

    public static boolean isComputationCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof NEStorageItems.ECOComputationCellItem;
    }

    private static String getCellTier(ItemStack stack) {
        if (!isComputationCell(stack)) {
            return "";
        }
        return ((NEStorageItems.ECOComputationCellItem) stack.getItem()).getTier();
    }
}
