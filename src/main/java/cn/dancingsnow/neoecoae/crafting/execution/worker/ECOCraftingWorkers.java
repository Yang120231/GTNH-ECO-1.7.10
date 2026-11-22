package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage;

/** Loaded worker identity index. Missing/unloaded workers remain governed by durable job decisions. */
public final class ECOCraftingWorkers {

    private static final Map<ECOCraftingWorkerHandle, Set<UUID>> LOADED = new WeakHashMap<>();
    private static final Map<UUID, Set<ECOCraftingWorkerHandle>> BY_JOB = new HashMap<>();

    private ECOCraftingWorkers() {}

    public static synchronized void loaded(ECOCraftingWorkerHandle worker) {
        LOADED.putIfAbsent(worker, Collections.emptySet());
    }

    public static synchronized void unloaded(ECOCraftingWorkerHandle worker) {
        var previous = LOADED.remove(worker);
        if (previous != null) for (var jobId : previous) remove(jobId, worker);
    }

    public static synchronized void updateJobs(ECOCraftingWorkerHandle worker, Set<UUID> jobs) {
        var previous = LOADED.get(worker);
        if (previous == null || previous.equals(jobs)) return;
        for (var jobId : previous) if (!jobs.contains(jobId)) remove(jobId, worker);
        for (var jobId : jobs) {
            if (!previous.contains(jobId)) {
                BY_JOB.computeIfAbsent(jobId, ignored -> Collections.newSetFromMap(new WeakHashMap<>()))
                    .add(worker);
            }
        }
        LOADED.put(worker, jobs);
    }

    public static synchronized List<ECOCraftingWorkerHandle> forJob(World level, UUID jobId) {
        var workers = BY_JOB.get(jobId);
        var server = level == null ? null : NativeWorldStorage.server(level);
        if (server == null || workers == null) return Collections.emptyList();
        List<ECOCraftingWorkerHandle> result = new ArrayList<>(workers.size());
        for (var worker : workers) {
            var workerLevel = worker.getNativeLevel();
            if (workerLevel != null && NativeWorldStorage.server(workerLevel) == server) result.add(worker);
        }
        return result;
    }

    private static void remove(UUID jobId, ECOCraftingWorkerHandle worker) {
        var workers = BY_JOB.get(jobId);
        if (workers == null) return;
        workers.remove(worker);
        if (workers.isEmpty()) BY_JOB.remove(jobId);
    }
}
