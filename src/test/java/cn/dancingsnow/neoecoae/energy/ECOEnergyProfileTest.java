package cn.dancingsnow.neoecoae.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

class ECOEnergyProfileTest {

    @Test
    void highPerformanceParallelValuesStayExplicitlyTiered() {
        assertEquals(24, ECOEnergyProfile.craftingParallel(ECOControllerTier.L4));
        assertEquals(72, ECOEnergyProfile.craftingParallel(ECOControllerTier.L6));
        assertEquals(256, ECOEnergyProfile.craftingParallel(ECOControllerTier.L9));

        assertEquals(512, ECOEnergyProfile.overclockedCraftingParallel(ECOControllerTier.L4));
        assertEquals(1536, ECOEnergyProfile.overclockedCraftingParallel(ECOControllerTier.L6));
        assertEquals(6144, ECOEnergyProfile.overclockedCraftingParallel(ECOControllerTier.L9));

        assertEquals(1024, ECOEnergyProfile.computationAccelerators(ECOControllerTier.L4));
        assertEquals(3072, ECOEnergyProfile.computationAccelerators(ECOControllerTier.L6));
        assertEquals(9216, ECOEnergyProfile.computationAccelerators(ECOControllerTier.L9));
    }

    @Test
    void effectiveOverclockChangesProgressFromTenTicksToOne() {
        assertEquals(1, ECOEnergyProfile.craftingBurstCraftsPerTick(false, 0));
        assertEquals(37, ECOEnergyProfile.craftingBurstCraftsPerTick(true, 9));
        assertEquals(10, ECOEnergyProfile.craftingWorkPowerFromExtracted(10D, 1, 1));
        assertEquals(100, ECOEnergyProfile.craftingWorkPowerFromExtracted(100D, 1, 1));
    }

    @Test
    void proportionalPowerGivesDifferentBatchSizesEqualProgress() {
        double singleRequest = ECOEnergyProfile.craftingWorkPowerRequest(1, 100, 1, 1);
        double batchRequest = ECOEnergyProfile.craftingWorkPowerRequest(1, 100, 4, 1);
        double fulfilledRatio = 0.5D;

        assertEquals(50, ECOEnergyProfile.craftingWorkPowerFromExtracted(singleRequest * fulfilledRatio, 1, 1));
        assertEquals(50, ECOEnergyProfile.craftingWorkPowerFromExtracted(batchRequest * fulfilledRatio, 4, 1));
    }

    @Test
    void aggressiveBatchCanAdvanceFullL9Capacity() {
        int slots = 5632;
        int powerMultiplier = 512;
        double requested = ECOEnergyProfile.craftingBatchWorkPowerRequest(1, 100, slots, powerMultiplier);

        assertEquals(288358400D, requested, 0D);
        assertEquals(100, ECOEnergyProfile.craftingWorkPowerFromExtracted(requested, slots, powerMultiplier));
    }

    @Test
    void baseBatchRetainsTenTickCraftingTime() {
        double requested = ECOEnergyProfile.craftingBatchWorkPowerRequest(1, 10, 352, 1);

        assertEquals(3520D, requested, 0D);
        assertEquals(10, ECOEnergyProfile.craftingWorkPowerFromExtracted(requested, 352, 1));
    }
}
