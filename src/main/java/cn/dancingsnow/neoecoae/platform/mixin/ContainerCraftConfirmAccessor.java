package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.implementations.ContainerCraftConfirm;

/**
 * Read access to the confirmation container's planning future and result. Both fields exist unchanged in the pinned
 * rv3-beta-990 jar and the rv3-beta-1080 release, so the ECO report only observes the native lifecycle.
 */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public interface ContainerCraftConfirmAccessor {

    @Accessor("job")
    Future<ICraftingJob> neoecoae$getJob();

    @Accessor("result")
    ICraftingJob neoecoae$getResult();

    @Accessor("result")
    void neoecoae$setResult(ICraftingJob result);
}
