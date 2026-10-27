package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeCpuTargets;

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
                if (cluster.isNetworkRepresentative() && cluster.getActiveCPUCount() < cluster.getMaxThreads()
                    && cluster.getAvailableStorage() > 0)
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

    @Inject(method = NativeCpuTargets.PLAN, at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$plan(net.minecraft.world.World world, IGrid requestedGrid,
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
        if (!(job instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob)) return;
        if (job.isSimulation()) {
            cir.setReturnValue(null);
            return;
        }
        java.util.List<cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu> candidates = new java.util.ArrayList<>();
        for (CraftingCPUCluster cpu : craftingCPUClusters)
            if (cpu instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu
                && (target == null || target == cpu)
                && cpu.isActive()
                && !cpu.isBusy()
                && cpu.getAvailableStorage() >= job.getByteTotal()
                && (source.isPlayer() ? cpu.getCraftingAllowMode() != appeng.api.config.CraftingAllow.ONLY_NONPLAYER
                    : cpu.getCraftingAllowMode() != appeng.api.config.CraftingAllow.ONLY_PLAYER))
                candidates.add((cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu) cpu);
        candidates.sort(
            java.util.Comparator
                .comparingInt(
                    (cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu cpu) -> cpu.getCoProcessors())
                .thenComparingLong(cpu -> cpu.getAvailableStorage()));
        if (prioritizePower) java.util.Collections.reverse(candidates);
        appeng.api.networking.crafting.ICraftingLink link = null;
        for (var cpu : candidates) {
            link = cpu.submitJob(grid, job, source, requester);
            if (link != null) {
                if (follow && source instanceof appeng.api.networking.security.PlayerSource)
                    cpu.togglePlayerFollowStatus(
                        ((appeng.api.networking.security.PlayerSource) source).player.getCommandSenderName());
                break;
            }
        }
        cir.setReturnValue(link);
    }
}
