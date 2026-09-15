package cn.dancingsnow.neoecoae.crafting.runtime;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.tile.TileECOController;

/** Tracks loaded workers that own inputs extracted by a specific ECO CPU job. */
public final class ECOCraftingOwnershipRegistry {

    private static final Map<String, WeakReference<CraftingCPUCluster>> ACTIVE_JOBS = new HashMap<String, WeakReference<CraftingCPUCluster>>();
    private static final Map<String, java.util.Set<TileECOController>> OWNERS = new HashMap<>();

    private ECOCraftingOwnershipRegistry() {}

    public static synchronized void heartbeat(String jobId, CraftingCPUCluster cpu) {
        if (valid(jobId) && cpu != null) {
            ACTIVE_JOBS.put(jobId, new WeakReference<CraftingCPUCluster>(cpu));
        }
    }

    public static synchronized boolean isActive(String jobId) {
        return activeCpu(jobId) != null;
    }

    public static long injectOwnedOutput(String jobId, ItemStack prototype, long amount) {
        if (!valid(jobId) || prototype == null || amount <= 0L) {
            return 0L;
        }
        CraftingCPUCluster cpu;
        synchronized (ECOCraftingOwnershipRegistry.class) {
            cpu = activeCpu(jobId);
        }
        if (cpu == null) {
            return 0L;
        }
        IAEItemStack input = AEItemStack.create(prototype);
        if (input == null) {
            return 0L;
        }
        input.setStackSize(amount);
        IAEStack<?> leftover = cpu.injectItems(input, Actionable.MODULATE, cpu.getActionSource());
        long remaining = leftover == null ? 0L : Math.max(0L, Math.min(amount, leftover.getStackSize()));
        return amount - remaining;
    }

    private static CraftingCPUCluster activeCpu(String jobId) {
        if (!valid(jobId)) {
            return null;
        }
        WeakReference<CraftingCPUCluster> reference = ACTIVE_JOBS.get(jobId);
        CraftingCPUCluster cpu = reference == null ? null : reference.get();
        if (cpu == null || (!cpu.isActive() || !cpu.isBusy()
            || cpu.getLastCraftingLink() == null
            || !jobId.equals(
                cpu.getLastCraftingLink()
                    .getCraftingID()))) {
            ACTIVE_JOBS.remove(jobId);
            return null;
        }
        return cpu;
    }

    public static synchronized void register(String jobId, TileECOController controller) {
        if (!valid(jobId) || controller == null) {
            return;
        }
        OWNERS.computeIfAbsent(jobId, ignored -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()))
            .add(controller);
    }

    public static synchronized void unregister(String jobId, TileECOController controller) {
        if (!valid(jobId) || controller == null) {
            return;
        }
        java.util.Set<TileECOController> owners = OWNERS.get(jobId);
        if (owners == null) return;
        owners.remove(controller);
        if (owners.isEmpty()) OWNERS.remove(jobId);
    }

    public static void cancelAndRecover(String jobId) {
        java.util.List<TileECOController> owners = loadedOwners(jobId);
        synchronized (ECOCraftingOwnershipRegistry.class) {
            ACTIVE_JOBS.remove(jobId);
        }
        for (TileECOController controller : owners) {
            controller.recoverVirtualCraftingJob(jobId);
        }
    }

    public static void completeAndRecoverUnfinished(String jobId) {
        java.util.List<TileECOController> owners = loadedOwners(jobId);
        synchronized (ECOCraftingOwnershipRegistry.class) {
            ACTIVE_JOBS.remove(jobId);
        }
        for (TileECOController controller : owners) {
            controller.recoverVirtualCraftingUnfinishedInputs(jobId);
        }
    }

    private static synchronized java.util.List<TileECOController> loadedOwners(String jobId) {
        java.util.Set<TileECOController> owners = OWNERS.remove(jobId);
        java.util.List<TileECOController> result = new java.util.ArrayList<>();
        if (owners != null) for (TileECOController owner : owners) {
            if (owner != null && !owner.isInvalid()) result.add(owner);
        }
        return result;
    }

    private static boolean valid(String jobId) {
        return jobId != null && jobId.length() > 0;
    }
}
