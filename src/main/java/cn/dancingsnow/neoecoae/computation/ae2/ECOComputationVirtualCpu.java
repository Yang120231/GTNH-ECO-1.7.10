package cn.dancingsnow.neoecoae.computation.ae2;

import java.util.Collections;
import java.util.Iterator;

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
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public class ECOComputationVirtualCpu extends CraftingCPUCluster {

    private final ECOComputationCpuPool pool;
    private final TileECOInterface host;
    private final int serial;

    private long reservedStorage;
    private int coProcessors;
    private boolean active;
    private IGrid grid;
    private boolean released;

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

    void configureIdle(long storage, int coProcessors, IGrid grid, boolean active) {
        this.reservedStorage = Math.max(0L, storage);
        this.availableStorage = this.reservedStorage;
        this.coProcessors = Math.max(0, coProcessors);
        this.accelerator = this.coProcessors;
        this.grid = grid;
        this.active = active;
        this.myName = this.pool.nameFor(this.serial, false);
    }

    boolean belongsToPool(ECOComputationCpuPool owner) {
        return this.pool == owner;
    }

    long reservedStorage() {
        return this.reservedStorage;
    }

    long usedStorageForDisplay() {
        return this.isBusy() ? this.getUsedStorage() : 0L;
    }

    ComputationTaskInfo taskEntry() {
        if (!this.isBusy()) {
            return null;
        }
        IAEItemStack output = Platform.stackConvertPacket(this.getFinalMultiOutput());
        return new ComputationTaskInfo(output == null ? null : output.getItemStack(), this.getElapsedTime());
    }

    void updateGrid(IGrid grid, boolean active) {
        this.grid = grid;
        this.active = active;
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
            this.myName = this.pool.nameFor(this.serial, true);
            this.pool.onCpuJobAccepted(this);
        }
        return link;
    }

    @Override
    protected void completeJob() {
        super.completeJob();
        this.releaseFromPool();
    }

    @Override
    public void cancel() {
        super.cancel();
        this.releaseFromPool();
    }

    @Override
    public void destroy() {
        this.active = false;
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
        this.released = true;
        this.active = false;
        this.pool.onCpuJobFinished(this);
    }
}
