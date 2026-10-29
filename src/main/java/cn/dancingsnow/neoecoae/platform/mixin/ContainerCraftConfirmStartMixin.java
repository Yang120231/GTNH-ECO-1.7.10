package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmTracker;

/**
 * AE2 auto-start submits from {@code detectAndSendChanges}, outside the ECO start packet. Guard that submission
 * the same way: a null link keeps AE2 on the confirmation screen, and the failure is reported there.
 */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public abstract class ContainerCraftConfirmStartMixin {

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
