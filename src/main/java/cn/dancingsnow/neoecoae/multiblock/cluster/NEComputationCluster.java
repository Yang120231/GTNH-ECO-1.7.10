package cn.dancingsnow.neoecoae.multiblock.cluster;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
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
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
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
    /** BigInt child plans are segmented by the controller and must not consume the finite CPU byte reservation. */
    private final ReferenceSet<ICraftingPlan> bigOrderPlans = new ReferenceOpenHashSet<>();
    private ECOCraftingCPU fakeCpu;

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
        NELogicalNetworkManager.detachBeforeDestroy(this);
        super.destroy();
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
        return collectStorage(upperDrives) + collectStorage(lowerDrives);
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

    /**
     * Whether AE2 should list this host's placeholder CPU: the group representative while a thread is free,
     * exactly as the 1.21 baseline publishes it. Free bytes are not required; a too-small CPU is still listed.
     */
    public boolean shouldPublishPlaceholder() {
        return isNetworkRepresentative() && hasFreeThread();
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
        this.activeCpus.put(plan, cpu);
        // Restored CPUs are registered after the cluster's initial formed-state capacity calculation.
        // Recalculate immediately so persisted jobs consume their bytes and jobs restored without enough
        // computation cells are cancelled before they can intercept items from network storage.
        this.recalculateRemainingStorage();
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (formed) {
            recalculateRemainingStorage();
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
        IGridNode node = this.getNode();
        return node != null && node.isActive();
    }

    public ICraftingSubmitResult submitJob(GridView grid, ICraftingPlan job, IActionSource src,
        ICraftingRequester requestingMachine) {
        if (this.networkCluster != null) {
            return this.networkCluster.submitJob(grid, job, src, requestingMachine);
        }
        // Finished jobs must not keep their thread or bytes until the next crafting-cache tick.
        pruneInactiveCPUs();
        if (!this.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
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
            threadingCore.deactivate(cpu);
            // Every local CPU sees the same grid inventory. Retrying a confirmed material deficit only repeats the
            // complete extraction/rollback transaction and can multiply external-inventory persistence work.
            if (result.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT) return result;
            lastResult = result;
        }
        return lastResult;
    }

    boolean registerActiveJob(ICraftingPlan job, ECOCraftingCPU cpu) {
        this.activeCpus.put(job, cpu);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        return this.activeCpus.get(job) == cpu && cpu.getLogic()
            .hasJob();
    }

    public void recalculateRemainingStorage() {
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
            this.killCpu(plan, false, false);
        }

        this.availableStorage = Math.max(0, totalStorage - getActiveJobBytes());
    }

    /** Server-thread atomic reservation replacement for a parent's next complete long segment. */
    public boolean replaceBigOrderPlan(ECOCraftingCPU cpu, ICraftingPlan next) {
        ICraftingPlan previous = cpu.getPlan();
        if (previous == null || activeCpus.get(previous) != cpu || next.bytes() < 0) return false;
        activeCpus.remove(previous);
        bigOrderPlans.remove(previous);
        cpu.setBigOrderChildPlan(next);
        activeCpus.put(next, cpu);
        bigOrderPlans.add(next);
        recalculateRemainingStorage();
        return activeCpus.get(next) == cpu;
    }

    private long getActiveJobBytes() {
        long usedStorage = 0L;
        for (ICraftingPlan plan : new ArrayList<>(this.activeCpus.keySet())) {
            if (bigOrderPlans.contains(plan)) continue;
            usedStorage = NEMath.saturatingAdd(usedStorage, Math.max(0L, plan.bytes()));
        }
        return usedStorage;
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        for (Map.Entry<ICraftingPlan, ECOCraftingCPU> entry : new ArrayList<>(activeCpus.entrySet())) {
            ECOCraftingCPU cpu = entry.getValue();
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
        return nativeCpus.keySet()
            .removeIf(cpu -> cpu != fakeCpu && !activeCpus.containsValue(cpu));
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
        ECOCraftingCPU cpu = this.activeCpus.remove(plan);
        if (cpu != null) nativeCpus.remove(cpu);
        this.bigOrderPlans.remove(plan);
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
            this.activeCpus.remove(plan);
            this.bigOrderPlans.remove(plan);
            this.nativeCpus.remove(cpu);
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
        boolean posted = false;

        for (var r : this.blockEntities) {
            IGridNode n = r instanceof appeng.api.networking.security.IActionHost connected
                ? connected.getActionableNode()
                : null;
            if (n != null && n.getGrid() != null && !posted) {
                n.getGrid()
                    .postEvent(new MENetworkCraftingCpuChange(n));
                posted = true;
            }
        }

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
