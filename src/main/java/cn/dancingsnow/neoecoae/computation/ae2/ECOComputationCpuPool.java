package cn.dancingsnow.neoecoae.computation.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostStats;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public final class ECOComputationCpuPool {

    private final TileECOInterface host;
    private final List<ECOComputationVirtualCpu> cpus = new ArrayList<ECOComputationVirtualCpu>();
    private final List<ECOComputationVirtualCpu> pendingRelease = new ArrayList<ECOComputationVirtualCpu>();

    private ECOComputationVirtualCpu idleCpu;
    private ICraftingGrid attachedGrid;
    private IGrid grid;
    private long totalStorage;
    private int coProcessors;
    private int nextSerial = 1;
    private boolean syncing;

    public ECOComputationCpuPool(TileECOInterface host) {
        this.host = host;
    }

    public void refresh(IGrid grid, ICraftingGrid craftingGrid, ComputationHostStats stats, boolean active) {
        boolean gridChanged = this.attachedGrid != craftingGrid;
        if (gridChanged) {
            this.detach();
            this.attachedGrid = craftingGrid;
        }

        this.grid = grid;
        this.totalStorage = stats == null ? 0L : stats.totalBytes;
        this.coProcessors = stats == null ? 0 : stats.parallelCount;

        this.removeFinishedCpus();
        long idleStorage = active ? Math.max(0L, this.totalStorage - this.usedReservedStorage()) : 0L;
        this.ensureIdleCpu(idleStorage, active);
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            cpu.updateGrid(grid, active);
        }

        this.syncCurrent();
    }

    public void detach() {
        if (this.attachedGrid != null) {
            ECOComputationCpuBridge.detach(this.attachedGrid, this);
        }
        this.attachedGrid = null;
        this.grid = null;
    }

    public void shutdown() {
        List<ECOComputationVirtualCpu> snapshot = new ArrayList<ECOComputationVirtualCpu>(this.cpus);
        for (ECOComputationVirtualCpu cpu : snapshot) {
            cpu.shutdown();
        }
        this.cpus.clear();
        this.pendingRelease.clear();
        this.idleCpu = null;
        this.detach();
    }

    public List<ECOComputationVirtualCpu> cpus() {
        return Collections.unmodifiableList(this.cpus);
    }

    public long activeThreadCount() {
        long count = 0L;
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (this.isRunningCpu(cpu)) {
                count++;
            }
        }
        return count;
    }

    public long usedStorageBytes() {
        long used = 0L;
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (this.isPendingRelease(cpu)) {
                continue;
            }
            long usedStorage = cpu.usedStorageForDisplay();
            used = Long.MAX_VALUE - used < usedStorage ? Long.MAX_VALUE : used + usedStorage;
        }
        return used;
    }

    public List<ComputationTaskInfo> taskEntries() {
        List<ComputationTaskInfo> entries = new ArrayList<ComputationTaskInfo>();
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (cpu == this.idleCpu || this.isPendingRelease(cpu)) {
                continue;
            }
            ComputationTaskInfo entry = cpu.taskEntry();
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    boolean tryReserve(ECOComputationVirtualCpu cpu, ICraftingJob job) {
        if (cpu != this.idleCpu || job == null) {
            return false;
        }
        long required = job.getByteTotal();
        if (required <= 0L || required > this.idleCpu.reservedStorage()) {
            return false;
        }
        this.idleCpu.configureIdle(required, this.coProcessors, this.grid, true);
        this.idleCpu = null;
        return true;
    }

    void release(ECOComputationVirtualCpu cpu) {
        if (cpu == null) {
            return;
        }
        if (!this.pendingRelease.contains(cpu)) {
            this.pendingRelease.add(cpu);
        }
        if (cpu == this.idleCpu) {
            this.idleCpu = null;
        }
    }

    void onCpuJobAccepted(ECOComputationVirtualCpu cpu) {
        this.ensureIdleCpu(Math.max(0L, this.totalStorage - this.usedReservedStorage()), this.grid != null);
        this.syncCurrent();
    }

    void onCpuJobFinished(ECOComputationVirtualCpu cpu) {
        this.release(cpu);
    }

    String nameFor(int serial, boolean busy) {
        return busy ? "ECO Computation #" + serial : "ECO Computation Free";
    }

    private void ensureIdleCpu(long storage, boolean active) {
        if (storage <= 0L) {
            if (this.idleCpu != null) {
                this.cpus.remove(this.idleCpu);
                this.idleCpu = null;
            }
            return;
        }
        if (this.idleCpu == null || !this.cpus.contains(this.idleCpu)) {
            this.idleCpu = new ECOComputationVirtualCpu(this, this.host, this.nextSerial++);
            this.cpus.add(this.idleCpu);
        }
        this.idleCpu.configureIdle(storage, this.coProcessors, this.grid, active);
    }

    private void syncCurrent() {
        if (this.attachedGrid != null && !this.syncing) {
            this.syncing = true;
            try {
                ECOComputationCpuBridge.sync(this.attachedGrid, this, this.registeredCpus());
            } finally {
                this.syncing = false;
            }
        }
    }

    private void removeFinishedCpus() {
        Iterator<ECOComputationVirtualCpu> iterator = this.cpus.iterator();
        while (iterator.hasNext()) {
            ECOComputationVirtualCpu cpu = iterator.next();
            if (this.isPendingRelease(cpu) || cpu != this.idleCpu && !cpu.isBusy()) {
                if (cpu == this.idleCpu) {
                    this.idleCpu = null;
                }
                iterator.remove();
            }
        }
        this.pendingRelease.clear();
    }

    private List<ECOComputationVirtualCpu> registeredCpus() {
        if (this.pendingRelease.isEmpty()) {
            return this.cpus;
        }
        List<ECOComputationVirtualCpu> registered = new ArrayList<ECOComputationVirtualCpu>();
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (!this.isPendingRelease(cpu)) {
                registered.add(cpu);
            }
        }
        return registered;
    }

    private boolean isRunningCpu(ECOComputationVirtualCpu cpu) {
        return cpu != this.idleCpu && !this.isPendingRelease(cpu) && cpu.isBusy();
    }

    private boolean isPendingRelease(ECOComputationVirtualCpu cpu) {
        return this.pendingRelease.contains(cpu);
    }

    private long usedReservedStorage() {
        long used = 0L;
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (this.isRunningCpu(cpu)) {
                long reserved = cpu.reservedStorage();
                used = Long.MAX_VALUE - used < reserved ? Long.MAX_VALUE : used + reserved;
            }
        }
        return used;
    }
}
