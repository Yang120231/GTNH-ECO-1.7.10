package cn.dancingsnow.neoecoae.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingCapacityTest {

    @Test
    void reproducesBaseAndL9WorkerLimits() {
        int baseSlots = ECOCraftingCapacity.threadSlotsPerWorker(32, 16, false, true);
        int overclockedSlots = ECOCraftingCapacity.threadSlotsPerWorker(32, 16, true, true);

        assertEquals(352, ECOCraftingCapacity.maxInFlightCrafts(10000, 11, baseSlots));
        assertEquals(5632, ECOCraftingCapacity.maxInFlightCrafts(10000, 11, overclockedSlots));
    }

    @Test
    void parallelCoresCanLimitWorkerSlots() {
        assertEquals(640, ECOCraftingCapacity.maxInFlightCrafts(640, 11, 512));
        assertEquals(288, ECOCraftingCapacity.availableCraftSlots(640, 352));
    }

    @Test
    void hostWithoutParallelCoreCannotAcceptWork() {
        int slots = ECOCraftingCapacity.threadSlotsPerWorker(32, 16, true, false);

        assertEquals(0, slots);
        assertEquals(0, ECOCraftingCapacity.maxInFlightCrafts(0, 11, slots));
    }

    @Test
    void overclockRequiresParallelOverflow() {
        assertEquals(0, ECOCraftingCapacity.overclockTimes(5632, 5632));
        assertEquals(9, ECOCraftingCapacity.overclockTimes(5760, 5632));
    }

    @Test
    void elevenL9SegmentsReproduceModernCapacityAndOverclock() {
        int coreCount = 11;
        int workerCount = 11;
        int baseParallel = coreCount * 256;
        int overclockedParallel = coreCount * (256 + 384);
        int baseSlots = ECOCraftingCapacity.threadSlotsPerWorker(32, 16, false, true);
        int overclockedSlots = ECOCraftingCapacity.threadSlotsPerWorker(32, 16, true, true);

        assertEquals(352, ECOCraftingCapacity.maxInFlightCrafts(baseParallel, workerCount, baseSlots));
        assertEquals(5632,
            ECOCraftingCapacity.maxInFlightCrafts(overclockedParallel, workerCount, overclockedSlots));
        assertEquals(9, ECOCraftingCapacity.overclockTimes(overclockedParallel, workerCount * overclockedSlots));
    }
}
