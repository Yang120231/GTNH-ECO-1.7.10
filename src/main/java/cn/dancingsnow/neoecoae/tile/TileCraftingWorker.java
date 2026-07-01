package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;

public class TileCraftingWorker extends TileCraftingMember {

    public static final int BASE_QUEUE_CAPACITY = 32;

    private static final String TAG_RUNNING = "Running";
    private static final String TAG_SLOT_ID = "SlotId";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_TOTAL_PROGRESS = "TotalProgress";
    private static final String TAG_QUEUE = "Queue";
    private static final String TAG_OUTPUT = "Output";
    private static final int PROGRESS_SYNC_BUCKETS = 10;

    private final List<WorkEntry> queue = new ArrayList<WorkEntry>();

    public boolean acceptPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        if (details == null || table == null || !this.hasQueueSpace()) {
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
        TileECOController controller = this.findCraftingController();
        if (controller != null && !controller.consumeCraftingCoolantForWork(1)) {
            return false;
        }
        this.queue.add(new WorkEntry(this.queue.size(), 0, ECOEnergyProfile.CRAFTING_WORK_MAX_PROGRESS, output.copy()));
        this.normalizeSlots();
        this.onStateChanged();
        return true;
    }

    public boolean isRunning() {
        return !this.queue.isEmpty();
    }

    public boolean isWorking() {
        return this.isRunning();
    }

    public int queueSize() {
        return this.queue.size();
    }

    public int queueCapacity() {
        return BASE_QUEUE_CAPACITY;
    }

    public boolean hasQueueSpace() {
        return this.queueSize() < this.queueCapacity();
    }

    public int getSlotId() {
        WorkEntry entry = this.peekEntry();
        return entry == null ? -1 : entry.slotId;
    }

    public int getProgress() {
        WorkEntry entry = this.peekEntry();
        return entry == null ? 0 : entry.progress;
    }

    public int getTotalProgress() {
        WorkEntry entry = this.peekEntry();
        return entry == null ? 0 : entry.totalProgress;
    }

    public ItemStack getCurrentOutput() {
        WorkEntry entry = this.peekEntry();
        return entry == null || entry.output == null ? null : entry.output.copy();
    }

    public ItemStack takePendingOutput() {
        WorkEntry entry = this.queue.isEmpty() ? null : this.queue.remove(0);
        if (entry == null) {
            return null;
        }
        this.normalizeSlots();
        this.onStateChanged();
        return entry.output == null ? null : entry.output.copy();
    }

