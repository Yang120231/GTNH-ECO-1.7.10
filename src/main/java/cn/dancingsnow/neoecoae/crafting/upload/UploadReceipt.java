package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/** Proof of one completed upload. Undo touches only this physical slot and this exact pattern. */
public final class UploadReceipt {

    public final Object sourceIdentity;
    public final PatternSlotAccess.SlotRef target;
    public final ItemStack pattern;
    public final PatternUploadTarget.Kind kind;
    public final String targetName;
    public final int dimension, x, y, z;

    public UploadReceipt(Object sourceIdentity, PatternSlotAccess.SlotRef target, ItemStack pattern,
        PatternUploadTarget destination) {
        this.sourceIdentity = sourceIdentity;
        this.target = target;
        this.pattern = PatternStacks.copy(pattern, 1);
        this.kind = destination.kind();
        this.targetName = destination.name();
        this.dimension = destination.dimension();
        this.x = destination.x();
        this.y = destination.y();
        this.z = destination.z();
    }
}
