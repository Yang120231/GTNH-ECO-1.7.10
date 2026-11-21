package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmTracker;
import cn.dancingsnow.neoecoae.platform.mixin.ContainerCraftConfirmAccessor;

/**
 * AE2 auto-start submits from {@code detectAndSendChanges}, outside the ECO start packet. Guard that submission
 * the same way: a null link keeps AE2 on the confirmation screen, and the failure is reported there.
 */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public abstract class ContainerCraftConfirmStartMixin {

    @Inject(method = "pushCraftedItemToPins", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoecoae$omitTemporaryOrderPin(CallbackInfo ci) {
        var output = ((ContainerCraftConfirm) (Object) this).getItemToCraft();
        if (output != null && cn.dancingsnow.neoecoae.platform.ae2.crafting.TemporaryCraftingOrder
            .isOrder(cn.dancingsnow.neoecoae.platform.ae2.key.AEKey.fromNative(output))) ci.cancel();
    }

    /** AE2's default CPU choice skips a CPU that cannot take the ECO plan, so automatic selection finds one. */
    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$rejectIncompatibleCpu(CraftingCPUStatus status, CallbackInfoReturnable<Boolean> cir) {
        var cpu = status.getServerCluster();
        if (cpu != null && ECOCraftConfirmTracker
            .nativeIncompatible(cpu, ((ContainerCraftConfirmAccessor) (Object) this).neoecoae$getResult())) cir
                .setReturnValue(false);
    }

    @Redirect(
        method = "startJob(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingGrid;submitJob(Lappeng/api/networking/crafting/ICraftingJob;Lappeng/api/networking/crafting/ICraftingRequester;Lappeng/api/networking/crafting/ICraftingCPU;ZLappeng/api/networking/security/BaseActionSource;Z)Lappeng/api/networking/crafting/ICraftingLink;"),
        require = 0)
    private ICraftingLink neoecoae$guardSubmit(ICraftingGrid grid, ICraftingJob job, ICraftingRequester requester,
        ICraftingCPU target, boolean prioritizePower, BaseActionSource source, boolean followCraft) {
        try {
            return grid.submitJob(job, requester, target, prioritizePower, source, followCraft);
        } catch (RuntimeException | LinkageError failure) {
            ECOCraftConfirmTracker.autoStartFailed((ContainerCraftConfirm) (Object) this, failure);
            return null;
        }
    }
}