    public List<ItemStack> takeQueuedOutputs() {
        if (this.queue.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemStack> outputs = new ArrayList<ItemStack>();
        for (WorkEntry entry : this.queue) {
            if (entry.output != null) {
                outputs.add(entry.output.copy());
            }
        }
        this.queue.clear();
        this.onStateChanged();
        return outputs;
    }

    public void clearRunningSlot() {
        if (!this.queue.isEmpty()) {
            this.queue.remove(0);
            this.normalizeSlots();
            this.onStateChanged();
        }
    }

    public void setRunningSlot(int slotId, int progress, int totalProgress, boolean running) {
        int normalizedTotal = Math.max(0, totalProgress);
        int normalizedProgress = Math.max(0, Math.min(progress, normalizedTotal));
        int normalizedSlot = running ? Math.max(0, slotId) : -1;
        WorkEntry current = this.peekEntry();
        if (!running) {
            if (this.queue.isEmpty()) {
                return;
            }
            this.queue.clear();
            this.onStateChanged();
            return;
        }
        if (current != null && current.slotId == normalizedSlot
            && current.progress == normalizedProgress
            && current.totalProgress == normalizedTotal) {
            return;
        }
        if (current == null) {
            this.queue.add(new WorkEntry(normalizedSlot, normalizedProgress, normalizedTotal, null));
        } else {
            current.slotId = normalizedSlot;
            current.progress = normalizedProgress;
            current.totalProgress = normalizedTotal;
        }
        this.normalizeSlots();
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
        WorkEntry current = this.peekEntry();
        tag.setBoolean(TAG_RUNNING, current != null);
        tag.setInteger(TAG_SLOT_ID, current == null ? -1 : current.slotId);
        tag.setInteger(TAG_PROGRESS, current == null ? 0 : current.progress);
        tag.setInteger(TAG_TOTAL_PROGRESS, current == null ? 0 : current.totalProgress);
        if (current != null && current.output != null) {
            NBTTagCompound legacyOutput = new NBTTagCompound();
            current.output.writeToNBT(legacyOutput);
            tag.setTag("PendingOutput", legacyOutput);
        }
        NBTTagList list = new NBTTagList();
        for (WorkEntry entry : this.queue) {
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setInteger(TAG_SLOT_ID, entry.slotId);
            entryTag.setInteger(TAG_PROGRESS, entry.progress);
            entryTag.setInteger(TAG_TOTAL_PROGRESS, entry.totalProgress);
            if (entry.output != null) {
                NBTTagCompound outputTag = new NBTTagCompound();
                entry.output.writeToNBT(outputTag);
                entryTag.setTag(TAG_OUTPUT, outputTag);
            }
            list.appendTag(entryTag);
        }
        tag.setTag(TAG_QUEUE, list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.queue.clear();
        NBTTagList list = tag.getTagList(TAG_QUEUE, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount() && this.queue.size() < this.queueCapacity(); i++) {
            WorkEntry entry = readEntry(list.getCompoundTagAt(i));
            if (entry != null) {
                this.queue.add(entry);
            }
        }
        if (this.queue.isEmpty() && tag.getBoolean(TAG_RUNNING)) {
            ItemStack output = tag.hasKey("PendingOutput")
                ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("PendingOutput"))
                : null;
            this.queue.add(
                new WorkEntry(
                    Math.max(0, tag.getInteger(TAG_SLOT_ID)),
                    Math.max(0, tag.getInteger(TAG_PROGRESS)),
                    Math.max(0, tag.getInteger(TAG_TOTAL_PROGRESS)),
                    output));
        }
        this.normalizeSlots();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        WorkEntry current = this.peekEntry();
        tag.setBoolean(TAG_RUNNING, current != null);
        tag.setInteger(TAG_SLOT_ID, current == null ? -1 : current.slotId);
        tag.setInteger(TAG_PROGRESS, current == null ? 0 : current.progress);
        tag.setInteger(TAG_TOTAL_PROGRESS, current == null ? 0 : current.totalProgress);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.queue.clear();
        if (tag.getBoolean(TAG_RUNNING)) {
            this.queue.add(
                new WorkEntry(
                    Math.max(0, tag.getInteger(TAG_SLOT_ID)),
                    Math.max(0, tag.getInteger(TAG_PROGRESS)),
                    Math.max(0, tag.getInteger(TAG_TOTAL_PROGRESS)),
                    null));
        }
        this.normalizeSlots();
    }

    private void onStateChanged() {
        this.markDirty();
        this.notifyCraftingControllerChanged();
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote || this.queue.isEmpty()) {
            return;
        }
        WorkEntry current = this.peekEntry();
        if (current == null) {
            return;
        }
        TileECOController controller = this.findCraftingController();
        if (current.progress < current.totalProgress) {
            if (controller == null) {
                return;
            }
            int bonusValue = controller.getCraftingWorkBonusValue();
            int powerMultiplier = controller.getCraftingWorkPowerMultiplier();
            double request = ECOEnergyProfile.craftingWorkPowerRequest(1, bonusValue, 1, powerMultiplier);
            double simulated = controller.extractCraftingEnergy(request, true);
            if (simulated <= 0D) {
                return;
            }
            double consumable = Math.min(request, simulated);
            int progress = ECOEnergyProfile.craftingWorkPowerFromExtracted(consumable, 1, powerMultiplier);
            if (progress <= 0) {
                return;
            }
            double extracted = controller.extractCraftingEnergy(consumable, false);
            progress = ECOEnergyProfile.craftingWorkPowerFromExtracted(extracted, 1, powerMultiplier);
            if (progress <= 0) {
                return;
            }
            int previousProgress = current.progress;
            current.progress = Math.min(current.totalProgress, current.progress + progress);
            this.onProgressChanged(previousProgress, current);
            return;
        }
        ItemStack remaining = controller == null || current.output == null ? null
            : controller.acceptCraftingOutput(current.output);
        if (remaining == null) {
            this.queue.remove(0);
            this.normalizeSlots();
            this.onStateChanged();
        } else if (current.output == null || remaining.stackSize < current.output.stackSize) {
            current.output = remaining.copy();
            this.onStateChanged();
        }
    }

