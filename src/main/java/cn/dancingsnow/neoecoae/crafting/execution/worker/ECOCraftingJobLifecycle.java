package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/** Durable terminal decisions shared by CPU owners and workers, including unloaded workers. */
public final class ECOCraftingJobLifecycle extends net.minecraft.world.WorldSavedData {

    private static final String DATA_NAME = "neoecoae_crafting_job_lifecycle";

    // Do not expire decisions by time: a worker can remain unloaded for arbitrarily long.
    // Unknown jobs remain resumable; absence of a live CPU is never evidence of cancellation.
    private final Map<UUID, Boolean> terminatedJobs = new HashMap<>();

    private static @Nullable ECOCraftingJobLifecycle get(@Nullable World level) {
        if (!(level instanceof WorldServer)) return null;
        var server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) return null;
        var world = server.worldServerForDimension(0);
        if (world == null) return null;
        var data = (ECOCraftingJobLifecycle) world.mapStorage.loadData(ECOCraftingJobLifecycle.class, DATA_NAME);
        if (data == null) {
            data = new ECOCraftingJobLifecycle();
            world.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    public static boolean isTerminated(@Nullable World level, @Nullable UUID jobId) {
        if (jobId == null) return false;
        var data = get(level);
        return data != null && data.terminatedJobs.containsKey(jobId);
    }

    /** Publish before clearing the owner's job or detaching its grid. First terminal decision wins. */
    public static void finish(@Nullable World level, @Nullable UUID jobId, boolean completed) {
        if (jobId == null) return;
        var data = get(level);
        if (data != null && data.terminatedJobs.putIfAbsent(jobId, completed) == null) data.markDirty();
    }

    /**
     * Terminates a job owned by an external AE2 CPU and wakes every loaded ECO worker that still holds its
     * batch. External CPUs clear their own inventory during cancellation, so the worker must learn about the
     * terminal decision before that inventory is gone; otherwise a completed fast-path batch can remain owned by
     * an F host until the orphan timeout.
     */
    public static void cancelAndRecover(@Nullable World level, @Nullable UUID jobId) {
        if (jobId == null) return;
        finish(level, jobId, false);
        if (level == null || cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level) == null)
            return;
        for (var worker : cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkers.loaded()) {
            if (worker.getNativeLevel() != null
                && cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(worker.getNativeLevel())
                    == cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level)) {
                worker.recoverTerminatedJob(jobId);
            }
        }
    }

    /** Works without decoding a plan, so even a quarantined CPU can terminate its worker ownership. */
    public static void cancelPersistedJob(@Nullable World level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompound("job")
            .getCompound("link");
        if (link.hasUUID("craftId")) finish(level, link.getUUID("craftId"), false);
    }

    /**
     * Repairs the obsolete restore-failure cancellation marker once the complete persisted CPU has decoded again.
     * Completed jobs remain terminal; only the false marker formerly written by the quarantine path is removed.
     */
    public static void resumePersistedJob(@Nullable World level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompound("job")
            .getCompound("link");
        if (!link.hasUUID("craftId")) return;
        var data = get(level);
        if (data != null && Boolean.FALSE.equals(data.terminatedJobs.remove(link.getUUID("craftId")))) {
            data.markDirty();
        }
    }

    private static ECOCraftingJobLifecycle load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ECOCraftingJobLifecycle();
        ListTag jobs = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag job = jobs.getCompound(i);
            if (job.hasUUID("id")) data.terminatedJobs.put(job.getUUID("id"), job.getBoolean("completed"));
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag jobs = new ListTag();
        terminatedJobs.forEach((id, completed) -> {
            CompoundTag job = new CompoundTag();
            job.putUUID("id", id);
            job.putBoolean("completed", completed);
            jobs.add(job);
        });
        tag.put("jobs", jobs);
        return tag;
    }

    public ECOCraftingJobLifecycle() {
        this(DATA_NAME);
    }

    public ECOCraftingJobLifecycle(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(net.minecraft.nbt.NBTTagCompound data) {
        terminatedJobs.clear();
        terminatedJobs.putAll(
            load(
                new CompoundTag(data),
                cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY).terminatedJobs);
    }

    @Override
    public void writeToNBT(net.minecraft.nbt.NBTTagCompound data) {
        save(new CompoundTag(data), cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY);
    }
}
