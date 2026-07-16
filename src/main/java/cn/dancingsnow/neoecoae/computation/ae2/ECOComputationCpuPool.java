package cn.dancingsnow.neoecoae.computation.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostStats;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public final class ECOComputationCpuPool {

    private static final String TAG_VERSION = "EcoComputationCpuPoolVersion";
    private static final String TAG_NEXT_SERIAL = "NextSerial";
    private static final String TAG_CPUS = "Cpus";
    private static final String TAG_SERIAL = "Serial";
    private static final String TAG_RESERVED_STORAGE = "ReservedStorage";
    private static final String TAG_RUNTIME = "Runtime";
    private static final int VERSION = 1;

    private final TileECOInterface host;
    private final List<ECOComputationVirtualCpu> cpus = new ArrayList<ECOComputationVirtualCpu>();
    private final List<ECOComputationVirtualCpu> pendingRelease = new ArrayList<ECOComputationVirtualCpu>();
    private final List<NBTTagCompound> pendingRestore = new ArrayList<NBTTagCompound>();

    private ECOComputationVirtualCpu idleCpu;
    private ICraftingGrid attachedGrid;
    private IGrid grid;
    private long totalStorage;
    private int totalThreads;
    private int coProcessors;
    private ComputationCpuSelectionMode cpuSelectionMode = ComputationCpuSelectionMode.ANY;
    private int nextSerial = 1;
    private boolean syncing;

    public ECOComputationCpuPool(TileECOInterface host) {
        this.host = host;
    }

    public void refresh(IGrid grid, ICraftingGrid craftingGrid, ComputationHostStats stats, boolean active,
        ComputationCpuSelectionMode cpuSelectionMode) {
        boolean gridChanged = this.attachedGrid != craftingGrid;
        if (gridChanged) {
            this.detach();
            this.attachedGrid = craftingGrid;
        }

        this.grid = grid;
        this.totalStorage = stats == null ? 0L : stats.totalBytes;
        this.totalThreads = stats == null ? 0 : stats.totalThreads;
        this.coProcessors = stats == null ? 0 : stats.parallelCount;
        this.cpuSelectionMode = cpuSelectionMode == null ? ComputationCpuSelectionMode.ANY : cpuSelectionMode;

        this.restorePendingCpus(grid, active);
        this.removeFinishedCpus();
        long idleStorage = active && this.runningCpuCount() < this.totalThreads
            ? Math.max(0L, this.totalStorage - this.usedReservedStorage())
            : 0L;
        this.ensureIdleCpu(idleStorage, active);
        this.updateCpuResources(active);

        this.syncCurrent();
    }

    public void detach() {
        if (this.attachedGrid != null) {
            ECOComputationCpuBridge.detach(this.attachedGrid, this);
        }
        this.attachedGrid = null;
        this.grid = null;
    }

    public void readFromNBT(NBTTagCompound tag) {
        this.pendingRestore.clear();
        this.cpus.clear();
        this.pendingRelease.clear();
        this.idleCpu = null;
        this.nextSerial = Math.max(1, tag.getInteger(TAG_NEXT_SERIAL));
        if (tag.getInteger(TAG_VERSION) != VERSION) {
            return;
        }
        NBTTagList cpuTags = tag.getTagList(TAG_CPUS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cpuTags.tagCount(); i++) {
            this.pendingRestore.add(cpuTags.getCompoundTagAt(i));
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(TAG_VERSION, VERSION);
        tag.setInteger(TAG_NEXT_SERIAL, this.nextSerial);
        NBTTagList cpuTags = new NBTTagList();
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (cpu.shouldPersist()) {
                cpuTags.appendTag(this.writeCpu(cpu));
            }
        }
        for (NBTTagCompound pending : this.pendingRestore) {
            cpuTags.appendTag((NBTTagCompound) pending.copy());
        }
        tag.setTag(TAG_CPUS, cpuTags);
    }

    public boolean hasPersistentState() {
        if (!this.pendingRestore.isEmpty()) {
            return true;
        }
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (cpu.shouldPersist()) {
                return true;
            }
        }
        return false;
    }

    public void shutdown() {
        List<ECOComputationVirtualCpu> snapshot = new ArrayList<ECOComputationVirtualCpu>(this.cpus);
        for (ECOComputationVirtualCpu cpu : snapshot) {
            cpu.shutdown();
            if (cpu.shouldPersist()) {
                this.pendingRestore.add(this.writeCpu(cpu));
            }
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
        this.idleCpu.configureIdle(
            required,
            this.coProcessorsFor(this.idleCpu, this.runningCpuCount() + 1),
            this.grid,
            true,
            this.cpuSelectionMode);
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
        boolean active = this.grid != null;
        long idleStorage = active && this.runningCpuCount() < this.totalThreads
            ? Math.max(0L, this.totalStorage - this.usedReservedStorage())
            : 0L;
        this.ensureIdleCpu(idleStorage, active);
        this.updateCpuResources(active);
        this.syncCurrent();
    }

    void onCpuJobFinished(ECOComputationVirtualCpu cpu) {
        this.release(cpu);
        this.host.requestComputationCpuRefresh();
    }

    void requestLinkRebind() {
        this.syncCurrent();
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
        this.idleCpu.configureIdle(storage, 0, this.grid, active, this.cpuSelectionMode);
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

    private void restorePendingCpus(IGrid grid, boolean active) {
        if (this.pendingRestore.isEmpty()) {
            return;
        }
        Iterator<NBTTagCompound> iterator = this.pendingRestore.iterator();
        while (iterator.hasNext()) {
            NBTTagCompound cpuTag = iterator.next();
            ECOComputationVirtualCpu cpu = this.readCpu(cpuTag, grid, active);
            iterator.remove();
            if (cpu != null) {
                this.cpus.add(cpu);
                this.nextSerial = Math.max(this.nextSerial, cpu.serial() + 1);
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

    private NBTTagCompound writeCpu(ECOComputationVirtualCpu cpu) {
        NBTTagCompound cpuTag = new NBTTagCompound();
        cpuTag.setInteger(TAG_SERIAL, cpu.serial());
        cpuTag.setLong(TAG_RESERVED_STORAGE, cpu.reservedStorage());
        cpuTag.setTag(TAG_RUNTIME, cpu.writeRuntimeNBT());
        return cpuTag;
    }

    private ECOComputationVirtualCpu readCpu(NBTTagCompound cpuTag, IGrid grid, boolean active) {
        if (!cpuTag.hasKey(TAG_RUNTIME, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        int serial = Math.max(1, cpuTag.getInteger(TAG_SERIAL));
        long reservedStorage = Math.max(0L, cpuTag.getLong(TAG_RESERVED_STORAGE));
        if (reservedStorage <= 0L) {
            return null;
        }
        ECOComputationVirtualCpu cpu = new ECOComputationVirtualCpu(this, this.host, serial);
        boolean restored = cpu.restoreFromNBT(
            cpuTag.getCompoundTag(TAG_RUNTIME),
            reservedStorage,
            this.coProcessors,
            grid,
            active,
            this.cpuSelectionMode);
        return restored ? cpu : null;
    }

    private void updateCpuResources(boolean active) {
        int participants = this.runningCpuCount() + (this.idleCpu == null ? 0 : 1);
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (this.isPendingRelease(cpu)) {
                continue;
            }
            cpu.updateResources(this.coProcessorsFor(cpu, participants), this.grid, active, this.cpuSelectionMode);
        }
    }

    private int coProcessorsFor(ECOComputationVirtualCpu target, int participants) {
        if (target == null || participants <= 0 || this.coProcessors <= 0) {
            return 0;
        }
        int base = this.coProcessors / participants;
        int remainder = this.coProcessors % participants;
        int rank = 0;
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (cpu == target) {
                break;
            }
            if (cpu == this.idleCpu || this.isRunningCpu(cpu)) {
                rank++;
            }
        }
        return base + (rank < remainder ? 1 : 0);
    }

    private int runningCpuCount() {
        int count = 0;
        for (ECOComputationVirtualCpu cpu : this.cpus) {
            if (this.isRunningCpu(cpu)) {
                count++;
            }
        }
        return count;
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
