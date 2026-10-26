package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.*;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;

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

    @Inject(method = "updateCPUClusters()V", at = @At("TAIL"), require = 1)
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
                if (cluster.isNetworkRepresentative())
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

    @Inject(method = { "addNode", "removeNode" }, at = @At("HEAD"), require = 1)
    private void neoecoae$topology(IGridNode node, IGridHost machine, CallbackInfo ci) {
        if (machine instanceof ECOComputationSystemBlockEntity) updateList = true;
    }
}
