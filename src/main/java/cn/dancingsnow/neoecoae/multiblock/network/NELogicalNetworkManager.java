package cn.dancingsnow.neoecoae.multiblock.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

/**
 * Server-thread authority for network-switch group membership. A logical group is scoped to one
 * dimension, one subsystem, one online AE2 grid and one frequency, and is capped at
 * {@link NEFrequencyAllocator#HOST_LIMIT} members - both crafting and computation groups partition
 * into chunks of that size. A switch multiplier only applies after at least two physical hosts join
 * the same logical group.
 */
public final class NELogicalNetworkManager {

    private static final Map<ServerWorldView, LevelState> LEVELS = new IdentityHashMap<>();
    /** Same order the network clusters use to pick their leader. */
    private static final java.util.Comparator<BlockPos> HOST_POSITION_ORDER = java.util.Comparator
        .comparingInt(BlockPos::getX)
        .thenComparingInt(BlockPos::getY)
        .thenComparingInt(BlockPos::getZ);
    private static final int NETWORK_HOST_LIMIT = NEFrequencyAllocator.HOST_LIMIT;

    private NELogicalNetworkManager() {}

    public static void attach(NECluster<?> cluster) {
        if (cluster instanceof NEComputationCluster) {
            refreshAfterGridChange(cluster);
            return;
        }
        if (!(cluster instanceof NECraftingCluster craftingCluster)) return;
        WorldView level = getLevel(cluster);
        if (!(level instanceof ServerWorldView serverLevel) || cluster.isDestroyed() || !cluster.isNetworkMode()) {
            detach(cluster);
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        if (state.crafting.add(craftingCluster)) {
            assignCraftingFrequencyIfNeeded(craftingCluster, state.crafting);
            rebuildCrafting(state);
            refreshAfterGridChange(craftingCluster);
        }
    }

    public static void refresh(NECluster<?> cluster) {
        if (cluster instanceof NEComputationCluster) {
            refreshAfterGridChange(cluster);
            return;
        }
        if (cluster.isDestroyed() || !cluster.isNetworkMode()) {
            detach(cluster);
            return;
        }
        WorldView level = getLevel(cluster);
        if (!(level instanceof ServerWorldView serverLevel)) {
            clearAssociation(cluster);
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        if (cluster instanceof NECraftingCluster craftingCluster) {
            state.crafting.add(craftingCluster);
            assignCraftingFrequencyIfNeeded(craftingCluster, state.crafting);
            rebuildCrafting(state);
        }
    }

    public static void refreshAfterGridChange(NECluster<?> cluster) {
        WorldView level = getLevel(cluster);
        if (!(level instanceof ServerWorldView serverLevel) || cluster.isDestroyed()
            || !(cluster instanceof NEComputationCluster) && !cluster.isNetworkMode()) {
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        state.pendingGridRefresh.add(cluster);
        if (cluster instanceof NEComputationCluster computation) {
            for (var cpu : computation.getAllocatedCPUs()) cpu.getLogic()
                .refreshExactRefillGrid();
            if (state.awaitingCapacity.remove(computation)) state.pendingCapacity.add(computation);
        }
    }

    public static void requestComputationGridRefresh(NEComputationCluster cluster) {
        if (getLevel(cluster) instanceof ServerWorldView level) {
            requestComputationGridRefresh(cluster, level.getGameTime());
        }
    }

    public static void requestComputationGridRefresh(NEComputationCluster cluster, long tick) {
        if (getLevel(cluster) instanceof ServerWorldView level) {
            LEVELS.computeIfAbsent(level, ignored -> new LevelState()).pendingGridTopology
                .merge(cluster, tick, Math::min);
        }
    }

    public static void requestComputationCapacityRefresh(NEComputationCluster cluster) {
        if (getLevel(cluster) instanceof ServerWorldView level) {
            LEVELS.computeIfAbsent(level, ignored -> new LevelState()).pendingCapacity.add(cluster);
        }
    }

    public static void requestCpuPublication(NEComputationCluster cluster, IGrid grid, IGridNode node) {
        if (getLevel(cluster) instanceof ServerWorldView level) {
            LEVELS.computeIfAbsent(level, ignored -> new LevelState()).pendingCpuGrids.put(grid, node);
        }
    }

    /** Drain after AE2's grid update, once boot, power and channel state have settled. */
    public static void tick(ServerWorldView level) {
        LevelState state = LEVELS.get(level);
        if (state == null || level.getServer()
            .isStopped()) return;
        if (!state.pendingGridTopology.isEmpty()) {
            List<NEComputationCluster> pending = new ArrayList<>();
            long now = level.getGameTime();
            state.pendingGridTopology.entrySet()
                .removeIf(entry -> {
                    if (entry.getValue() > now) return false;
                    pending.add(entry.getKey());
                    return true;
                });
            if (!pending.isEmpty()) {
                MBCalculator.pauseGridRebuilds();
                try {
                    for (NEComputationCluster cluster : pending)
                        if (!cluster.isDestroyed() && getLevel(cluster) == level) {
                            cluster.refreshGridChanges();
                        }
                } finally {
                    MBCalculator.resumeGridRebuilds();
                }
            }
        }
        Set<NECluster<?>> pending = new ReferenceOpenHashSet<>(state.pendingGridRefresh);
        state.pendingGridRefresh.clear();
        boolean computationChanged = state.computationDirty;
        state.computationDirty = false;
        Set<NetworkGroupKey> retunedGroups = null;
        Map<IGrid, ECOComputationSystemBlockEntity> plannerSources = new IdentityHashMap<>();
        for (NECluster<?> cluster : pending) {
            if (!cluster.isDestroyed() && getLevel(cluster) == level) {
                if (cluster instanceof NEComputationCluster computation) {
                    if (computation.getController() == null || computation.getController()
                        .getCluster() != computation) continue;
                    if (computation.isGridOnline()) {
                        IGrid grid = computation.getNode()
                            .getGrid();
                        ECOComputationSystemBlockEntity source = plannerSources.computeIfAbsent(grid, key -> {
                            var hosts = NativeECOPlanning.hosts(key);
                            for (var host : hosts) if (!pending.contains(host.getCluster())) return host;
                            return hosts.isEmpty() ? computation.getController() : hosts.get(0);
                        });
                        computation.getController()
                            .adoptGridPlannerSettings(source);
                    }
                    NetworkGroupKey oldKey = state.computationKeys.get(computation);
                    if (computation.isNetworkMode()) {
                        state.computation.add(computation);
                        assignComputationFrequencyIfNeeded(computation, state.computation);
                        NetworkGroupKey key = computationNetworkKey(computation);
                        if (!key.equals(oldKey)) computationChanged = true;
                        if (oldKey != null && oldKey.frequency != key.frequency) {
                            if (retunedGroups == null) retunedGroups = new HashSet<>();
                            retunedGroups.add(oldKey);
                            retunedGroups.add(key);
                        }
                        state.computationKeys.put(computation, key);
                    } else if (state.computation.remove(computation)) {
                        state.computationKeys.remove(computation);
                        computation.setNetworkCluster(null);
                        computationChanged = true;
                    }
                } else refresh(cluster);
            }
        }
        if (computationChanged) {
            var profiler = level.getServer()
                .nativeServer().theProfiler;
            profiler.startSection("ecoComputationGroups");
            try {
                rebuildComputation(state, retunedGroups);
            } finally {
                profiler.endSection();
            }
        }
        boolean pendingStructures = MBCalculator.hasPendingChecks(level);
        if (state.retryCapacityAfterDiscovery && !pendingStructures) {
            state.retryCapacityAfterDiscovery = false;
            state.pendingCapacity.addAll(state.awaitingCapacity);
            state.awaitingCapacity.clear();
        }
        if (!state.pendingCapacity.isEmpty()) {
            List<NEComputationCluster> capacity = new ArrayList<>(state.pendingCapacity);
            state.pendingCapacity.clear();
            for (NEComputationCluster cluster : capacity) {
                state.awaitingCapacity.remove(cluster);
                if (cluster.isDestroyed() || cluster.getController() == null
                    || cluster.getController()
                        .getCluster() != cluster)
                    continue;
                if (cluster.canSettleCapacity(pendingStructures)) {
                    cluster.settleCapacity();
                    cluster.notifyGridCpuChange();
                } else {
                    state.awaitingCapacity.add(cluster);
                    state.retryCapacityAfterDiscovery |= pendingStructures;
                }
            }
        }
        if (!state.pendingCpuGrids.isEmpty()) {
            Map<IGrid, IGridNode> publications = new IdentityHashMap<>(state.pendingCpuGrids);
            state.pendingCpuGrids.clear();
            publications.forEach(
                (grid, node) -> { if (!grid.isEmpty()) grid.postEvent(new MENetworkCraftingCpuChange(node)); });
        }
        if (state.isEmpty()) LEVELS.remove(level, state);
    }

    public static void detachBeforeDestroy(NECluster<?> cluster) {
        detach(cluster);
    }

    public static void clearAssociation(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster) {
            craftingCluster.setNetworkCluster(null);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            computationCluster.setNetworkCluster(null);
        }
    }

    public static void clearAll() {
        for (ServerWorldView level : new ArrayList<>(LEVELS.keySet())) {
            clear(level);
        }
    }

    public static void clear(ServerWorldView level) {
        LevelState state = LEVELS.remove(level);
        if (state == null) {
            return;
        }
        for (NECraftingCluster cluster : state.crafting) {
            cluster.setNetworkCluster(null);
        }
        for (NEComputationCluster cluster : state.computation) {
            cluster.setNetworkCluster(null);
        }
        state.pendingGridRefresh.clear();
        state.craftingNetworks.clear();
        state.computationNetworks.clear();
        state.crafting.clear();
        state.computation.clear();
    }

    private static void detach(NECluster<?> cluster) {
        WorldView level = getLevel(cluster);
        if (!(level instanceof ServerWorldView serverLevel)) {
            clearAssociation(cluster);
            return;
        }
        LevelState state = LEVELS.get(serverLevel);
        if (state == null) {
            clearAssociation(cluster);
            return;
        }
        if (cluster instanceof NECraftingCluster craftingCluster) {
            if (state.crafting.remove(craftingCluster)) {
                rebuildCrafting(state);
            }
            craftingCluster.setNetworkCluster(null);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            if (state.computation.remove(computationCluster)) {
                state.computationDirty = true;
            }
            state.computationKeys.remove(computationCluster);
            state.pendingGridRefresh.remove(computationCluster);
            state.pendingGridTopology.remove(computationCluster);
            state.pendingCapacity.remove(computationCluster);
            state.awaitingCapacity.remove(computationCluster);
            computationCluster.setNetworkCluster(null);
            computationCluster.notifyGridCpuChange();
        }
        if (state.isEmpty()) {
            LEVELS.remove(serverLevel);
        }
    }

    private static void rebuildCrafting(LevelState state) {
        state.crafting.removeIf(cluster -> {
            var controller = cluster.getController();
            boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
            if (stale) {
                cluster.setNetworkCluster(null);
            }
            return stale;
        });
        if (state.crafting.isEmpty()) {
            state.craftingNetworks.clear();
            return;
        }

        Map<NetworkGroupKey, List<NECraftingCluster>> groups = new HashMap<>();
        for (NECraftingCluster cluster : state.crafting) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(craftingNetworkKey(cluster), ignored -> new ArrayList<>())
                .add(cluster);
        }

        Set<NetworkPartitionKey> activeKeys = new HashSet<>();
        for (Map.Entry<NetworkGroupKey, List<NECraftingCluster>> entry : groups.entrySet()) {
            List<NECraftingCluster> clusters = entry.getValue();
            // Hash-set order changes between rebuilds; sorting keeps partitions (and their leaders) stable.
            clusters.sort(
                java.util.Comparator.comparing(
                    cluster -> cluster.getController()
                        .getBlockPos(),
                    HOST_POSITION_ORDER));
            for (int start = 0; start < clusters.size(); start += NETWORK_HOST_LIMIT) {
                int partition = start / NETWORK_HOST_LIMIT;
                NetworkPartitionKey key = new NetworkPartitionKey(entry.getKey(), partition);
                activeKeys.add(key);
                List<NECraftingCluster> members = clusters
                    .subList(start, Math.min(start + NETWORK_HOST_LIMIT, clusters.size()));
                if (members.size() < 2) {
                    // Solo member: no multiplier applies, so no network cluster is needed.
                    continue;
                }
                NECraftingNetworkCluster network = state.craftingNetworks
                    .computeIfAbsent(key, ignored -> new NECraftingNetworkCluster());
                for (NECraftingCluster cluster : members) {
                    cluster.setNetworkCluster(network);
                }
                network.configure(members);
            }
        }
        state.craftingNetworks.keySet()
            .retainAll(activeKeys);
    }

    private static void rebuildComputation(LevelState state, @Nullable Set<NetworkGroupKey> retunedGroups) {
        state.computation.removeIf(cluster -> {
            var controller = cluster.getController();
            boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
            if (stale) {
                state.computationKeys.remove(cluster);
                cluster.setNetworkCluster(null);
            }
            return stale;
        });
        if (state.computation.isEmpty()) {
            state.computationNetworks.clear();
            return;
        }

        Map<NEComputationCluster, List<NEComputationCluster>> previousPools = new IdentityHashMap<>();
        Map<NetworkGroupKey, List<NEComputationCluster>> groups = new HashMap<>();
        for (NEComputationCluster cluster : state.computation) {
            NetworkGroupKey key = state.computationKeys.get(cluster);
            if (retunedGroups != null && retunedGroups.contains(key)) {
                previousPools.put(cluster, poolMembers(cluster));
            }
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(computationNetworkKey(cluster), ignored -> new ArrayList<>())
                .add(cluster);
        }

        Set<NetworkPartitionKey> activeKeys = new HashSet<>();
        for (Map.Entry<NetworkGroupKey, List<NEComputationCluster>> entry : groups.entrySet()) {
            List<NEComputationCluster> clusters = entry.getValue();
            clusters.sort(
                java.util.Comparator.comparing(
                    cluster -> cluster.getController()
                        .getBlockPos(),
                    HOST_POSITION_ORDER));
            for (int start = 0; start < clusters.size(); start += NETWORK_HOST_LIMIT) {
                int partition = start / NETWORK_HOST_LIMIT;
                NetworkPartitionKey key = new NetworkPartitionKey(entry.getKey(), partition);
                activeKeys.add(key);
                List<NEComputationCluster> members = clusters
                    .subList(start, Math.min(start + NETWORK_HOST_LIMIT, clusters.size()));
                if (members.size() < 2) {
                    // Solo member: no multiplier applies, so no network cluster is needed.
                    continue;
                }
                NEComputationNetworkCluster network = state.computationNetworks
                    .computeIfAbsent(key, ignored -> new NEComputationNetworkCluster());
                for (NEComputationCluster cluster : members) {
                    cluster.setNetworkCluster(network);
                }
                network.configure(members);
            }
        }
        state.computationNetworks.keySet()
            .retainAll(activeKeys);
        previousPools.forEach(
            (cluster, previous) -> { if (!previous.equals(poolMembers(cluster))) cluster.deferCapacitySettlement(); });
        // AE2 caches the published placeholder CPUs. Frequency changes do not replace a node,
        // so explicitly republish after grouping is final to update leaders and pooled capacity.
        Set<IGrid> notifiedGrids = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (NEComputationCluster cluster : state.computation) {
            var grid = cluster.getGrid();
            if (grid != null && notifiedGrids.add(grid.nativeGrid())) cluster.notifyGridCpuChange();
        }
    }

    private static List<NEComputationCluster> poolMembers(NEComputationCluster cluster) {
        var pool = cluster.getNetworkCluster();
        return pool == null ? java.util.Collections.singletonList(cluster) : new ArrayList<>(pool.getMembers());
    }

    private static void assignCraftingFrequencyIfNeeded(NECraftingCluster cluster, Set<NECraftingCluster> clusters) {
        var controller = cluster.getController();
        if (controller == null || controller.hasNetworkFrequency()) {
            return;
        }
        Object network = networkObject(cluster);
        if (network == null) {
            return;
        }
        IntArrayList assignedFrequencies = new IntArrayList();
        for (NECraftingCluster other : clusters) {
            if (other == cluster || networkObject(other) != network) {
                continue;
            }
            var otherController = other.getController();
            if (otherController != null && otherController.hasNetworkFrequency()) {
                assignedFrequencies.add(otherController.getNetworkFrequency());
            }
        }
        controller.assignNetworkFrequency(NEFrequencyAllocator.allocate(assignedFrequencies));
    }

    private static void assignComputationFrequencyIfNeeded(NEComputationCluster cluster,
        Set<NEComputationCluster> clusters) {
        var controller = cluster.getController();
        if (controller == null || controller.hasNetworkFrequency()) {
            return;
        }
        Object network = networkObject(cluster);
        if (network == null) {
            return;
        }
        IntArrayList assignedFrequencies = new IntArrayList();
        for (NEComputationCluster other : clusters) {
            if (other == cluster || networkObject(other) != network) {
                continue;
            }
            var otherController = other.getController();
            if (otherController != null && otherController.hasNetworkFrequency()) {
                assignedFrequencies.add(otherController.getNetworkFrequency());
            }
        }
        controller.assignNetworkFrequency(NEFrequencyAllocator.allocate(assignedFrequencies));
    }

    private static NetworkGroupKey craftingNetworkKey(NECraftingCluster cluster) {
        return networkKey(
            cluster,
            cluster.getController() == null ? 0
                : cluster.getController()
                    .getNetworkFrequency());
    }

    private static NetworkGroupKey computationNetworkKey(NEComputationCluster cluster) {
        return networkKey(
            cluster,
            cluster.getController() == null ? 0
                : cluster.getController()
                    .getNetworkFrequency());
    }

    private static NetworkGroupKey networkKey(NECluster<?> cluster, int frequency) {
        Object network = networkObject(cluster);
        return new NetworkGroupKey(network == null ? cluster : network, frequency);
    }

    private static Object networkObject(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster) {
            var controller = craftingCluster.getController();
            if (controller != null && controller.getMainNode()
                .isOnline()) {
                return controller.getMainNode()
                    .getGrid();
            }
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            if (computationCluster.isGridOnline()) return computationCluster.getGrid();
        }
        return null;
    }

