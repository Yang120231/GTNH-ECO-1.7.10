package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeCpuTargets;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpuSelection;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;

/** Include live ECO CPU identities in the exact set used for scheduling, link recovery and output routing. */
@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class CraftingCpuPublicationMixin {

    @Shadow
    @Final
    protected IGrid grid;
    @Shadow
    @Final
    protected Set<CraftingCPUCluster> craftingCPUClusters;
    @Shadow
    protected boolean updateList;

    /** Reused per tick so releasing finished ECO CPUs allocates nothing in steady state. */
    @Unique
    private final Set<Object> neoecoae$owners = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @Shadow
    public abstract void addLink(appeng.crafting.CraftingLink link);

    @Inject(method = NativeCpuTargets.PUBLISH, at = @At("TAIL"), require = 1)
    private void neoecoae$publish(CallbackInfo ci) {
        Set<cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster> seen = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        for (Class<? extends IGridHost> type : grid.getMachinesClasses()) {
            if (!ECOComputationSystemBlockEntity.class.isAssignableFrom(type)) continue;
            for (IGridNode node : grid.getMachines(type)) {
                ECOComputationSystemBlockEntity host = (ECOComputationSystemBlockEntity) node.getMachine();
                var cluster = host.getCluster();
                if (cluster == null || cluster.isDestroyed() || !seen.add(cluster)) continue;
                cluster.pruneInactiveCPUs();
                // Same rule as 1.21's getCpus: the representative lists its placeholder while a thread is free.
                if (cluster.shouldPublishPlaceholder())
                    craftingCPUClusters.add((CraftingCPUCluster) cluster.nativeCPU(cluster.getFakeCPU()));
                for (var cpu : cluster.getActiveCPUs()) {
                    CraftingCPUCluster nativeCpu = (CraftingCPUCluster) cluster.nativeCPU(cpu);
                    craftingCPUClusters.add(nativeCpu);
                    if (nativeCpu.getLastCraftingLink() != null)
                        addLink((appeng.crafting.CraftingLink) nativeCpu.getLastCraftingLink());
                }
            }
        }
    }

    @Inject(method = { NativeCpuTargets.ADD, NativeCpuTargets.REMOVE }, at = @At("HEAD"), require = 1)
    private void neoecoae$topology(IGridNode node, IGridHost machine, CallbackInfo ci) {
        if (machine instanceof ECOComputationSystemBlockEntity) updateList = true;
    }

    @Group(name = "neoecoae$planningEntry", min = 1, max = 1)
    // @Desc keeps optional overloads exact even when development refmap remapping enables permissive matching.
    @Inject(
        target = @Desc(
            value = "beginCraftingJob",
            ret = java.util.concurrent.Future.class,
            args = { net.minecraft.world.World.class, IGrid.class,
                appeng.api.networking.security.BaseActionSource.class, appeng.api.storage.data.IAEStack.class,
                appeng.api.config.CraftingMode.class, appeng.api.networking.crafting.ICraftingCallback.class }),
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void neoecoae$plan(net.minecraft.world.World world, IGrid requestedGrid,
        appeng.api.networking.security.BaseActionSource source, appeng.api.storage.data.IAEStack<?> output,
        appeng.api.config.CraftingMode mode, appeng.api.networking.crafting.ICraftingCallback callback,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingJob>> cir) {
        neoecoae$beginPlan(world, requestedGrid, source, output, mode, callback, cir);
    }

    @Group(name = "neoecoae$planningEntry", min = 1, max = 1)
    @Inject(
        target = @Desc(
            value = "beginCraftingJob",
            ret = java.util.concurrent.Future.class,
            args = { net.minecraft.world.World.class, IGrid.class,
                appeng.api.networking.security.BaseActionSource.class, appeng.api.storage.data.IAEStack.class,
                appeng.api.config.CraftingMode.class, boolean.class,
                appeng.api.networking.crafting.ICraftingCallback.class }),
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void neoecoae$planWithLiteMode(net.minecraft.world.World world, IGrid requestedGrid,
        appeng.api.networking.security.BaseActionSource source, appeng.api.storage.data.IAEStack<?> output,
        appeng.api.config.CraftingMode mode, boolean liteMode,
        appeng.api.networking.crafting.ICraftingCallback callback,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingJob>> cir) {
        // Lite only picks AE2's own CraftingJobFast, and every newer overload funnels here with the client's lite
        // default; deferring to it would bypass ECO for all requests, so the ECO fast-planner switch takes precedence.
        neoecoae$beginPlan(world, requestedGrid, source, output, mode, callback, cir);
    }

    @Unique
    private void neoecoae$beginPlan(net.minecraft.world.World world, IGrid requestedGrid,
        appeng.api.networking.security.BaseActionSource source, appeng.api.storage.data.IAEStack<?> output,
        appeng.api.config.CraftingMode mode, appeng.api.networking.crafting.ICraftingCallback callback,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingJob>> cir) {
        if (mode != appeng.api.config.CraftingMode.STANDARD) return;
        var future = cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning
            .begin(world, requestedGrid, source, output, callback);
        if (future != null) cir.setReturnValue(future);
    }

    @Inject(method = NativeCpuTargets.SUBMIT, at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$submit(appeng.api.networking.crafting.ICraftingJob job,
        appeng.api.networking.crafting.ICraftingRequester requester, appeng.api.networking.crafting.ICraftingCPU target,
        boolean prioritizePower, appeng.api.networking.security.BaseActionSource source, boolean follow,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<appeng.api.networking.crafting.ICraftingLink> cir) {
        if (!(job instanceof NativeECOCraftingJob)) return;
        NativeECOCraftingJob ecoJob = (NativeECOCraftingJob) job;
        // A native-safe plan explicitly sent to a native AE2 CPU runs through AE2's own path unchanged.
        if (!job.isSimulation() && NativeECOCpuSelection.deferToNative(ecoJob, target, grid)) return;
        cir.setReturnValue(
            NativeECOCpuSelection
                .submit(craftingCPUClusters, grid, ecoJob, requester, target, prioritizePower, source, follow));
    }

    /** Mirrors 1.21: after AE2 advanced every CPU, release ECO CPUs whose job ended during this tick. */
    @Inject(method = NativeCpuTargets.UPDATE_TICK, at = @At("TAIL"), require = 1)
    private void neoecoae$releaseFinished(CallbackInfo ci) {
        if (NativeECOCpuSelection.releaseFinished(craftingCPUClusters, neoecoae$owners)) updateList = true;
    }
}
