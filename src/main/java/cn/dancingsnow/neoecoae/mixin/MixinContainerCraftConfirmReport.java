package cn.dancingsnow.neoecoae.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOCraftingJob;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOPlanningReportAccess;

@Mixin(value = ContainerCraftConfirm.class, remap = false)
public abstract class MixinContainerCraftConfirmReport implements ECOPlanningReportAccess {

    @Shadow
    private ICraftingJob result;

    @GuiSync(19710)
    public String neoecoae$planningReport = "";
    private ICraftingJob neoecoae$reportedJob;

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"), remap = true)
    private void neoecoae$updateReport(CallbackInfo ci) {
        if (((ContainerCraftConfirm) (Object) this).getWorld().isRemote || result == neoecoae$reportedJob) return;
        neoecoae$reportedJob = result;
        neoecoae$planningReport = result instanceof ECOCraftingJob ? ((ECOCraftingJob) result).createPlanningReport()
            : "";
    }

    @Override
    public String neoecoae$getPlanningReport() {
        return neoecoae$planningReport;
    }
}
