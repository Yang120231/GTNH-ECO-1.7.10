package cn.dancingsnow.neoecoae.multiblock.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.ISecurityGrid;
import appeng.me.cache.SecurityCache;
import cn.dancingsnow.neoecoae.api.computation.ECOComputationCells;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public class NEComputationCluster extends NECluster<NEComputationCluster>
    implements cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPUHost {

    private final List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();

    private final List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();

    private final List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();

    private final List<ECOComputationParallelCoreBlockEntity> parallelCores = new ArrayList<>();

    @Nullable
    private ECOComputationSystemBlockEntity controller;

    @Nullable
    private IActionSource actionSource;
    private int maxThreads = 0;
    private long availableStorage = 0;

    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;

    @Nullable
    private NEComputationNetworkCluster networkCluster;

    private final Map<ICraftingPlan, ECOCraftingCPU> activeCpus = new IdentityHashMap<>();
    @Nullable
    private List<ECOCraftingCPU> allocatedCpuSnapshot;
    /** BigInt child plans are segmented by the controller and must not consume the finite CPU byte reservation. */
    private final ReferenceSet<ICraftingPlan> bigOrderPlans = new ReferenceOpenHashSet<>();
    private ECOCraftingCPU fakeCpu;
    private final Set<NEBlockEntity<?, ?>> gridMembers = Collections.newSetFromMap(new IdentityHashMap<>());
    private NEBlockEntity<?, ?> ingress;
    private IGridConnection ingressConnection;
    private boolean retriedGridNeighbors;
    private boolean legacyGrid;
    private boolean reservationsPending;
    private boolean memberRemovalPending;
    private boolean permanentRemoval;
    private final Set<NEBlockEntity<?, ?>> dirtyGridFaces = Collections.newSetFromMap(new IdentityHashMap<>());

    public NEComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    protected boolean hideAllCasingsWhenFormed() {
        return true;
    }

    @Override
    public boolean isNetworkMode() {
        return controller != null && controller.hasNetworkSwitch();
    }

    @Override
    public int getNetworkMultiplier() {
        if (controller == null) {
            return 1;
        }
        if (controller.hasHighEnergyNetworkSwitch()) {
            return 8;
        }
        if (controller.hasNormalNetworkSwitch()) {
            return 2;
        }
        return 1;
    }

    public int getNetworkMemberCount() {
        return networkCluster == null ? 1
            : networkCluster.getMembers()
                .size();
    }

    /** Unmet endgame preconditions across this host's logical network, or for this host alone when standalone. */
    public List<String> getEndgameBlockers() {
        if (networkCluster != null) {
            return networkCluster.getEndgameBlockers();
        }
        return NEComputationNetworkCluster.endgameBlockers(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(this));
    }

    public int getNormalSwitchHostCount() {
        if (networkCluster != null) {
            return networkCluster.getNormalSwitchHostCount();
        }
        return controller != null && controller.hasNormalNetworkSwitch() ? 1 : 0;
    }

    public int getHighEnergySwitchHostCount() {
        if (networkCluster != null) {
            return networkCluster.getHighEnergySwitchHostCount();
        }
        return controller != null && controller.hasHighEnergyNetworkSwitch() ? 1 : 0;
    }

    public void setNetworkCluster(@Nullable NEComputationNetworkCluster networkCluster) {
        this.networkCluster = networkCluster;
    }

    /**
     * Whether this member should contribute the group's shared placeholder CPU. Every member in a
     * group reports the same pooled numbers, so only the first member avoids advertising duplicate
     * free capacity in the crafting terminal.
     */
    public boolean isNetworkRepresentative() {
        return networkCluster == null || networkCluster.isLeader(this);
    }

    @Override
    public void destroy() {
        if (isDestroyed()) return;
        notifyGridCpuChange();
        NELogicalNetworkManager.detachBeforeDestroy(this);
        MBCalculator.pauseGridRebuilds();
        try {
            try {
                super.destroy();
            } finally {
                if (!gridMembers.isEmpty()) {
                    clearIngressConnection();
                    gridMembers.forEach(member -> member.setDeferredGridNode(false));
                    gridMembers.clear();
                    dirtyGridFaces.clear();
                    ingress = null;
                }
                for (var nativeCpu : nativeCpus.values()) releaseNativeCPU(nativeCpu);
                nativeCpus.clear();
            }
        } finally {
            MBCalculator.resumeGridRebuilds();
        }
    }

    private static void releaseNativeCPU(@Nullable appeng.api.networking.crafting.ICraftingCPU nativeCpu) {
        if (nativeCpu instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu bridge)
            bridge.release();
    }

    @Override
    public void breakCluster() {
        if (isDestroyed()) return;
        // Keep nodes attached until cancellation has returned CPU inventory and notified loaded workers.
        // Ordinary destroy() preserves deferred CPU state for topology rebuilds and chunk unloads.
        for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
            core.prepareForPermanentRemoval();
        }
        destroy();
    }

    /**
     * Own real byte capacity from installed computation cells, ignoring network pooling.
     */
    public long getTotalStorage() {
        return NEMath.saturatingAdd(collectStorage(upperDrives), collectStorage(lowerDrives));
    }

    /**
     * Bytes reserved by this member's active jobs. This may exceed the member's physical storage while
     * it belongs to a computation network, because the network admits jobs against the shared byte pool.
     */
    public long getOwnUsedStorage() {
        return getActiveJobBytes();
    }

    public int getOwnMaxThreads() {
        return this.maxThreads;
    }

    public int getMaxThreads() {
        return networkCluster != null ? networkCluster.getTotalThreads() : this.maxThreads;
    }

    public long getAvailableStorage() {
        return networkCluster != null ? networkCluster.getPooledAvailableStorage() : this.availableStorage;
    }

    public int getPooledParallelism() {
        return networkCluster != null ? networkCluster.getTotalParallelism() : getCPUAccelerators();
    }

    public int getPooledFullParallelism() {
        return networkCluster != null ? networkCluster.getTotalFullParallelism() : getFullCPUAccelerators();
    }

    public long getPooledTotalStorage() {
        return networkCluster != null ? networkCluster.getTotalStorageCapacity() : getTotalStorage();
    }

    /** Whether a new job can still spawn a CPU in this host's admission pool (network or standalone). */
    @Override
    public boolean hasFreeThread() {
        return getActiveCPUCount() < getMaxThreads();
    }

    @Override
    public Object submissionPool() {
        return networkCluster != null ? networkCluster : this;
    }

    @Override
    public boolean pruneSubmissionPool() {
        if (networkCluster == null) return pruneInactiveCPUs();
        boolean changed = false;
        for (NEComputationCluster member : networkCluster.getMembers()) {
            if (member.pruneInactiveCPUs()) changed = true;
        }
        return changed;
    }

    /**
     * Whether AE2 should list this host's placeholder CPU: the group representative while a thread is free,
     * exactly as the 1.21 baseline publishes it. Free bytes are not required; a too-small CPU is still listed.
     */
    public boolean shouldPublishPlaceholder() {
        return !reservationsPending && !memberRemovalPending
            && isNetworkRepresentative()
            && (networkCluster == null || !networkCluster.hasPendingCapacity())
            && hasFreeThread();
    }

    public int getActiveCPUCount() {
        if (networkCluster == null) {
            return getActiveCPUs().size();
        }
        int total = 0;
        for (NEComputationCluster member : networkCluster.getMembers()) {
            total += member.getActiveCPUs()
                .size();
        }
        return total;
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECOComputationDriveBlockEntity driveBlockEntity) {
            reclassifyDrive(driveBlockEntity);
        }
        if (blockEntity instanceof ECOComputationThreadingCoreBlockEntity threadingCore) {
            threadingCores.add(threadingCore);
        }
        if (blockEntity instanceof ECOComputationSystemBlockEntity system) {
            controller = system;
            actionSource = IActionSource.ofMachine(system);
        }
        if (blockEntity instanceof ECOComputationParallelCoreBlockEntity parallelCore) {
            parallelCores.add(parallelCore);
        }
    }

    public boolean usesCompactGrid() {
        return controller != null && controller.defersGridNode();
    }

    public Iterator<IGridNode> gridNodes() {
        List<IGridNode> nodes = new ArrayList<>(gridMembers.size());
        for (var member : gridMembers) {
            IGridNode node = member.getGridNode();
            if (node != null) nodes.add(node);
        }
        return nodes.iterator();
    }

    public void memberChanged(NEBlockEntity<?, ?> member) {
        if (!isDestroyed() && usesCompactGrid()) {
            dirtyGridFaces.add(member);
            NELogicalNetworkManager.requestComputationGridRefresh(this);
        }
    }

    public void memberRemoved(boolean permanent) {
        memberRemovalPending = true;
        permanentRemoval |= permanent;
        controller.requestMultiblockCheck();
    }

    public boolean hasPermanentRemoval() {
        return permanentRemoval;
    }

    public void refreshValidatedGrid(boolean force) {
        boolean wasWaiting = memberRemovalPending;
        memberRemovalPending = false;
        permanentRemoval = false;
        refreshGrid(force);
        NELogicalNetworkManager.refreshAfterGridChange(this);
        if (wasWaiting) notifyGridCpuChange();
    }

    public void requestGridPowerUpdate() {
        if (!isDestroyed() && controller != null) NELogicalNetworkManager.requestComputationGridRefresh(this);
    }

    private WorldView getWorld() {
        return controller == null ? null : controller.getLevel();
    }

    public void refreshGrid(boolean force) {
        if (isDestroyed() || !usesCompactGrid() || !(getWorld() instanceof ServerWorldView level)) return;
        var profiler = level.getServer()
            .nativeServer().theProfiler;
        profiler.startSection("ecoComputationTopology");
        MBCalculator.pauseGridRebuilds();
        try {
            updateGrid(level, force);
        } finally {
            MBCalculator.resumeGridRebuilds();
            profiler.endSection();
        }
    }

    private void updateGrid(ServerWorldView level, boolean force) {
        NEBlockEntity<?, ?> nextIngress = null;
        Direction ingressSide = null;
        IGridNode externalNode = null;
        boolean legacy = false;
        boolean pendingNeighbor = false;
        for (TileEntity tile : blockEntities) {
            if (!(tile instanceof NEBlockEntity<?, ?>member)) continue;
            legacy |= !member.defersGridNode();
            BlockPos position = member.getBlockPos();
            for (Direction side : Direction.values()) {
                BlockPos neighbor = position.relative(side);
                if (containsPosition(neighbor)) continue;
                TileEntity adjacent = level.getBlockEntity(neighbor);
                if (adjacent instanceof IGridHost host && !adjacent.isInvalid()) {
                    if (adjacent instanceof NEBlockEntity<?, ?>part && part.defersGridNode()) {
                        if (part.getCluster() != null && part.getCluster() != this && part.isFormed()) legacy = true;
                        continue;
                    }
                    IGridNode candidate = host.getGridNode(
                        side.getOpposite()
                            .nativeSide());
                    if (candidate == null) {
                        pendingNeighbor |= adjacent instanceof appeng.tile.grid.AENetworkTile network
                            && !network.getProxy()
                                .isReady();
                        continue;
                    }
                    if (nextIngress == null) {
                        nextIngress = member;
                        ingressSide = side;
                        externalNode = candidate;
                    } else legacy = true;
                }
            }
        }
        IGridNode previousController = getNode();
        IGrid previousGrid = previousController == null ? null : previousController.getGrid();
        boolean awaitingSecurity = false;
        boolean allowInternalLink = true;
        if (!legacy && nextIngress != null) {
            IGrid externalGrid = externalNode.getGrid();
            ISecurityGrid security = externalGrid == null ? null : externalGrid.getCache(ISecurityGrid.class);
            if (security instanceof SecurityCache nativeSecurity && nativeSecurity.getSecurityKey() != -1) {
                boolean attached = connectionsMatch(level, nextIngress, EnumSet.of(ingressSide));
                boolean linked = attached
                    && (nextIngress == controller || previousController != null && hasIngressLink(previousController));
                if (!linked) {
                    if (!security.isAvailable()) {
                        awaitingSecurity = true;
                        allowInternalLink = false;
                        if (!attached) nextIngress = null;
                    } else if (!attached
                        && !security.hasPermission(nextIngress.getOwnerPlayerId(), SecurityPermissions.BUILD)) {
                            nextIngress.securityBreak();
                            nextIngress = null;
                        } else if (nextIngress != controller
                            && !security.hasPermission(controller.getOwnerPlayerId(), SecurityPermissions.BUILD)) {
                                allowInternalLink = false;
                                controller.securityBreak();
                            }
                }
            }
        }
        if (legacy) nextIngress = null;
        boolean layoutChanged = legacyGrid != legacy || ingress != nextIngress;
        legacyGrid = legacy;
        if (ingress != nextIngress) clearIngressConnection();
        ingress = nextIngress;

        Set<NEBlockEntity<?, ?>> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        if (legacy) {
            for (TileEntity tile : blockEntities) if (tile instanceof NEBlockEntity<?, ?>member) wanted.add(member);
        } else {
            wanted.add(controller);
            if (ingress != null) wanted.add(ingress);
        }
        if (wanted.contains(controller) && controller.setDeferredGridNode(true)) gridMembers.add(controller);
        for (NEBlockEntity<?, ?> member : wanted) {
            if (member != controller && member.setDeferredGridNode(true)) gridMembers.add(member);
        }
        updateGridPower();
        for (NEBlockEntity<?, ?> member : wanted) {
            if (!gridMembers.contains(member)) continue;
            if (!member.defersGridNode()) {
                member.onGridConnectableSidesChanged();
                continue;
            }
            EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
            if (legacy) {
                BlockPos position = member.getBlockPos();
                for (Direction side : Direction.values()) {
                    if (level.getBlockEntity(position.relative(side)) instanceof IGridHost) faces.add(side);
                }
            } else if (member == ingress) {
                faces.add(ingressSide);
            }
            boolean refresh = force || dirtyGridFaces.contains(member) && !connectionsMatch(level, member, faces);
            member.getMainNode()
                .updateExposedOnSides(faces, refresh);
        }
        dirtyGridFaces.clear();
        if (awaitingSecurity) {
            dirtyGridFaces.add(controller);
            NELogicalNetworkManager.requestComputationGridRefresh(this, level.getGameTime() + 20);
        }
        if (pendingNeighbor && !retriedGridNeighbors) {
            retriedGridNeighbors = true;
            dirtyGridFaces.add(controller);
            NELogicalNetworkManager.requestComputationGridRefresh(this);
        } else if (!pendingNeighbor) retriedGridNeighbors = false;

        IGridNode controllerNode = getNode();
        if (allowInternalLink && ingress != null && ingress != controller && controllerNode != null) {
            IGridNode portNode = ingress.getGridNode();
            if (portNode != null && (ingressConnection == null || !controllerNode.getConnections()
                .contains(ingressConnection))) {
                try {
                    ingressConnection = AEApi.instance()
                        .createGridConnection(controllerNode, portNode);
                } catch (FailedConnection refused) {
                    ingress.securityBreak();
                }
            }
        }
        gridMembers.removeIf(member -> {
            if (wanted.contains(member)) return false;
            member.setDeferredGridNode(false);
            return true;
        });
        controllerNode = getNode();
        if (previousGrid != null && previousController != null
            && (controllerNode == null || previousGrid != controllerNode.getGrid())
            && !previousGrid.isEmpty()) {
            NELogicalNetworkManager.requestCpuPublication(this, previousGrid, previousController);
        }
        boolean gridChanged = controllerNode != null && controllerNode.getGrid() != previousGrid;
        if (layoutChanged || gridChanged) NELogicalNetworkManager.refreshAfterGridChange(this);
        if (gridChanged) notifyGridCpuChange();
    }

    private static boolean connectionsMatch(ServerWorldView level, NEBlockEntity<?, ?> member, Set<Direction> faces) {
        IGridNode node = member.getGridNode();
        if (node == null) return faces.isEmpty();
        for (Direction side : faces) {
            TileEntity adjacent = level.getBlockEntity(
                member.getBlockPos()
                    .relative(side));
            IGridNode neighbor = adjacent instanceof IGridHost host ? host.getGridNode(
                side.getOpposite()
                    .nativeSide())
                : null;
            if (neighbor == null) return false;
            boolean found = false;
            for (IGridConnection connection : node.getConnections()) {
                if (connection.getDirection(node) == side.nativeSide() && connection.getOtherSide(node) == neighbor) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void updateGridPower() {
        double total = 0;
        for (TileEntity tile : blockEntities)
            if (tile instanceof NEBlockEntity<?, ?>member) total += member.appliedIdlePower()
                .orElse(0);
        for (NEBlockEntity<?, ?> member : gridMembers) {
            if (member.defersGridNode()) member.getMainNode()
                .setIdlePowerUsage(
                    legacyGrid ? member.appliedIdlePower()
                        .orElse(0) : member == controller ? total : 0);
        }
    }

    public void refreshGridChanges() {
        if (isDestroyed() || !usesCompactGrid()) return;
        if (dirtyGridFaces.isEmpty()) updateGridPower();
        else refreshGrid(false);
    }

    private void clearIngressConnection() {
        IGridConnection connection = ingressConnection;
        ingressConnection = null;
        IGridNode node = getNode();
        if (connection != null && node != null
            && node.getConnections()
                .contains(connection))
            connection.destroy();
    }

    public boolean reconcileMembers(List<NEBlockEntity<NEComputationCluster, ?>> members, BlockPos min, BlockPos max) {
        if (!usesCompactGrid() || controller == null || !members.contains(controller)) return false;
        for (var member : members) if (member.getCluster() != null && member.getCluster() != this) return false;
        Set<TileEntity> next = Collections.newSetFromMap(new IdentityHashMap<>());
        next.addAll(members);
        Set<TileEntity> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        previous.addAll(blockEntities);
        for (TileEntity tile : previous) {
            if (!next.contains(tile) && tile instanceof NEBlockEntity<?, ?>member && member.getCluster() == this)
                member.updateCluster(null);
        }
        blockEntities.clear();
        upperDrives.removeIf(member -> !next.contains(member));
        lowerDrives.removeIf(member -> !next.contains(member));
        threadingCores.removeIf(member -> !next.contains(member));
        parallelCores.removeIf(member -> !next.contains(member));
        setBounds(min, max);
        for (var member : members) {
            if (previous.contains(member)) blockEntities.add(member);
            else addBlockEntity(member);
        }
        for (var member : members) if (!previous.contains(member)) member.updateCluster(this);
        updateFormed(true);
        notifyGridCpuChange();
        return true;
    }

    /** Rebuilds this member's presentation placement without exposing list mutation to addons. */
    public void reclassifyDrive(ECOComputationDriveBlockEntity drive) {
        WorldView level = drive.getLevel();
        if (level == null) return;
        var context = ECOMultiblockParts.context(level, drive.getBlockPos());
        var below = ECOMultiblockParts.context(
            level,
            drive.getBlockPos()
                .below());
        boolean transmitter = ECOMultiblockParts.matches(
            below,
            ECOMultiblockParts.Role.TRANSMITTER,
            below.state()
                .is(CraftingMachines.COMPUTATION_TRANSMITTER));
        var row = ECOMultiblockParts
            .driveRow(context, transmitter ? ECOMultiblockParts.DriveRow.UPPER : ECOMultiblockParts.DriveRow.LOWER);
        boolean lower = row == ECOMultiblockParts.DriveRow.LOWER;
        List<ECOComputationDriveBlockEntity> selected = lower ? lowerDrives : upperDrives;
        List<ECOComputationDriveBlockEntity> other = lower ? upperDrives : lowerDrives;
        other.remove(drive);
        if (!selected.contains(drive)) selected.add(drive);
        selected.sort(
            java.util.Comparator.comparingInt(
                member -> ECOMultiblockParts.order(ECOMultiblockParts.context(level, member.getBlockPos()))));
        if (drive.isLowerDrive() != lower) {
            drive.setLowerDrive(lower);
            drive.setChanged();
        }
    }

    public void pickup(ICraftingPlan plan, ECOCraftingCPU cpu) {
        putActiveCpu(plan, cpu);
        deferCapacitySettlement();
    }

    public void deferCapacitySettlement() {
        reservationsPending = true;
        NELogicalNetworkManager.requestComputationCapacityRefresh(this);
    }

    public void settleCapacity() {
        reservationsPending = false;
        recalculateRemainingStorage();
    }

    public boolean hasPendingCapacity() {
        return reservationsPending;
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (formed) {
            if (usesCompactGrid() && MBCalculator.isModificationInProgress()) {
                deferCapacitySettlement();
            } else recalculateRemainingStorage();
            // The placeholder CPU is created lazily by getFakeCPU() and must never be replaced while the
            // cluster lives, so it is deliberately not (re)assigned here.
            this.maxThreads = threadingCores.stream()
                .mapToInt(
                    it -> it.getTier()
                        .getCPUThreads())
                .sum();
            if (controller != null) {
                this.selectionMode = controller.getCpuSelectionMode();
            }
        } else {
            availableStorage = 0;
        }
    }

    private long collectStorage(List<ECOComputationDriveBlockEntity> driveBlockEntities) {
        long ret = 0;
        for (ECOComputationDriveBlockEntity driveBlockEntity : driveBlockEntities) {
            ItemStack itemStack = driveBlockEntity.getCellStack();
            if (itemStack != null && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(itemStack)) {
                var memory = ECOComputationCells.describe(itemStack);
                if (memory != null) ret = NEMath.saturatingAdd(ret, memory.bytes());
            }
        }
        return ret;
    }

    public int getCPUAccelerators() {
        long total = parallelCores.stream()
            .mapToLong(
                core -> core.getTier()
                    .getCPUAccelerators())
            .sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    /** Parallelism this cluster reaches when every parallel slot holds an L9 core (its full build). */
    public int getFullCPUAccelerators() {
        long total = (long) parallelCores.size() * StorageTiers.L9.getCPUAccelerators();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public boolean canBeAutoSelectedFor(IActionSource actionSource) {
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> actionSource.player()
                .isPresent();
            case MACHINE_ONLY -> !actionSource.player()
                .isPresent();
        };
    }

    /** Applies preferred-first CPU selection after the selection-mode filter. */
    @Override
    public boolean isPreferredFor(IActionSource actionSource) {
        return switch (selectionMode) {
            case ANY -> false;
            case PLAYER_ONLY -> actionSource.player()
                .isPresent();
            case MACHINE_ONLY -> !actionSource.player()
                .isPresent();
        };
    }

    public void setSelectionMode(CpuSelectionMode mode) {
        if (networkCluster != null) {
            networkCluster.setSelectionMode(mode);
            return;
        }
        applyNetworkSelectionMode(mode);
    }

    void applyNetworkSelectionMode(CpuSelectionMode mode) {
        if (this.selectionMode == mode) {
            return;
        }
        this.selectionMode = mode;
        if (controller != null) {
            controller.setCpuSelectionMode(mode);
        }
        updateGridForChangedCpu();
    }

    public void cycleSelectionMode() {
        setSelectionMode(switch (selectionMode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        });
    }

    public @Nullable IGridNode getNode() {
        return controller != null ? controller.getActionableNode() : null;
    }

    public boolean isActive() {
        return !reservationsPending && !memberRemovalPending && isGridOnline();
    }

    boolean hasUnvalidatedRemoval() {
        return memberRemovalPending;
    }

    public boolean isGridOnline() {
        IGridNode node = getNode();
        return !isDestroyed() && node != null
            && node.isActive()
            && (!usesCompactGrid() || legacyGrid || ingress == controller || ingress != null && hasIngressLink(node));
    }

    public boolean canSettleCapacity(boolean pendingStructures) {
        if (!isNetworkMode() || activeCpus.isEmpty()) return true;
        if (!isGridOnline()) return getActiveJobBytes() <= getTotalStorage();
        if (!pendingStructures) return true;
        return networkCluster == null ? getActiveJobBytes() <= getTotalStorage() : networkCluster.reservationsFit();
    }

    private boolean hasIngressLink(IGridNode controllerNode) {
        return ingressConnection != null && controllerNode.getConnections()
            .contains(ingressConnection);
    }

    public ICraftingSubmitResult submitJob(GridView grid, ICraftingPlan job, IActionSource src,
        ICraftingRequester requestingMachine) {
        if (!isActive()) return CraftingSubmitResult.CPU_OFFLINE;
        if (this.networkCluster != null) {
            return this.networkCluster.submitJob(grid, job, src, requestingMachine);
        }
        // Finished jobs must not keep their thread or bytes until the next crafting-cache tick.
        pruneInactiveCPUs();
        if (this.availableStorage < job.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        ICraftingSubmitResult result = trySpawnLocalJob(grid, job, src, requestingMachine);
        return result == null ? CraftingSubmitResult.NO_CPU_FOUND : result;
    }

    /**
     * Tries to spawn and submit the job on one of this member's own physical threading cores.
     * Returns null only when no core ever produced a CPU at all (nothing to report); an unsuccessful
     * {@link ICraftingSubmitResult} from a spawned-but-rejected CPU is still returned so the caller
     * can surface the specific rejection reason.
     */
    @Nullable
    ICraftingSubmitResult trySpawnLocalJob(GridView grid, ICraftingPlan job, IActionSource src,
        ICraftingRequester requestingMachine) {
        GridView currentGrid = getGrid();
        if (!isActive() || currentGrid == null || currentGrid.nativeGrid() != grid.nativeGrid())
            return CraftingSubmitResult.CPU_OFFLINE;
        ICraftingSubmitResult lastResult = null;
        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            ECOCraftingCPU cpu = threadingCore.spawn(job);
            if (cpu == null) continue;
            ICraftingSubmitResult result = cpu.getLogic()
                .trySubmitJob(grid, job, src, requestingMachine);
            if (result.successful()) {
                if (this.registerActiveJob(job, cpu)) {
                    return result;
                }
                return CraftingSubmitResult.CPU_TOO_SMALL;
            }
            if (cpu.hasRemainingItems()) {
                // The failed admission could not roll everything back into the network. Keep the CPU as a draining
                // one (like a cancelled CPU with leftovers) so it keeps returning those items instead of losing them.
                cpu.getLogic()
                    .markForDeletion();
                putActiveCpu(job, cpu);
                cpu.markDirty();
                this.recalculateRemainingStorage();
                this.updateGridForChangedCpu();
            } else {
                threadingCore.deactivate(cpu);
                releaseNativeCPU(nativeCpus.remove(cpu));
            }
            // Every local CPU sees the same grid inventory. Retrying a confirmed material deficit only repeats the
            // complete extraction/rollback transaction and can multiply external-inventory persistence work.
            if (result.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT) return result;
            lastResult = result;
        }
        return lastResult;
    }

    boolean registerActiveJob(ICraftingPlan job, ECOCraftingCPU cpu) {
        putActiveCpu(job, cpu);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        return this.activeCpus.get(job) == cpu && cpu.getLogic()
            .hasJob();
    }

    public void recalculateRemainingStorage() {
        if (reservationsPending) return;
        long totalStorage = getTotalStorage();
        long usedStorage = getActiveJobBytes();

        this.availableStorage = Math.max(0, totalStorage - Math.min(totalStorage, usedStorage));
        boolean reservationsFit = networkCluster != null ? networkCluster.reservationsFit()
            : usedStorage <= totalStorage;
        if (reservationsFit || this.activeCpus.isEmpty()) {
            return;
        }

        List<ICraftingPlan> plansToKill = new ArrayList<>(this.activeCpus.keySet());
        for (ICraftingPlan plan : plansToKill) {
            ECOCraftingCPU cpu = activeCpus.get(plan);
            if (cpu == null || cpu.getLogic()
                .isMarkedForDeletion()) continue;
            this.killCpu(plan, false, false);
            if (networkCluster != null ? networkCluster.reservationsFit() : getActiveJobBytes() <= totalStorage) break;
        }

        this.availableStorage = Math.max(0, totalStorage - getActiveJobBytes());
    }

    /** Server-thread atomic reservation replacement for a parent's next complete long segment. */
    public boolean replaceBigOrderPlan(ECOCraftingCPU cpu, ICraftingPlan next) {
        ICraftingPlan previous = cpu.getPlan();
        if (previous == null || activeCpus.get(previous) != cpu || next.bytes() < 0) return false;
        removeActiveCpu(previous);
        bigOrderPlans.remove(previous);
        cpu.setBigOrderChildPlan(next);
        putActiveCpu(next, cpu);
        bigOrderPlans.add(next);
        recalculateRemainingStorage();
        return activeCpus.get(next) == cpu;
    }

    /**
     * GTNH merge reservation: the running job's plan is replaced by one covering both requests. Only the extra bytes
     * must still be free in the pool ({@code availableStorage >= usedStorage + job.getByteTotal()}).
     */
    @Override
    public boolean replaceMergedPlan(ECOCraftingCPU cpu, ICraftingPlan next) {
        ICraftingPlan previous = cpu.getPlan();
        if (previous == null || activeCpus.get(previous) != cpu || bigOrderPlans.contains(previous)) return false;
        if (reservationsPending || networkCluster != null && networkCluster.hasPendingCapacity()) return false;
        long extra = next.bytes() - previous.bytes();
        if (next.bytes() < 0 || extra < 0 || extra > getAvailableStorage()) return false;
        removeActiveCpu(previous);
        cpu.setBigOrderChildPlan(next);
        putActiveCpu(next, cpu);
        recalculateRemainingStorage();
        return activeCpus.get(next) == cpu;
    }

    private long getActiveJobBytes() {
        long usedStorage = 0L;
        for (var entry : activeCpus.entrySet()) {
            ICraftingPlan plan = entry.getKey();
            if (bigOrderPlans.contains(plan) || entry.getValue()
                .getLogic()
                .isMarkedForDeletion()) continue;
            usedStorage = NEMath.saturatingAdd(usedStorage, Math.max(0L, plan.bytes()));
        }
        return usedStorage;
    }

    private void putActiveCpu(ICraftingPlan plan, ECOCraftingCPU cpu) {
        activeCpus.put(plan, cpu);
        allocatedCpuSnapshot = null;
    }

    @Nullable
    private ECOCraftingCPU removeActiveCpu(@Nullable ICraftingPlan plan) {
        var removed = activeCpus.remove(plan);
        if (removed != null) allocatedCpuSnapshot = null;
        return removed;
    }

    /** Stable during output callbacks, which can re-enter the cluster and add or release CPUs. */
    public List<ECOCraftingCPU> getAllocatedCPUs() {
        if (allocatedCpuSnapshot == null) {
            allocatedCpuSnapshot = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(activeCpus.values());
        }
        return allocatedCpuSnapshot;
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        if (reservationsPending) return Collections.emptyList();
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        for (ECOCraftingCPU cpu : getAllocatedCPUs()) {
            if (cpu.getLogic()
                .hasJob()
                || cpu.getLogic()
                    .isMarkedForDeletion()
                || cpu.hasRemainingItems()) {
                cpus.add(cpu);
            }
        }
        return cpus;
    }

    /**
     * Releases every CPU whose job has ended (thread slot, byte reservation and native identity) and tells the
     * grid to rebuild its CPU list. Called every crafting-cache tick and before each submission; it allocates
     * nothing and posts nothing when no job finished.
     *
     * @return true when at least one CPU was released.
     */
    @Override
    public boolean pruneInactiveCPUs() {
        if (reservationsPending) return false;
        List<ICraftingPlan> killList = null;
        for (Map.Entry<ICraftingPlan, ECOCraftingCPU> entry : activeCpus.entrySet()) {
            ECOCraftingCPU cpu = entry.getValue();
            if (!cpu.getLogic()
                .hasJob()
                && !cpu.getLogic()
                    .isMarkedForDeletion()
                && !cpu.hasRemainingItems()) {
                if (killList == null) killList = new ArrayList<>();
                killList.add(entry.getKey());
            }
        }
        boolean changed = releaseOrphanedNativeCPUs();
        if (killList == null) return changed;
        for (ICraftingPlan iCraftingPlan : killList) {
            killCpu(iCraftingPlan, false);
        }
        updateGridForChangedCpu();
        return true;
    }

    /** Drops native identities of CPUs that never got (or no longer have) a registered plan, e.g. failed restores. */
    private boolean releaseOrphanedNativeCPUs() {
        int expected = activeCpus.size() + (fakeCpu != null && nativeCpus.containsKey(fakeCpu) ? 1 : 0);
        if (nativeCpus.size() <= expected) return false;
        return nativeCpus.entrySet()
            .removeIf(entry -> {
                if (entry.getKey() == fakeCpu || activeCpus.containsValue(entry.getKey())) return false;
                releaseNativeCPU(entry.getValue());
                return true;
            });
    }

    /**
     * The placeholder CPU reads the cluster's free storage live, so it stays a stable identity for the
     * whole lifetime of the cluster. Recreating it would invalidate any CPU selection referring to it.
     */
    public ECOCraftingCPU getFakeCPU() {
        if (this.fakeCpu == null) {
            this.fakeCpu = new ECOCraftingCPU(this, controller != null ? controller.getTier() : StorageTiers.L4);
        }
        return fakeCpu;
    }

    /**
     * @return true if the given plan currently holds a byte reservation in this cluster.
     */
    public boolean isPlanRegistered(@Nullable ICraftingPlan plan) {
        return plan != null && this.activeCpus.containsKey(plan);
    }

    public void deactivate(@Nullable ICraftingPlan plan) {
        ECOCraftingCPU cpu = removeActiveCpu(plan);
        if (cpu != null) releaseNativeCPU(nativeCpus.remove(cpu));
        this.bigOrderPlans.remove(plan);
        if (!isDestroyed() && (!usesCompactGrid() || !MBCalculator.isModificationInProgress()))
            this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        if (cpu != null && cpu.getOwner() != null) {
            cpu.getOwner()
                .deactivate(cpu);
        }
    }

    public void cancelJob(ICraftingPlan plan) {
        if (this.activeCpus.get(plan) != null) {
            this.killCpu(plan, true);
        }
    }

    private void killCpu(ICraftingPlan plan, boolean update) {
        killCpu(plan, update, true);
    }

    private void killCpu(ICraftingPlan plan, boolean update, boolean recalculate) {
        ECOCraftingCPU cpu = activeCpus.get(plan);
        if (cpu == null) {
            // CPU may have already been removed by another call (e.g., from recalculateRemainingStorage)
            return;
        }
        cpu.getLogic()
            .cancel();
        cpu.getLogic()
            .markForDeletion();
        if (!cpu.hasRemainingItems()) {
            if (cpu.getOwner() != null) {
                cpu.getOwner()
                    .deactivate(cpu);
            }
            removeActiveCpu(plan);
            this.bigOrderPlans.remove(plan);
            releaseNativeCPU(this.nativeCpus.remove(cpu));
        }
        if (recalculate) {
            this.recalculateRemainingStorage();
        }
        if (update) {
            updateGridForChangedCpu();
        }
    }

    /**
     * Tells AE2's ICraftingService to rebuild its CPU cache after this custom cluster
     * becomes visible or changes availability. Unlike a native CraftingCPU, an ECO
     * cluster is not represented by a grid node owned by ICraftingService, so cluster
     * formation must explicitly publish the change.
     */
    public void notifyGridCpuChange() {
        IGridNode node = getNode();
        if (node != null && node.getGrid() != null)
            NELogicalNetworkManager.requestCpuPublication(this, node.getGrid(), node);
    }

    private void updateGridForChangedCpu() {
        notifyGridCpuChange();
    }

    public List<ECOComputationDriveBlockEntity> getUpperDrives() {
        return upperDrives;
    }

    public List<ECOComputationDriveBlockEntity> getLowerDrives() {
        return lowerDrives;
    }

    public List<ECOComputationThreadingCoreBlockEntity> getThreadingCores() {
        return threadingCores;
    }

    public List<ECOComputationParallelCoreBlockEntity> getParallelCores() {
        return parallelCores;
    }

    public ECOComputationSystemBlockEntity getController() {
        return controller;
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    public CpuSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public NEComputationNetworkCluster getNetworkCluster() {
        return networkCluster;
    }

    private final java.util.Map<ECOCraftingCPU, appeng.api.networking.crafting.ICraftingCPU> nativeCpus = new java.util.IdentityHashMap<>();

    public appeng.api.networking.crafting.ICraftingCPU nativeCPU(ECOCraftingCPU cpu) {
        var existing = nativeCpus.get(cpu);
        if (existing != null) return existing;
        // A released CPU keeps the identity its links refer to; never mint a second one for it.
        var bound = cpu.boundNativeCPU();
        if (bound != null) return bound;
        var created = createNativeCPU(cpu);
        nativeCpus.put(cpu, created);
        return created;
    }

    /** Creates the AE2 identity of an ECO CPU; the identity binds itself to the CPU on construction. */
    protected appeng.api.networking.crafting.ICraftingCPU createNativeCPU(ECOCraftingCPU cpu) {
        return new cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu(
            new cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime(cpu));
    }

    /** Whether this host still holds a native identity for the CPU; released CPUs must not. */
    public boolean hasNativeCPU(ECOCraftingCPU cpu) {
        return nativeCpus.containsKey(cpu);
    }

    public net.minecraft.world.World getLevel() {
        return controller == null ? null : controller.getWorldObj();
    }

    public cn.dancingsnow.neoecoae.platform.ae2.grid.GridView getGrid() {
        return controller == null ? null
            : controller.getMainNode()
                .getGrid();
    }

    public cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions getPlanningOptions() {
        return cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions.from(controller);
    }

    /** Finds the CPU running a plan anywhere in this host's admission pool (every network member). */
    @Override
    public ECOCraftingCPU findCPU(ICraftingPlan plan) {
        ECOCraftingCPU own = activeCpus.get(plan);
        if (own != null || networkCluster == null) return own;
        for (NEComputationCluster member : networkCluster.getMembers()) {
            ECOCraftingCPU cpu = member.activeCpus.get(plan);
            if (cpu != null) return cpu;
        }
        return null;
    }

    public java.util.List<ECOComputationDriveBlockEntity> getDrives() {
        var drives = new java.util.ArrayList<ECOComputationDriveBlockEntity>(upperDrives);
        drives.addAll(lowerDrives);
        return drives;
    }
}
