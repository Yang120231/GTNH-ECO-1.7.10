package cn.dancingsnow.neoecoae.crafting.runtime;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.computation.ae2.ECOComputationVirtualCpu;
import cn.dancingsnow.neoecoae.tile.TileECOController;

/** Tracks loaded workers that own inputs extracted by a specific ECO CPU job. */
public final class ECOCraftingOwnershipRegistry {

    private static final Map<String, WeakReference<ECOComputationVirtualCpu>> ACTIVE_JOBS =
        new HashMap<String, WeakReference<ECOComputationVirtualCpu>>();
    private static final Map<String, WeakReference<TileECOController>> OWNERS =
        new HashMap<String, WeakReference<TileECOController>>();

    private ECOCraftingOwnershipRegistry() {}

    public static synchronized void heartbeat(String jobId, ECOComputationVirtualCpu cpu) {
        if (valid(jobId) && cpu != null) {
            ACTIVE_JOBS.put(jobId, new WeakReference<ECOComputationVirtualCpu>(cpu));
        }
    }

    public static synchronized boolean isActive(String jobId) {
        return activeCpu(jobId) != null;
    }

    public static long injectOwnedOutput(String jobId, ItemStack prototype, long amount) {
        if (!valid(jobId) || prototype == null || amount <= 0L) {
            return 0L;
        }
        ECOComputationVirtualCpu cpu;
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

    private static ECOComputationVirtualCpu activeCpu(String jobId) {
        if (!valid(jobId)) {
            return null;
        }
        WeakReference<ECOComputationVirtualCpu> reference = ACTIVE_JOBS.get(jobId);
        ECOComputationVirtualCpu cpu = reference == null ? null : reference.get();
        if (cpu == null || !cpu.ownsCraftingJob(jobId)) {
            ACTIVE_JOBS.remove(jobId);
            return null;
        }
        return cpu;
    }

    public static synchronized void register(String jobId, TileECOController controller) {
        if (!valid(jobId) || controller == null) {
            return;
        }
        OWNERS.put(jobId, new WeakReference<TileECOController>(controller));
    }

    public static synchronized void unregister(String jobId, TileECOController controller) {
        if (!valid(jobId) || controller == null) {
            return;
        }
        WeakReference<TileECOController> reference = OWNERS.get(jobId);
        TileECOController existing = reference == null ? null : reference.get();
        if (existing == null || existing == controller) {
            OWNERS.remove(jobId);
        }
    }

    public static void cancelAndRecover(String jobId) {
        TileECOController controller = loadedOwner(jobId);
        synchronized (ECOCraftingOwnershipRegistry.class) {
            ACTIVE_JOBS.remove(jobId);
        }
        if (controller != null) {
            controller.recoverVirtualCraftingJob(jobId);
        }
    }

    public static void completeAndRecoverUnfinished(String jobId) {
        TileECOController controller = loadedOwner(jobId);
        synchronized (ECOCraftingOwnershipRegistry.class) {
            ACTIVE_JOBS.remove(jobId);
        }
        if (controller != null) {
            controller.recoverVirtualCraftingUnfinishedInputs(jobId);
        }
    }

    private static synchronized TileECOController loadedOwner(String jobId) {
        WeakReference<TileECOController> reference = OWNERS.remove(jobId);
        TileECOController controller = reference == null ? null : reference.get();
        return controller == null || controller.isInvalid() ? null : controller;
    }

    private static boolean valid(String jobId) {
        return jobId != null && jobId.length() > 0;
    }
}
