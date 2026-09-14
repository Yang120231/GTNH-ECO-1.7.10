package cn.dancingsnow.neoecoae.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.TaskProgress.class, remap = false)
public interface MixinCraftingTaskProgress {

    @Accessor("value")
    long neoecoae$getValue();

    @Accessor("value")
    void neoecoae$setValue(long value);
}
