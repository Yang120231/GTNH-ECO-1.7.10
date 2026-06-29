package cn.dancingsnow.neoecoae.tile;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

public class TileCraftingWorker extends TileCraftingMember {

    private static final String TAG_RUNNING = "Running";
    private static final String TAG_SLOT_ID = "SlotId";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_TOTAL_PROGRESS = "TotalProgress";

    private boolean running;
    private int slotId = -1;
    private int progress;
    private int totalProgress;
    private ItemStack pendingOutput;

    public boolean acceptPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        if (details == null || table == null || this.running) {
            return false;
        }
        ItemStack output = details.getOutput(table, this.worldObj);
        if (output == null) {
            IAEItemStack[] outputs = details.getCondensedOutputs();
            if (outputs.length > 0 && outputs[0] != null) {
                output = outputs[0].getItemStack();
            }
        }
        if (output == null) {
            return false;
        }
        this.pendingOutput = output.copy();
        this.setRunningSlot(0, 0, 4, true);
        return true;
    }

    public boolean isRunning() {
        return this.running;
    }

    public boolean isWorking() {
        return this.running;
    }

    public int queueSize() {
        return this.running ? 1 : 0;
    }

    public int getSlotId() {
        return this.slotId;
    }

    public int getProgress() {
        return this.progress;
    }

    public int getTotalProgress() {
        return this.totalProgress;
    }

    public ItemStack takePendingOutput() {
        ItemStack output = this.pendingOutput;
        this.pendingOutput = null;
        if (output != null) {
            this.clearRunningSlot();
        }
        return output;
    }

    public void clearRunningSlot() {
        this.setRunningSlot(-1, 0, 0, false);
    }

    public void setRunningSlot(int slotId, int progress, int totalProgress, boolean running) {
        int normalizedTotal = Math.max(0, totalProgress);
        int normalizedProgress = Math.max(0, Math.min(progress, normalizedTotal));
        int normalizedSlot = running ? Math.max(0, slotId) : -1;
        if (this.slotId == normalizedSlot && this.progress == normalizedProgress
            && this.totalProgress == normalizedTotal
            && this.running == running) {
            return;
        }
        this.slotId = normalizedSlot;
        this.progress = normalizedProgress;
        this.totalProgress = normalizedTotal;
        this.running = running;
        this.onStateChanged();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean(TAG_RUNNING, this.running);
        tag.setInteger(TAG_SLOT_ID, this.slotId);
        tag.setInteger(TAG_PROGRESS, this.progress);
        tag.setInteger(TAG_TOTAL_PROGRESS, this.totalProgress);
        if (this.pendingOutput != null) {
            NBTTagCompound outputTag = new NBTTagCompound();
            this.pendingOutput.writeToNBT(outputTag);
            tag.setTag("PendingOutput", outputTag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.running = tag.getBoolean(TAG_RUNNING);
        this.slotId = this.running ? Math.max(0, tag.getInteger(TAG_SLOT_ID)) : -1;
        this.totalProgress = Math.max(0, tag.getInteger(TAG_TOTAL_PROGRESS));
        this.progress = Math.max(0, Math.min(tag.getInteger(TAG_PROGRESS), this.totalProgress));
        this.pendingOutput = tag.hasKey("PendingOutput")
            ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("PendingOutput"))
            : null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(TAG_RUNNING, this.running);
        tag.setInteger(TAG_SLOT_ID, this.slotId);
        tag.setInteger(TAG_PROGRESS, this.progress);
        tag.setInteger(TAG_TOTAL_PROGRESS, this.totalProgress);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.running = tag.getBoolean(TAG_RUNNING);
        this.slotId = this.running ? Math.max(0, tag.getInteger(TAG_SLOT_ID)) : -1;
        this.totalProgress = Math.max(0, tag.getInteger(TAG_TOTAL_PROGRESS));
        this.progress = Math.max(0, Math.min(tag.getInteger(TAG_PROGRESS), this.totalProgress));
    }

    private void onStateChanged() {
        this.markDirty();
        this.notifyCraftingControllerChanged();
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote || !this.running) {
            return;
        }
        if (this.progress < this.totalProgress) {
            this.progress++;
            this.markDirty();
            return;
        }
        TileECOController controller = this.findCraftingController();
        boolean accepted = controller == null || this.pendingOutput == null
            || controller.acceptCraftingOutput(this.pendingOutput);
        if (accepted) {
            this.pendingOutput = null;
            this.clearRunningSlot();
        }
    }
}
