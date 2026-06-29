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
import cn.dancingsnow.neoecoae.storage.ae2.ECOStorageDriveProvider;
import cn.dancingsnow.neoecoae.storage.item.IECOStorageMatrixItem;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;

public class TileECODrive extends TileEntity implements IInventory {

    private static final String TAG_CELL = "Cell";
    private static final String TAG_HAS_CELL = "HasCell";
    private static final String TAG_CELL_TIER = "CellTier";

    private ItemStack cellStack;
    private boolean clientHasCell;
    private String clientCellTier = "";

    public boolean hasCell() {
        return this.cellStack != null || this.clientHasCell;
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
            removed = this.cellStack;
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
        if (!this.canExtractCellStack()) {
            return null;
        }
        ItemStack stack = this.cellStack;
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
        return "container.neoecoae.eco_drive";
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
        if (slot != 0 || stack == null || !(stack.getItem() instanceof IECOStorageMatrixItem)) {
            return false;
        }
        TileECOController controller = this.findController();
        return controller == null ? !ECOStorageCellMetadata.hasNonPortableState(stack) : controller.canAcceptDriveCell(stack);
    }

    public boolean canExtractCellStack() {
        TileECOController controller = this.findController();
        return controller == null || controller.canExtractDriveCell(this);
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
        this.markDirty();
    }

    private TileECOController findController() {
        if (this.worldObj == null) {
            return null;
        }
        for (Object tile : this.worldObj.loadedTileEntityList) {
            if (tile instanceof TileECOController) {
                TileECOController controller = (TileECOController) tile;
                if (controller.isFormed()) {
                    ECOStorageDriveProvider provider = controller.createStorageDriveProvider();
                    if (provider.containsDrive(this.xCoord, this.yCoord, this.zCoord)) {
                        return controller;
                    }
                }
            }
        }
        return null;
    }

    private static String getCellTier(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        String tier = ECOStorageCellAccess.readTier(stack, "");
        if (tier.length() > 0) {
            return tier;
        }
        if (stack.getItem() instanceof NEStorageItems.ECOStorageCellItem) {
            return ((NEStorageItems.ECOStorageCellItem) stack.getItem()).getTier();
        }
        return "";
    }
}
