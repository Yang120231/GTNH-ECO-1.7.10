package cn.dancingsnow.neoecoae.gui.computation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public final class ComputationHostSnapshot {

    private static final int MAX_TASKS = 32;

    public static final ComputationHostSnapshot EMPTY = new ComputationHostSnapshot(
        false,
        false,
        "L4",
        "",
        0,
        0,
        0,
        0,
        0L,
        0L,
        ComputationCpuSelectionMode.ANY,
        Collections.<TaskEntry>emptyList());

    public final boolean formed;
    public final boolean active;
    public final String tier;
    public final String formationMessage;
    public final int usedThreads;
    public final int totalThreads;
    public final int parallelCount;
    public final int parallelCores;
    public final long usedComputationBytes;
    public final long totalBytes;
    public final ComputationCpuSelectionMode cpuSelectionMode;
    public final List<TaskEntry> tasks;

    private ComputationHostSnapshot(boolean formed, boolean active, String tier, String formationMessage,
        int usedThreads, int totalThreads, int parallelCount, int parallelCores, long usedComputationBytes,
        long totalBytes, ComputationCpuSelectionMode cpuSelectionMode, List<TaskEntry> tasks) {
        this.formed = formed;
        this.active = active;
        this.tier = tier == null ? "" : tier;
        this.formationMessage = formationMessage == null ? "" : formationMessage;
        this.usedThreads = Math.max(0, usedThreads);
        this.totalThreads = Math.max(0, totalThreads);
        this.parallelCount = Math.max(0, parallelCount);
        this.parallelCores = Math.max(0, parallelCores);
        this.usedComputationBytes = Math.max(0L, usedComputationBytes);
        this.totalBytes = Math.max(0L, totalBytes);
        this.cpuSelectionMode = cpuSelectionMode == null ? ComputationCpuSelectionMode.ANY : cpuSelectionMode;
        this.tasks = Collections.unmodifiableList(new ArrayList<TaskEntry>(tasks));
    }

    public static ComputationHostSnapshot create(TileECOController controller) {
        if (controller == null) {
            return EMPTY;
        }
        ComputationHostStats stats = controller.getComputationHostStats();
        return new ComputationHostSnapshot(
            controller.isFormed(),
            controller.isComputationHostActive(),
            controller.getTier()
                .name(),
            controller.getLastFormationMessage(),
            saturatingInt(controller.getUsedComputationThreads()),
            stats.totalThreads,
            stats.parallelCount,
            stats.parallelCores,
            controller.getUsedComputationStorageBytes(),
            stats.totalBytes,
            controller.getComputationCpuSelectionMode(),
            taskEntriesFrom(controller.getComputationTaskEntries()));
    }

    public void write(ByteBuf buf) {
        buf.writeBoolean(this.formed);
        buf.writeBoolean(this.active);
        writeString(buf, this.tier);
        writeString(buf, this.formationMessage);
        buf.writeInt(this.usedThreads);
        buf.writeInt(this.totalThreads);
        buf.writeInt(this.parallelCount);
        buf.writeInt(this.parallelCores);
        buf.writeLong(this.usedComputationBytes);
        buf.writeLong(this.totalBytes);
        buf.writeInt(this.cpuSelectionMode.ordinal());
        int taskCount = Math.min(this.tasks.size(), MAX_TASKS);
        buf.writeInt(taskCount);
        for (int i = 0; i < taskCount; i++) {
            this.tasks.get(i)
                .write(buf);
        }
    }

    public static ComputationHostSnapshot read(ByteBuf buf) {
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        String tier = readString(buf);
        String formationMessage = readString(buf);
        int usedThreads = buf.readInt();
        int totalThreads = buf.readInt();
        int parallelCount = buf.readInt();
        int parallelCores = buf.readInt();
        long usedComputationBytes = buf.readLong();
        long totalBytes = buf.readLong();
        ComputationCpuSelectionMode cpuSelectionMode = ComputationCpuSelectionMode.fromOrdinal(buf.readInt());
        int taskCount = Math.min(Math.max(0, buf.readInt()), MAX_TASKS);
        List<TaskEntry> tasks = new ArrayList<TaskEntry>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            tasks.add(TaskEntry.read(buf));
        }
        return new ComputationHostSnapshot(
            formed,
            active,
            tier,
            formationMessage,
            usedThreads,
            totalThreads,
            parallelCount,
            parallelCores,
            usedComputationBytes,
            totalBytes,
            cpuSelectionMode,
            tasks);
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 256);
        buf.writeShort(length);
        buf.writeBytes(bytes, 0, length);
    }

    private static String readString(ByteBuf buf) {
        int length = Math.min(Math.max(0, buf.readUnsignedShort()), 256);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int saturatingInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static List<TaskEntry> taskEntriesFrom(List<ComputationTaskInfo> taskInfos) {
        List<TaskEntry> entries = new ArrayList<TaskEntry>();
        if (taskInfos == null) {
            return entries;
        }
        for (ComputationTaskInfo taskInfo : taskInfos) {
            if (taskInfo != null) {
                entries.add(
                    new TaskEntry(
                        taskInfo.outputStack,
                        taskInfo.outputAmount,
                        taskInfo.elapsedNanos,
                        taskInfo.cpuName,
                        taskInfo.cpuSerial,
                        taskInfo.cpuStorage,
                        taskInfo.coProcessors,
                        taskInfo.cpuSelectionMode,
                        taskInfo.status));
            }
        }
        return entries;
    }

    public static final class TaskEntry {

        public final String outputName;
        public final ItemStack outputStack;
        public final long outputAmount;
        public final long elapsedNanos;
        public final String cpuName;
        public final int cpuSerial;
        public final long cpuStorage;
        public final int coProcessors;
        public final ComputationCpuSelectionMode cpuSelectionMode;
        public final ComputationTaskInfo.Status status;

        public TaskEntry(ItemStack outputStack, long outputAmount, long elapsedNanos, String cpuName, int cpuSerial,
            long cpuStorage, int coProcessors, ComputationCpuSelectionMode cpuSelectionMode,
            ComputationTaskInfo.Status status) {
            this.outputStack = outputStack == null ? null : outputStack.copy();
            this.outputName = this.outputStack == null ? "" : this.outputStack.getDisplayName();
            this.outputAmount = Math.max(0L, outputAmount);
            this.elapsedNanos = Math.max(0L, elapsedNanos);
            this.cpuName = cpuName == null ? "" : cpuName;
            this.cpuSerial = Math.max(0, cpuSerial);
            this.cpuStorage = Math.max(0L, cpuStorage);
            this.coProcessors = Math.max(0, coProcessors);
            this.cpuSelectionMode = cpuSelectionMode == null ? ComputationCpuSelectionMode.ANY : cpuSelectionMode;
            this.status = status == null ? ComputationTaskInfo.Status.RUNNING : status;
        }

        private void write(ByteBuf buf) {
            ByteBufUtils.writeItemStack(buf, this.outputStack);
            buf.writeLong(this.outputAmount);
            buf.writeLong(this.elapsedNanos);
            writeString(buf, this.cpuName);
            buf.writeInt(this.cpuSerial);
            buf.writeLong(this.cpuStorage);
            buf.writeInt(this.coProcessors);
            buf.writeInt(this.cpuSelectionMode.ordinal());
            buf.writeInt(this.status.ordinal());
        }

        private static TaskEntry read(ByteBuf buf) {
            ItemStack output = ByteBufUtils.readItemStack(buf);
            long outputAmount = buf.readLong();
            long elapsedNanos = buf.readLong();
            String cpuName = readString(buf);
            int cpuSerial = buf.readInt();
            long cpuStorage = buf.readLong();
            int coProcessors = buf.readInt();
            ComputationCpuSelectionMode mode = ComputationCpuSelectionMode.fromOrdinal(buf.readInt());
            ComputationTaskInfo.Status[] statuses = ComputationTaskInfo.Status.values();
            int statusOrdinal = buf.readInt();
            ComputationTaskInfo.Status status = statusOrdinal < 0 || statusOrdinal >= statuses.length
                ? ComputationTaskInfo.Status.RUNNING
                : statuses[statusOrdinal];
            return new TaskEntry(
                output,
                outputAmount,
                elapsedNanos,
                cpuName,
                cpuSerial,
                cpuStorage,
                coProcessors,
                mode,
                status);
        }
    }
}
