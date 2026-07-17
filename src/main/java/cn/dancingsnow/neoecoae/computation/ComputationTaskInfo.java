package cn.dancingsnow.neoecoae.computation;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;

public final class ComputationTaskInfo {

    public enum Status {
        RUNNING,
        WAITING
    }

    public final String outputName;
    public final ItemStack outputStack;
    public final long outputAmount;
    public final long elapsedNanos;
    public final String cpuName;
    public final int cpuSerial;
    public final long cpuStorage;
    public final int coProcessors;
    public final ComputationCpuSelectionMode cpuSelectionMode;
    public final Status status;

    public ComputationTaskInfo(ItemStack outputStack, long outputAmount, long elapsedNanos, String cpuName,
        int cpuSerial, long cpuStorage, int coProcessors, ComputationCpuSelectionMode cpuSelectionMode, Status status) {
        this.outputStack = outputStack == null ? null : outputStack.copy();
        this.outputName = this.outputStack == null ? "" : this.outputStack.getDisplayName();
        this.outputAmount = Math.max(0L, outputAmount);
        this.elapsedNanos = Math.max(0L, elapsedNanos);
        this.cpuName = cpuName == null ? "" : cpuName;
        this.cpuSerial = Math.max(0, cpuSerial);
        this.cpuStorage = Math.max(0L, cpuStorage);
        this.coProcessors = Math.max(0, coProcessors);
        this.cpuSelectionMode = cpuSelectionMode == null ? ComputationCpuSelectionMode.ANY : cpuSelectionMode;
        this.status = status == null ? Status.RUNNING : status;
    }
}
