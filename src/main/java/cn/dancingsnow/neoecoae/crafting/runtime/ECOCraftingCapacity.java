package cn.dancingsnow.neoecoae.crafting.runtime;

public final class ECOCraftingCapacity {

    private ECOCraftingCapacity() {}

    public static int threadSlotsPerWorker(int baseSlots, int overclockMultiplier, boolean overclocked,
        boolean hasParallelCore) {
        long slots = (long) Math.max(0, baseSlots) * (overclocked ? Math.max(1, overclockMultiplier) : 1);
        return saturatingInt(slots);
    }

    public static int maxInFlightCrafts(int parallelThreads, int workerCount, int slotsPerWorker) {
        if (workerCount <= 0 || slotsPerWorker <= 0) {
            return 0;
        }
        return saturatingInt((long) workerCount * slotsPerWorker);
    }

    public static int availableCraftSlots(int maxInFlightCrafts, int occupiedSlots) {
        return Math.max(0, maxInFlightCrafts - Math.max(0, occupiedSlots));
    }

    public static int overclockTimes(int parallelThreads, int availableWorkerSlots) {
        int overflow = Math.max(0, parallelThreads - availableWorkerSlots);
        if (overflow <= 0 || parallelThreads <= 0) {
            return 0;
        }
        double ratio = (double) overflow / parallelThreads;
        return (int) Math.max(0L, Math.min(9L, Math.round(ratio / 0.05D)));
    }

    private static int saturatingInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }
}
