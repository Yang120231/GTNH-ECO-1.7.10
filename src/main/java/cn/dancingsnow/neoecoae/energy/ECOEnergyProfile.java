package cn.dancingsnow.neoecoae.energy;

import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathConfig;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public final class ECOEnergyProfile {

    public static final int CRAFTING_ENERGY_GAUGE_REFERENCE = 1000000;
    public static final int CRAFTING_BASE_WORK_POWER = 100;
    public static final int CRAFTING_COOLANT_PER_CRAFT = 5;
    public static final int CRAFTING_WORK_MAX_PROGRESS = 100;
    public static final int CRAFTING_MAX_WORK_POWER_PER_TICK = 500000;
    public static final double STORAGE_DRIVE_BASE_IDLE_POWER = 256.0D;
    public static final double INTERFACE_IDLE_POWER = 16.0D;

    private ECOEnergyProfile() {}

    public static int tierIndex(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 3;
        }
        if (tier == ECOControllerTier.L6) {
            return 2;
        }
        return 1;
    }

    public static int craftingParallel(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 256;
        }
        if (tier == ECOControllerTier.L6) {
            return 72;
        }
        return 24;
    }

    public static int overclockedCraftingParallel(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 384;
        }
        if (tier == ECOControllerTier.L6) {
            return 96;
        }
        return 32;
    }

    public static int computationAccelerators(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 576;
        }
        if (tier == ECOControllerTier.L6) {
            return 192;
        }
        return 64;
    }

    public static int computationThreads(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 64;
        }
        if (tier == ECOControllerTier.L6) {
            return 16;
        }
        return 4;
    }

    public static long computationBytes(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 1L << 30;
        }
        if (tier == ECOControllerTier.L6) {
            return 1L << 28;
        }
        return 1L << 26;
    }

    public static long storageBytes(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 1L << 36;
        }
        if (tier == ECOControllerTier.L6) {
            return 1L << 34;
        }
        return 1L << 30;
    }

    public static long powerStorageSize(ECOControllerTier tier) {
        if (tier == ECOControllerTier.L9) {
            return 1000000000L;
        }
        if (tier == ECOControllerTier.L6) {
            return 100000000L;
        }
        return 10000000L;
    }

    public static double storageSystemIdlePower(ECOControllerTier tier) {
        return 256.0D + (1 << (1 + 4 * tierIndex(tier)));
    }

    public static double storageCellIdlePower(long bytes) {
        return Math.max(0.5D, (double) Math.max(0L, bytes) / (double) (1L << 20));
    }

    public static int overclockedCraftingQueueMultiplier(ECOControllerTier tier) {
        return 2 << tierIndex(tier);
    }

    public static int overclockedCraftingPowerMultiplier(ECOControllerTier tier) {
        return overclockedCraftingQueueMultiplier(tier);
    }

    public static int craftingThreadCapacity(int workerCount, ECOControllerTier tier, boolean overclocked) {
        int perWorker = overclocked ? 32 * overclockedCraftingQueueMultiplier(tier) : 32;
        long capacity = (long) Math.max(0, workerCount) * (long) perWorker;
        return capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    public static int craftingMaxEnergyUsage(int workerCount, ECOControllerTier tier, boolean overclocked,
        boolean activeCooling) {
        long availableThreads = craftingThreadCapacity(workerCount, tier, overclocked);
        long usage = availableThreads * CRAFTING_BASE_WORK_POWER;
        if (overclocked && !activeCooling) {
            usage *= overclockedCraftingPowerMultiplier(tier);
        }
        return usage > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, usage);
    }

    public static int craftingWorkPowerFromExtracted(double extractedPower, int occupiedThreadSlots,
        int powerMultiplier) {
        int slots = Math.max(1, occupiedThreadSlots);
        int multiplier = Math.max(1, powerMultiplier);
        double divisor = slots * (double) multiplier;
        return (int) Math.max(0D, extractedPower / divisor);
    }

    public static double craftingWorkPowerRequest(int ticksPassed, int bonusValue, int occupiedThreadSlots,
        int powerMultiplier) {
        int safeTicks = Math.max(1, ticksPassed);
        int safeBonus = Math.max(1, Math.min(100, bonusValue));
        int slots = Math.max(1, occupiedThreadSlots);
        int multiplier = Math.max(1, powerMultiplier);
        double requested = safeTicks * (double) safeBonus * slots * multiplier;
        return Math.min(requested, CRAFTING_MAX_WORK_POWER_PER_TICK);
    }

    public static double craftingBatchWorkPowerRequest(int ticksPassed, int bonusValue, int occupiedThreadSlots,
        int powerMultiplier) {
        int slots = Math.max(1, occupiedThreadSlots);
        if (slots <= 1 || !ECOFastPathConfig.isAggressiveBatchEnabled()) {
            return craftingWorkPowerRequest(ticksPassed, bonusValue, slots, powerMultiplier);
        }
        int safeTicks = Math.max(1, ticksPassed);
        int safeBonus = Math.max(1, Math.min(100, bonusValue));
        int multiplier = Math.max(1, powerMultiplier);
        double requested = safeTicks * (double) safeBonus * slots * multiplier;
        double aggressiveCap = safeTicks * (double) safeBonus * multiplier * ECOFastPathConfig.batchTickLimit();
        return Math.min(requested, aggressiveCap);
    }

    /**
     * Maximum number of queued crafts a single worker may finish in one tick.
     * <p>
     * Without overclock this is 1 (legacy behaviour). Each effective overclock step raises the
     * per-worker burst ceiling so a fully overclocked host can drain hundreds of queued crafts per
     * tick across its workers, while each individual completion is still gated by the crafting
     * energy budget. Spreading the burst across workers avoids the single massive per-tick batch
     * that the upstream fast-path performs.
     */
    public static int craftingBurstCraftsPerTick(boolean overclocked, int effectiveOverclockTimes) {
        if (!overclocked || effectiveOverclockTimes <= 0) {
            return 1;
        }
        int clampedOverclock = Math.max(0, Math.min(9, effectiveOverclockTimes));
        return 1 + clampedOverclock * 4;
    }
}
