package cn.dancingsnow.neoecoae.computation.ae2;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.crafting.CraftingLink;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.NeoECOAE;

final class ECOComputationCpuBridge {

    private static Field craftingCpuClustersField;

    private ECOComputationCpuBridge() {}

    static boolean sync(ICraftingGrid grid, ECOComputationCpuPool owner, Collection<ECOComputationVirtualCpu> cpus) {
        Set<CraftingCPUCluster> clusters = craftingCpuClusters(grid);
        if (clusters == null) {
            return false;
        }
        detachOwned(clusters, owner);
        clusters.addAll(cpus);
        addLinks(grid, cpus);
        return true;
    }

    static void detach(ICraftingGrid grid, ECOComputationCpuPool owner) {
        Set<CraftingCPUCluster> clusters = craftingCpuClusters(grid);
        if (clusters != null) {
            detachOwned(clusters, owner);
        }
    }

    private static void detachOwned(Set<CraftingCPUCluster> clusters, ECOComputationCpuPool owner) {
        clusters.removeIf(
            cluster -> cluster instanceof ECOComputationVirtualCpu
                && ((ECOComputationVirtualCpu) cluster).belongsToPool(owner));
    }

    private static void addLinks(ICraftingGrid grid, Collection<ECOComputationVirtualCpu> cpus) {
        if (!(grid instanceof CraftingGridCache)) {
            return;
        }
        CraftingGridCache cache = (CraftingGridCache) grid;
        for (ECOComputationVirtualCpu cpu : cpus) {
            if (cpu.getLastCraftingLink() instanceof CraftingLink) {
                cache.addLink((CraftingLink) cpu.getLastCraftingLink());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<CraftingCPUCluster> craftingCpuClusters(ICraftingGrid grid) {
        if (!(grid instanceof CraftingGridCache)) {
            return null;
        }
        try {
            if (craftingCpuClustersField == null) {
                craftingCpuClustersField = CraftingGridCache.class.getDeclaredField("craftingCPUClusters");
                craftingCpuClustersField.setAccessible(true);
            }
            return (Set<CraftingCPUCluster>) craftingCpuClustersField.get(grid);
        } catch (ReflectiveOperationException e) {
            NeoECOAE.LOG.error("Unable to access AE2 crafting CPU cluster registry", e);
            return null;
        }
    }
}
