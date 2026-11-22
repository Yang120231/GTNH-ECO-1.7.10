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
        recoverTerminatedWorkers(level, jobId);
    }

    public static void recoverTerminatedWorkers(@Nullable World level, UUID jobId) {
        for (var worker : ECOCraftingWorkers.forJob(level, jobId)) worker.recoverTerminatedJob(jobId);
    }

    /** Works without decoding a plan, so even a quarantined CPU can terminate its worker ownership. */
    public static void cancelPersistedJob(@Nullable World level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompound("job")
            .getCompound("link");
        if (link.hasUUID("craftId")) finish(level, link.getUUID("craftId"), false);
    }

    public static boolean isPersistedJobTerminated(@Nullable World level, CompoundTag cpuData) {
        if (!cpuData.contains("job")) return false;
        CompoundTag link = cpuData.getCompound("job")
            .getCompound("link");
        UUID jobId = link.hasUUID("craftId") ? link.getUUID("craftId") : UUID.fromString(link.getString("CraftID"));
        return isTerminated(level, jobId);
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