    private static @Nullable WorldView getLevel(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster && craftingCluster.getController() != null) {
            return craftingCluster.getController()
                .getLevel();
        }
        if (cluster instanceof NEComputationCluster computationCluster && computationCluster.getController() != null) {
            return computationCluster.getController()
                .getLevel();
        }
        return null;
    }

    private static final class NetworkPartitionKey {

        private final NetworkGroupKey group;
        private final int partition;

        private NetworkPartitionKey(NetworkGroupKey group, int partition) {
            this.group = group;
            this.partition = partition;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NetworkPartitionKey key && group.equals(key.group) && partition == key.partition;
        }

        @Override
        public int hashCode() {
            return 31 * group.hashCode() + partition;
        }
    }

    private static final class NetworkGroupKey {

        private final Object network;
        private final int frequency;

        private NetworkGroupKey(Object network, int frequency) {
            this.network = network;
            this.frequency = frequency;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NetworkGroupKey key && network == key.network && frequency == key.frequency;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(network) + frequency;
        }
    }

    private static final class LevelState {

        private final Set<NECraftingCluster> crafting = new HashSet<>();
        private final Set<NEComputationCluster> computation = new HashSet<>();
        private final Map<NetworkPartitionKey, NECraftingNetworkCluster> craftingNetworks = new HashMap<>();
        private final Map<NetworkPartitionKey, NEComputationNetworkCluster> computationNetworks = new HashMap<>();
        private final ReferenceSet<NECluster<?>> pendingGridRefresh = new ReferenceOpenHashSet<>();
        private final Map<NEComputationCluster, Long> pendingGridTopology = new IdentityHashMap<>();
        private final ReferenceSet<NEComputationCluster> pendingCapacity = new ReferenceOpenHashSet<>();
        private final ReferenceSet<NEComputationCluster> awaitingCapacity = new ReferenceOpenHashSet<>();
        private final Map<NEComputationCluster, NetworkGroupKey> computationKeys = new IdentityHashMap<>();
        private final Map<IGrid, IGridNode> pendingCpuGrids = new IdentityHashMap<>();
        private boolean computationDirty;
        private boolean retryCapacityAfterDiscovery;

        private boolean isEmpty() {
            return crafting.isEmpty() && computation.isEmpty()
                && pendingGridRefresh.isEmpty()
                && pendingGridTopology.isEmpty()
                && pendingCapacity.isEmpty()
                && awaitingCapacity.isEmpty()
                && pendingCpuGrids.isEmpty()
                && !computationDirty;
        }
    }
}
