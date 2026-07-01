package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.CraftingHostStats;
import cn.dancingsnow.neoecoae.tile.TileCraftingWorker;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public final class CraftingHostSnapshot {

    private static final int MAX_WORKER_ENTRIES = 16;

    public static final CraftingHostSnapshot EMPTY = new CraftingHostSnapshot(
        false,
        false,
        "L4",
        "",
        false,
        false,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        Collections.<WorkerEntry>emptyList());

    public final boolean formed;
    public final boolean mirrored;
    public final String tier;
    public final String formationMessage;
    public final boolean overclocked;
    public final boolean activeCooling;
    public final int coolant;
    public final int maxCoolant;
    public final int maxEnergyUsage;
    public final int energyGaugeReference;
    public final int memberCount;
    public final int patternCount;
    public final int workerCount;
    public final int runningWorkerCount;
    public final int parallelCoreCount;
    public final int parallelCount;
    public final int inputCacheCount;
    public final int outputCacheCount;
    public final int queuedWorkCount;
    public final int plannerAcceptedCount;
    public final int plannerRejectedCount;
    public final int workQueueDepth;
    public final int workQueueUtilizationPercent;
    public final int workQueueCapacity;
    public final List<WorkerEntry> workerEntries;

    private CraftingHostSnapshot(boolean formed, boolean mirrored, String tier, String formationMessage,
        boolean overclocked, boolean activeCooling, int coolant, int maxCoolant, int maxEnergyUsage,
        int energyGaugeReference, int memberCount, int patternCount, int workerCount, int runningWorkerCount,
        int parallelCoreCount, int parallelCount, int inputCacheCount, int outputCacheCount, int queuedWorkCount,
        int plannerAcceptedCount, int plannerRejectedCount, int workQueueDepth, int workQueueUtilizationPercent,
        int workQueueCapacity, List<WorkerEntry> workerEntries) {
        this.formed = formed;
        this.mirrored = mirrored;
        this.tier = tier == null ? "" : tier;
        this.formationMessage = formationMessage == null ? "" : formationMessage;
        this.overclocked = overclocked;
        this.activeCooling = activeCooling;
        this.coolant = safeInt(coolant);
        this.maxCoolant = safeInt(maxCoolant);
        this.maxEnergyUsage = safeInt(maxEnergyUsage);
        this.energyGaugeReference = Math.max(1, safeInt(energyGaugeReference));
        this.memberCount = safeInt(memberCount);
        this.patternCount = safeInt(patternCount);
        this.workerCount = safeInt(workerCount);
        this.runningWorkerCount = safeInt(runningWorkerCount);
        this.parallelCoreCount = safeInt(parallelCoreCount);
        this.parallelCount = safeInt(parallelCount);
        this.inputCacheCount = safeInt(inputCacheCount);
        this.outputCacheCount = safeInt(outputCacheCount);
        this.queuedWorkCount = safeInt(queuedWorkCount);
        this.plannerAcceptedCount = safeInt(plannerAcceptedCount);
        this.plannerRejectedCount = safeInt(plannerRejectedCount);
        this.workQueueDepth = safeInt(workQueueDepth);
        this.workQueueUtilizationPercent = Math.max(0, Math.min(100, workQueueUtilizationPercent));
        this.workQueueCapacity = safeInt(workQueueCapacity);
        this.workerEntries = copyWorkers(workerEntries);
    }

    public static CraftingHostSnapshot create(TileECOController controller) {
        if (controller == null || controller.getSubsystem() != ECOControllerSubsystem.CRAFTING) {
            return EMPTY;
        }
        CraftingHostStats stats = controller.getCraftingHostStats();
        List<ECOFormationBlockPos> formedMembers = controller.getFormedMemberBlocks();
        int workQueueDepth = stats.queuedWorkCount;
        int workQueueCapacity = safeInt((long) stats.workerCount * TileCraftingWorker.BASE_QUEUE_CAPACITY);
        int utilization = ratioPercent(workQueueDepth, workQueueCapacity);
        return new CraftingHostSnapshot(
            controller.isFormed(),
            controller.isMirrored(),
            controller.getTier()
                .name(),
            controller.getLastFormationMessage(),
            controller.isCraftingOverclocked(),
            controller.isCraftingActiveCooling(),
            controller.getCraftingCoolant(),
            controller.getCraftingMaxCoolant(),
            controller.getCraftingMaxEnergyUsage(),
            TileECOController.CRAFTING_ENERGY_GAUGE_REFERENCE,
            formedMembers.size(),
            stats.patternCount,
            stats.workerCount,
            stats.runningWorkerCount,
            stats.parallelCoreCount,
            stats.parallelCount,
            stats.inputCachedItems,
            stats.outputCachedItems,
            stats.queuedWorkCount,
            controller.getCraftingPlannerAcceptedCount(),
            controller.getCraftingPlannerRejectedCount(),
            workQueueDepth,
            utilization,
            workQueueCapacity,
            workerEntries(controller, formedMembers));
    }

    public void write(ByteBuf buffer) {
        buffer.writeBoolean(this.formed);
        buffer.writeBoolean(this.mirrored);
        writeString(buffer, this.tier);
        writeString(buffer, this.formationMessage);
        buffer.writeBoolean(this.overclocked);
        buffer.writeBoolean(this.activeCooling);
        buffer.writeInt(this.coolant);
        buffer.writeInt(this.maxCoolant);
        buffer.writeInt(this.maxEnergyUsage);
        buffer.writeInt(this.energyGaugeReference);
        buffer.writeInt(this.memberCount);
        buffer.writeInt(this.patternCount);
        buffer.writeInt(this.workerCount);
        buffer.writeInt(this.runningWorkerCount);
        buffer.writeInt(this.parallelCoreCount);
        buffer.writeInt(this.parallelCount);
        buffer.writeInt(this.inputCacheCount);
        buffer.writeInt(this.outputCacheCount);
        buffer.writeInt(this.queuedWorkCount);
        buffer.writeInt(this.plannerAcceptedCount);
        buffer.writeInt(this.plannerRejectedCount);
        buffer.writeInt(this.workQueueDepth);
        buffer.writeInt(this.workQueueUtilizationPercent);
        buffer.writeInt(this.workQueueCapacity);
        int workerCount = Math.min(MAX_WORKER_ENTRIES, this.workerEntries.size());
        buffer.writeByte(workerCount);
        for (int i = 0; i < workerCount; i++) {
            this.workerEntries.get(i)
                .write(buffer);
        }
    }

    public static CraftingHostSnapshot read(ByteBuf buffer) {
        boolean formed = buffer.readBoolean();
        boolean mirrored = buffer.readBoolean();
        String tier = readString(buffer);
        String formationMessage = readString(buffer);
        boolean overclocked = buffer.readBoolean();
        boolean activeCooling = buffer.readBoolean();
        int coolant = buffer.readInt();
        int maxCoolant = buffer.readInt();
        int maxEnergyUsage = buffer.readInt();
        int energyGaugeReference = buffer.readInt();
        int memberCount = buffer.readInt();
        int patternCount = buffer.readInt();
        int workerCount = buffer.readInt();
        int runningWorkerCount = buffer.readInt();
        int parallelCoreCount = buffer.readInt();
        int parallelCount = buffer.readInt();
        int inputCacheCount = buffer.readInt();
        int outputCacheCount = buffer.readInt();
        int queuedWorkCount = buffer.readInt();
        int plannerAcceptedCount = buffer.readInt();
        int plannerRejectedCount = buffer.readInt();
        int workQueueDepth = buffer.readInt();
        int workQueueUtilizationPercent = buffer.readInt();
        int workQueueCapacity = buffer.readInt();
        int syncedWorkers = buffer.readUnsignedByte();
        List<WorkerEntry> workers = new ArrayList<WorkerEntry>();
        for (int i = 0; i < syncedWorkers; i++) {
            workers.add(WorkerEntry.read(buffer));
        }
        return new CraftingHostSnapshot(
            formed,
            mirrored,
            tier,
            formationMessage,
            overclocked,
            activeCooling,
            coolant,
            maxCoolant,
            maxEnergyUsage,
            energyGaugeReference,
            memberCount,
            patternCount,
            workerCount,
            runningWorkerCount,
            parallelCoreCount,
            parallelCount,
            inputCacheCount,
            outputCacheCount,
            queuedWorkCount,
            plannerAcceptedCount,
            plannerRejectedCount,
            workQueueDepth,
            workQueueUtilizationPercent,
            workQueueCapacity,
            workers);
    }

    private static int safeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int ratioPercent(long value, long max) {
        if (value <= 0L || max <= 0L) {
            return 0;
        }
        return (int) Math.max(1L, Math.min(100L, value * 100L / max));
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 512);
        buffer.writeShort(length);
        buffer.writeBytes(bytes, 0, length);
    }

    private static String readString(ByteBuf buffer) {
        int length = Math.min(Math.max(0, buffer.readUnsignedShort()), 512);
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<WorkerEntry> workerEntries(TileECOController controller, List<ECOFormationBlockPos> members) {
        List<WorkerEntry> entries = new ArrayList<WorkerEntry>();
        World world = controller.getWorldObj();
        if (world == null || !controller.isFormed()) {
            return entries;
        }
        for (int i = 0; i < members.size() && entries.size() < MAX_WORKER_ENTRIES; i++) {
            ECOFormationBlockPos pos = members.get(i);
            TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileCraftingWorker) {
                entries.add(WorkerEntry.from((TileCraftingWorker) tile, entries.size()));
            }
        }
        return entries;
    }

    private static List<WorkerEntry> copyWorkers(List<WorkerEntry> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkerEntry> copy = new ArrayList<WorkerEntry>();
        for (WorkerEntry entry : source) {
            if (entry != null) {
                copy.add(entry.copy());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class WorkerEntry {

        public final int index;
        public final ItemStack outputStack;
        public final String outputName;
        public final int queueSize;
        public final int queueCapacity;
        public final int progress;
        public final int totalProgress;

        private WorkerEntry(int index, ItemStack outputStack, int queueSize, int queueCapacity, int progress,
            int totalProgress) {
            this.index = safeInt(index);
            this.outputStack = outputStack == null ? null : outputStack.copy();
            this.outputName = outputStack == null ? "" : outputStack.getDisplayName();
            this.queueSize = safeInt(queueSize);
            this.queueCapacity = safeInt(queueCapacity);
            this.progress = safeInt(progress);
            this.totalProgress = safeInt(totalProgress);
        }

        private static WorkerEntry from(TileCraftingWorker worker, int index) {
            TileCraftingWorker.WorkSnapshot snapshot = worker.snapshot();
            return new WorkerEntry(
                index,
                snapshot.output,
                snapshot.queueSize,
                snapshot.queueCapacity,
                snapshot.progress,
                snapshot.totalProgress);
        }

        private void write(ByteBuf buffer) {
            buffer.writeInt(this.index);
            ByteBufUtils.writeItemStack(buffer, this.outputStack);
            buffer.writeInt(this.queueSize);
            buffer.writeInt(this.queueCapacity);
            buffer.writeInt(this.progress);
            buffer.writeInt(this.totalProgress);
        }

        private static WorkerEntry read(ByteBuf buffer) {
            return new WorkerEntry(
                buffer.readInt(),
                ByteBufUtils.readItemStack(buffer),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt());
        }

        private WorkerEntry copy() {
            return new WorkerEntry(
                this.index,
                this.outputStack,
                this.queueSize,
                this.queueCapacity,
                this.progress,
                this.totalProgress);
        }
    }
}
