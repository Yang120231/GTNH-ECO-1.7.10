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
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;
import cn.dancingsnow.neoecoae.storage.item.IECOStorageMatrixItem;

public class TileECODrive extends TileEntity implements IInventory {

    private static final String TAG_CELL = "Cell";
    private static final String TAG_HAS_CELL = "HasCell";
    private static final String TAG_CELL_TIER = "CellTier";
    private static final String TAG_ONLINE = "Online";
    private static final String TAG_CELL_LED_COLOR = "CellLedColor";

    private static final int LED_OFF = 0x000000;
    private static final int LED_LOW = 0x45F05A;
    private static final int LED_MEDIUM = 0xFFEA4A;
    private static final int LED_HIGH = 0xFF9D32;
    private static final int LED_FULL = 0xFF5151;
    private static final int LED_INFINITE_MEMBER = 0xD8A8FF;

    private ItemStack cellStack;
    private boolean clientHasCell;
    private String clientCellTier = "";
    private boolean online;
    private boolean clientOnline;
    private int cellLedColor;
    private int clientCellLedColor;
    private TileECOController cachedController;

    public boolean hasCell() {
        return this.cellStack != null || this.clientHasCell;
    }

    public ItemStack getCellStack() {
        return this.cellStack;
    }

    public String getCellTierForRender() {
        return this.cellStack != null ? getCellTier(this.cellStack) : this.clientCellTier;
    }

    public boolean isOnlineForRender() {
        return this.worldObj != null && this.worldObj.isRemote ? this.clientOnline : this.online;
    }

    public int getCellLedColorForRender() {
        return this.worldObj != null && this.worldObj.isRemote ? this.clientCellLedColor : this.cellLedColor;
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
        if (!this.canExtractCellStack()) {
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
        if (!this.canExtractCellStack()) {
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
        return controller == null ? !ECOStorageCellMetadata.hasNonPortableState(stack)
            : controller.canAcceptDriveCell(stack);
    }

    public boolean canExtractCellStack() {
        TileECOController controller = this.findController();
        return controller == null ? !ECOStorageCellMetadata.hasNonPortableState(this.cellStack)
            : controller.canExtractDriveCell(this);
    }

    public boolean canRemoveFromWorld() {
        TileECOController controller = this.findController();
        if (controller != null && controller.protectsWorldPosition(this.xCoord, this.yCoord, this.zCoord)) {
            return false;
        }
        return this.cellStack == null || this.canExtractCellStack();
    }

    public boolean prepareForWorldRemoval() {
        return this.canRemoveFromWorld();
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
        this.cellLedColor = this.computeCellLedColor(this.online);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(TAG_HAS_CELL, this.cellStack != null);
        tag.setString(TAG_CELL_TIER, this.cellStack != null ? getCellTier(this.cellStack) : "");
        tag.setBoolean(TAG_ONLINE, this.online);
        tag.setInteger(TAG_CELL_LED_COLOR, this.cellLedColor);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.clientHasCell = tag.getBoolean(TAG_HAS_CELL);
        this.clientCellTier = tag.getString(TAG_CELL_TIER);
        this.clientOnline = tag.getBoolean(TAG_ONLINE);
        this.clientCellLedColor = tag.getInteger(TAG_CELL_LED_COLOR);
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
        TileECOController controller = this.findController();
        if (controller != null) {
            controller.onStorageBackendChanged();
        }
        this.updateVisualState(controller);
        this.markDirty();
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote || this.worldObj.getTotalWorldTime() % 20L != 0L) {
            return;
        }
        this.updateVisualState(this.findController());
    }

    public void refreshOnlineState(TileECOController controller) {
        this.updateVisualState(controller);
    }

    private void updateVisualState(TileECOController controller) {
        boolean nowOnline = controller != null && this.hasCell() && this.hasOnlineInterface(controller);
        int nowLedColor = this.computeCellLedColor(nowOnline);
        if (this.online == nowOnline && this.cellLedColor == nowLedColor) {
            return;
        }
        this.online = nowOnline;
        this.cellLedColor = nowLedColor;
        this.markDirty();
    }

    private int computeCellLedColor(boolean nowOnline) {
        if (!nowOnline || this.cellStack == null) {
            return LED_OFF;
        }
        if (ECOStorageCellMetadata.hasNonPortableState(this.cellStack)) {
            return LED_INFINITE_MEMBER;
        }
        if (!(this.cellStack.getItem() instanceof IECOStorageMatrixItem)) {
            return LED_OFF;
        }
        long total = Math.max(0L, ((IECOStorageMatrixItem) this.cellStack.getItem()).getDisplayBytes(this.cellStack));
        if (total <= 0L) {
            return LED_LOW;
        }
        ECOStorageBackend backend = ECOStorageCellAccess.load(this.cellStack);
        long used = backend.getUsed()
            .toLongSaturated();
        if (used >= total) {
            return LED_FULL;
        }
        double ratio = (double) used / (double) total;
        if (ratio >= 0.90D) {
            return LED_FULL;
        }
        if (ratio >= 0.75D) {
            return LED_HIGH;
        }
        if (ratio >= 0.50D) {
            return LED_MEDIUM;
        }
        return LED_LOW;
    }

    private boolean hasOnlineInterface(TileECOController controller) {
        if (this.worldObj == null || controller == null) {
            return false;
        }
        return controller.hasOnlineStorageInterface();
    }

    private TileECOController findController() {
        if (this.worldObj == null) {
            return null;
        }
        if (this.cachedController != null && this.cachedController.getWorldObj() == this.worldObj
            && this.cachedController.isFormed()
            && this.cachedController.hasFormedMemberBlock(this.xCoord, this.yCoord, this.zCoord)) {
            return this.cachedController;
        }
        for (TileECOController controller : ECOControllerRegistry.controllers(this.worldObj)) {
            if (controller.isFormed() && controller.hasFormedMemberBlock(this.xCoord, this.yCoord, this.zCoord)) {
                this.cachedController = controller;
                return controller;
            }
        }
        this.cachedController = null;
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
