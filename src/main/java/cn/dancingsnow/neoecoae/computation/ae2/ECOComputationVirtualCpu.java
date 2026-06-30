package cn.dancingsnow.neoecoae.computation.ae2;

import java.util.Collections;
import java.util.Iterator;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.CraftingAllow;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.WorldCoord;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingThread;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public class ECOComputationVirtualCpu extends CraftingCPUCluster {

    private static final String TAG_BATCH_RUNTIME = "EcoBatchRuntime";

    private final ECOComputationCpuPool pool;
    private final TileECOInterface host;
    private final int serial;
    private final ECOCraftingThread batchRuntime = new ECOCraftingThread();

    private long reservedStorage;
    private int coProcessors;
    private boolean active;
    private IGrid grid;
    private boolean released;
    private boolean reservedForJob;
    private CraftingAllow craftingAllowMode = CraftingAllow.ALLOW_ALL;

    ECOComputationVirtualCpu(ECOComputationCpuPool pool, TileECOInterface host, int serial) {
        super(
            new WorldCoord(host.xCoord, host.yCoord, host.zCoord),
            new WorldCoord(host.xCoord, host.yCoord, host.zCoord));
        this.pool = pool;
        this.host = host;
        this.serial = serial;
        this.machineSrc = new MachineSource(host);
        this.isComplete = true;
    }

    void configureIdle(long storage, int coProcessors, IGrid grid, boolean active,
        ComputationCpuSelectionMode cpuSelectionMode) {
        this.reservedStorage = Math.max(0L, storage);
        this.availableStorage = this.reservedStorage;
        this.reservedForJob = false;
        this.updateResources(coProcessors, grid, active, cpuSelectionMode);
        this.myName = this.pool.nameFor(this.serial, false);
    }

    boolean belongsToPool(ECOComputationCpuPool owner) {
        return this.pool == owner;
    }

    long reservedStorage() {
        return this.reservedStorage;
    }

    int serial() {
        return this.serial;
    }

    long usedStorageForDisplay() {
        return this.isBusy() ? this.getUsedStorage() : 0L;
    }

    boolean shouldPersist() {
        return !this.released && (this.hasAllocatedJobState() || super.isBusy()
            || this.getLastCraftingLink() != null
            || this.getUsedStorage() > 0L
            || !this.inventory.isEmpty());
    }

    ComputationTaskInfo taskEntry() {
        if (!this.isBusy()) {
            return null;
        }
        IAEItemStack output = Platform.stackConvertPacket(this.getFinalMultiOutput());
        return new ComputationTaskInfo(output == null ? null : output.getItemStack(), this.getElapsedTime());
    }

    void updateResources(int coProcessors, IGrid grid, boolean active, ComputationCpuSelectionMode cpuSelectionMode) {
        this.coProcessors = Math.max(0, coProcessors);
        this.accelerator = this.coProcessors;
        this.grid = grid;
        this.active = active;
        this.craftingAllowMode = allowMode(cpuSelectionMode);
    }

    boolean restoreFromNBT(NBTTagCompound data, long reservedStorage, int coProcessors, IGrid grid, boolean active,
        ComputationCpuSelectionMode cpuSelectionMode) {
        this.reservedStorage = Math.max(0L, reservedStorage);
        this.availableStorage = this.reservedStorage;
        this.updateResources(coProcessors, grid, active, cpuSelectionMode);
        this.released = false;
        this.machineSrc = new MachineSource(this.host);
        try {
            super.readFromNBT(data);
        } catch (RuntimeException e) {
            return false;
        }
        this.readBatchRuntime(data);
        this.reservedForJob = this.hasRestoredJobState();
        this.myName = this.pool.nameFor(this.serial, true);
        return this.shouldPersist();
    }

    NBTTagCompound writeRuntimeNBT() {
        NBTTagCompound data = new NBTTagCompound();
        super.writeToNBT(data);
        this.writeBatchRuntime(data);
        return data;
    }

    @Override
    public ICraftingLink submitJob(IGrid grid, ICraftingJob job, BaseActionSource src,
        ICraftingRequester requestingMachine) {
        if (this.released) {
            return null;
        }
        if (!this.pool.tryReserve(this, job)) {
            return null;
        }
        ICraftingLink link = super.submitJob(grid, job, src, requestingMachine);
        if (link == null) {
            this.releaseFromPool();
        } else {
            this.reservedForJob = true;
            this.onBatchJobAccepted(job);
            this.myName = this.pool.nameFor(this.serial, true);
            this.pool.onCpuJobAccepted(this);
        }
        return link;
    }

    @Override
    protected void completeJob() {
        super.completeJob();
        this.completeBatchJob();
        this.releaseFromPool();
    }

    @Override
    public void cancel() {
        super.cancel();
        this.failBatchJob("cpu canceled");
        this.releaseFromPool();
    }

    @Override
    public void destroy() {
        this.active = false;
        this.disableBatchRuntime("cpu destroyed");
        this.releaseFromPool();
        super.destroy();
    }

    void shutdown() {
        this.active = false;
        if (this.isBusy()) {
            this.cancel();
            return;
        }
        this.releaseFromPool();
    }

    @Override
    public void updateCraftingLogic(IGrid grid, IEnergyGrid energyGrid, CraftingGridCache craftingGrid) {
        if (!this.isActive()) {
            return;
        }
        this.tickBatchRuntime();

        if (this.myLastLink != null && this.myLastLink.isCanceled()) {
            this.myLastLink = null;
            this.cancel();
        }

        if (this.isComplete) {
            if (!this.inventory.isEmpty()) {
                this.storeItems();
            }
            return;
        }

        this.waiting = false;
        if (this.tasks.isEmpty()) {
            return;
        }

        this.remainingOperations = this.accelerator + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        int started = this.remainingOperations;
        this.workableTasks.clear();
        this.workableTasks.putAll(this.tasks);
        this.knownBusyMediums.clear();
        if (this.remainingOperations > 0) {
            do {
                this.somethingChanged = false;
                this.executeCrafting(energyGrid, craftingGrid);
            } while (this.somethingChanged && this.remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - this.remainingOperations;
        this.knownBusyMediums.clear();

        if (this.remainingOperations > 0 && !this.somethingChanged) {
            this.waiting = true;
        }
    }

    @Override
    public void markDirty() {
        this.host.markDirty();
    }

    @Override
    public IGrid getGrid() {
        return this.grid;
    }

    @Override
    public boolean isActive() {
        return !this.released && this.active && this.host.isComputationCpuOnline();
    }

    @Override
    public boolean isBusy() {
        return super.isBusy() || this.hasAllocatedJobState();
    }

    @Override
    public long getAvailableStorage() {
        return this.reservedStorage;
    }

    @Override
    public int getCoProcessors() {
        return this.coProcessors;
    }

    @Override
    public BaseActionSource getActionSource() {
        return this.machineSrc;
    }

    @Override
    public CraftingAllow getCraftingAllowMode() {
        return this.craftingAllowMode;
    }

    @Override
    public void changeCraftingAllowMode(CraftingAllow mode) {}

    @Override
    public String getName() {
        return this.myName;
    }

    @Override
    public Iterator<IGridHost> getTiles() {
        return Collections.<IGridHost>singleton(this.host)
            .iterator();
    }

    @Override
    public void updateStatus(boolean updateGrid) {}

    @Override
    public void updateName() {
        this.myName = this.pool.nameFor(this.serial, this.isBusy());
    }

    @Override
    public void breakCluster() {
        this.destroy();
    }

    @Override
    protected net.minecraft.world.World getWorld() {
        return this.host.getWorldObj();
    }

    @Override
    protected void done() {}

    private void releaseFromPool() {
        if (this.released) {
            return;
        }
        this.reservedForJob = false;
        this.released = true;
        this.active = false;
        this.pool.onCpuJobFinished(this);
    }

    private boolean hasAllocatedJobState() {
        return !this.released && this.reservedForJob && !this.isComplete;
    }

    private boolean hasRestoredJobState() {
        return !this.isComplete || super.isBusy()
            || this.getLastCraftingLink() != null
            || this.getUsedStorage() > 0L
            || !this.inventory.isEmpty();
    }

    private void readBatchRuntime(NBTTagCompound data) {
        if (!data.hasKey(TAG_BATCH_RUNTIME)) {
            return;
        }
        try {
            this.batchRuntime.readFromNBT(data.getCompoundTag(TAG_BATCH_RUNTIME));
        } catch (RuntimeException e) {
            this.batchRuntime.disable(
                e.getClass()
                    .getSimpleName());
        }
    }

    private void writeBatchRuntime(NBTTagCompound data) {
        try {
            NBTTagCompound batchTag = new NBTTagCompound();
            this.batchRuntime.writeToNBT(batchTag);
            data.setTag(TAG_BATCH_RUNTIME, batchTag);
        } catch (RuntimeException ignored) {}
    }

    private void onBatchJobAccepted(ICraftingJob job) {
        try {
            this.batchRuntime.enqueue(job);
        } catch (RuntimeException e) {
            this.batchRuntime.disable(
                e.getClass()
                    .getSimpleName());
        }
    }

    private void tickBatchRuntime() {
        try {
            this.batchRuntime.tick(this.accelerator + 1);
        } catch (RuntimeException e) {
            this.batchRuntime.disable(
                e.getClass()
                    .getSimpleName());
        }
    }

    private void completeBatchJob() {
        try {
            this.batchRuntime.completeCurrent();
        } catch (RuntimeException e) {
            this.batchRuntime.disable(
                e.getClass()
                    .getSimpleName());
        }
    }

    private void failBatchJob(String reason) {
        try {
            this.batchRuntime.failCurrent(reason);
        } catch (RuntimeException e) {
            this.batchRuntime.disable(
                e.getClass()
                    .getSimpleName());
        }
    }

    private void disableBatchRuntime(String reason) {
        try {
            this.batchRuntime.disable(reason);
        } catch (RuntimeException ignored) {}
    }

    private static CraftingAllow allowMode(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            return CraftingAllow.ONLY_PLAYER;
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            return CraftingAllow.ONLY_NONPLAYER;
        }
        return CraftingAllow.ALLOW_ALL;
    }
}
