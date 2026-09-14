package cn.dancingsnow.neoecoae.mixin;

import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOCraftingJob;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOExternalCpuBatch;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchCoordinator;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchTransaction;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingExecutionContext;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingOwnershipRegistry;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionHost;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.tile.TileECOController;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUExecution
    implements ECOExecutionHost, ECOCraftingBatchCoordinator, ECOExternalCpuBatch.Accounting {

    @Shadow
    @Final
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks;
    @Shadow
    protected boolean suspended;
    @Unique
    private ECOExecutionRuntime neoecoae$execution;
    @Shadow
    protected IItemList<IAEStack<?>> waitingFor;
    @Shadow
    protected int remainingOperations;

    @Shadow
    protected abstract void postChange(IAEStack<?> stack, BaseActionSource source);

    @Shadow
    protected abstract void postCraftingStatusChange(IAEStack<?> stack);

    @Unique
    private double neoecoae$energyDebt;

    @Override
    public ECOCraftingBatchTransaction prepareBatch(ICraftingPatternDetails pattern, InventoryCrafting table,
        TileECOController controller) {
        try {
            return ECOExternalCpuBatch.prepare(
                (CraftingCPUCluster) (Object) this,
                (MixinCraftingTaskProgress) tasks.get(pattern),
                pattern,
                table,
                controller,
                this);
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.warn("External CPU fastpath preparation failed; using ordinary dispatch", failure);
            return null;
        }
    }

    @Override
    public void accepted(ICraftingPatternDetails details, int extra, double debt) {
        neoecoae$energyDebt += debt;
        for (IAEStack<?> output : details.getCondensedAEOutputs()) {
            IAEStack<?> added = output.copy()
                .setStackSize(Math.multiplyExact(output.getStackSize(), extra));
            waitingFor.add(added);
            postChange(added, ((CraftingCPUCluster) (Object) this).getActionSource());
            postCraftingStatusChange(added);
        }
        remainingOperations -= extra;
        if (neoecoae$energyDebt > 0.01) suspended = true;
        ((CraftingCPUCluster) (Object) this).markDirty();
    }

    @Override
    public void recordSlowCraftAccepted() {}

    @Override
    public boolean isBatchDispatchSuspended() {
        return suspended || neoecoae$energyDebt > 0.01;
    }

    @Override
    public void handleBatchFailure(RuntimeException failure) {
        suspended = true;
        ((CraftingCPUCluster) (Object) this).markDirty();
        NeoECOAE.LOG.error("External CPU batch failed; CPU suspended", failure);
    }

    @Override
    public ECOExecutionRuntime neoecoae$getExecution() {
        return neoecoae$execution;
    }

    @Override
    public void neoecoae$setExecution(ECOExecutionRuntime execution) {
        neoecoae$execution = execution;
    }

    @Redirect(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean neoecoae$dispatch(ICraftingMedium medium, ICraftingPatternDetails pattern,
        InventoryCrafting table) {
        if (neoecoae$execution != null && neoecoae$execution.allowance(pattern) == 0) return false;
        MixinCraftingTaskProgress progress = (MixinCraftingTaskProgress) tasks.get(pattern);
        long before = progress == null ? 0 : progress.neoecoae$getValue();
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        ICraftingLink link = cpu.getLastCraftingLink();
        if (link != null) ECOCraftingOwnershipRegistry.heartbeat(link.getCraftingID(), cpu);
        boolean accepted;
        try (ECOCraftingExecutionContext.Scope ignored = ECOCraftingExecutionContext
            .enter(link == null ? null : link.getCraftingID(), (ECOCraftingBatchCoordinator) (Object) this)) {
            accepted = medium.pushPattern(pattern, table);
        }
        if (accepted && neoecoae$execution != null) {
            long extras = progress == null ? 0 : before - progress.neoecoae$getValue();
            neoecoae$execution.dispatched(pattern, Math.addExact(1, extras));
        }
        return accepted;
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$preventMerge(IGrid grid, ICraftingJob job, BaseActionSource source,
        ICraftingRequester requester, CallbackInfoReturnable<ICraftingLink> cir) {
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        if (cpu.isBusy() && (neoecoae$execution != null || job instanceof ECOCraftingJob)) cir.setReturnValue(null);
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void neoecoae$submissionResult(IGrid grid, ICraftingJob job, BaseActionSource source,
        ICraftingRequester requester, CallbackInfoReturnable<ICraftingLink> cir) {
        if (cir.getReturnValue() == null && !((CraftingCPUCluster) (Object) this).isBusy()) neoecoae$execution = null;
    }

    @Inject(method = { "cancel", "destroy" }, at = @At("HEAD"))
    private void neoecoae$clearExecution(CallbackInfo ci) {
        ICraftingLink link = ((CraftingCPUCluster) (Object) this).getLastCraftingLink();
        if (link != null) ECOCraftingOwnershipRegistry.cancelAndRecover(link.getCraftingID());
        neoecoae$execution = null;
    }

    @Inject(method = "completeJob", at = @At("RETURN"))
    private void neoecoae$completeExecution(CallbackInfo ci) {
        if (!((CraftingCPUCluster) (Object) this).isBusy()) neoecoae$execution = null;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void neoecoae$writeExecution(NBTTagCompound data, CallbackInfo ci) {
        data.setDouble("EcoBatchEnergyDebt", neoecoae$energyDebt);
        if (neoecoae$execution != null) data.setTag("EcoExecution", neoecoae$execution.write());
        else data.removeTag("EcoExecution");
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void neoecoae$readExecution(NBTTagCompound data, CallbackInfo ci) {
        neoecoae$energyDebt = Math.max(0, data.getDouble("EcoBatchEnergyDebt"));
        if (!Double.isFinite(neoecoae$energyDebt)) {
            neoecoae$energyDebt = Double.MAX_VALUE;
            suspended = true;
        }
        neoecoae$execution = null;
        if (!data.hasKey("EcoExecution", 10)) return;
        try {
            neoecoae$execution = ECOExecutionRuntime.read(data.getCompoundTag("EcoExecution"));
        } catch (RuntimeException failure) {
            suspended = true;
            NeoECOAE.LOG.error("Invalid ECO execution schedule; CPU suspended to preserve its inventory", failure);
        }
    }

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"), cancellable = true)
    private void neoecoae$settleDebt(IGrid grid, IEnergyGrid energy, appeng.me.cache.CraftingGridCache crafting,
        CallbackInfo ci) {
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        if (cpu.getLastCraftingLink() != null) ECOCraftingOwnershipRegistry.heartbeat(
            cpu.getLastCraftingLink()
                .getCraftingID(),
            cpu);
        if (neoecoae$energyDebt <= 0.01) return;
        double paid = energy.extractAEPower(neoecoae$energyDebt, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (Double.isFinite(paid) && paid > 0) neoecoae$energyDebt = Math.max(0, neoecoae$energyDebt - paid);
        ((CraftingCPUCluster) (Object) this).markDirty();
        if (neoecoae$energyDebt > 0.01) ci.cancel();
    }
}
