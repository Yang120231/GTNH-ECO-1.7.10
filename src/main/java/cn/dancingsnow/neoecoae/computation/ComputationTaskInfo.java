package cn.dancingsnow.neoecoae.computation;

import net.minecraft.item.ItemStack;

public final class ComputationTaskInfo {

    public final String outputName;
    public final ItemStack outputStack;
    public final long elapsedNanos;

    public ComputationTaskInfo(ItemStack outputStack, long elapsedNanos) {
        this.outputStack = outputStack == null ? null : outputStack.copy();
        this.outputName = this.outputStack == null ? "" : this.outputStack.getDisplayName();
        this.elapsedNanos = Math.max(0L, elapsedNanos);
    }
}