    private WorkEntry peekEntry() {
        return this.queue.isEmpty() ? null : this.queue.get(0);
    }

    private void onProgressChanged(int previousProgress, WorkEntry current) {
        if (current == null || current.progress <= previousProgress) {
            return;
        }
        if (current.progress >= current.totalProgress
            || progressSyncBucket(previousProgress, current.totalProgress) != progressSyncBucket(
                current.progress,
                current.totalProgress)) {
            this.markDirty();
        }
    }

    private static int progressSyncBucket(int progress, int totalProgress) {
        if (progress <= 0 || totalProgress <= 0) {
            return 0;
        }
        if (progress >= totalProgress) {
            return PROGRESS_SYNC_BUCKETS;
        }
        long longTotal = (long) totalProgress;
        if (longTotal <= 0L) {
            return 0;
        }
        return (int) Math.min(
            PROGRESS_SYNC_BUCKETS - 1L,
            (long) progress * (long) PROGRESS_SYNC_BUCKETS / longTotal);
    }

    private void normalizeSlots() {
        for (int i = 0; i < this.queue.size(); i++) {
            WorkEntry entry = this.queue.get(i);
            entry.slotId = i;
            entry.totalProgress = Math.max(0, entry.totalProgress);
            entry.progress = Math.max(0, Math.min(entry.progress, entry.totalProgress));
        }
    }

    private static WorkEntry readEntry(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(TAG_OUTPUT)) {
            return null;
        }
        ItemStack output = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_OUTPUT));
        if (output == null) {
            return null;
        }
        int totalProgress = Math.max(0, tag.getInteger(TAG_TOTAL_PROGRESS));
        return new WorkEntry(
            Math.max(0, tag.getInteger(TAG_SLOT_ID)),
            Math.max(0, Math.min(tag.getInteger(TAG_PROGRESS), totalProgress)),
            totalProgress,
            output);
    }

    public static final class WorkSnapshot {

        public final ItemStack output;
        public final int queueSize;
        public final int queueCapacity;
        public final int progress;
        public final int totalProgress;

        private WorkSnapshot(ItemStack output, int queueSize, int queueCapacity, int progress, int totalProgress) {
            this.output = output == null ? null : output.copy();
            this.queueSize = Math.max(0, queueSize);
            this.queueCapacity = Math.max(0, queueCapacity);
            this.progress = Math.max(0, progress);
            this.totalProgress = Math.max(0, totalProgress);
        }
    }

    public WorkSnapshot snapshot() {
        WorkEntry current = this.peekEntry();
        return new WorkSnapshot(
            current == null ? null : current.output,
            this.queueSize(),
            this.queueCapacity(),
            current == null ? 0 : current.progress,
            current == null ? 0 : current.totalProgress);
    }

    private static final class WorkEntry {

        private int slotId;
        private int progress;
        private int totalProgress;
        private ItemStack output;

        private WorkEntry(int slotId, int progress, int totalProgress, ItemStack output) {
            this.slotId = Math.max(0, slotId);
            this.totalProgress = Math.max(0, totalProgress);
            this.progress = Math.max(0, Math.min(progress, this.totalProgress));
            this.output = output == null ? null : output.copy();
        }
    }
}
