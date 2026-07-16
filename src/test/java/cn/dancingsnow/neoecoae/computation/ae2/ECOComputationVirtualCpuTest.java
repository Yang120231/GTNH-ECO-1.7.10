package cn.dancingsnow.neoecoae.computation.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import appeng.me.cluster.implementations.CraftingCPUCluster;

class ECOComputationVirtualCpuTest {

    @Test
    void reservesAe2TaskProgressForBatchAccounting() {
        CraftingCPUCluster.TaskProgress progress = new CraftingCPUCluster.TaskProgress();

        assertTrue(ECOComputationVirtualCpu.setTaskProgressValue(progress, 5632L));
        assertEquals(5632L, ECOComputationVirtualCpu.taskProgressValue(progress));
        assertTrue(ECOComputationVirtualCpu.setTaskProgressValue(progress, 1L));
        assertEquals(1L, ECOComputationVirtualCpu.taskProgressValue(progress));
    }

    @Test
    void energyConstrainedBatchUsesLargestAffordableCraftCount() {
        assertEquals(10, ECOComputationVirtualCpu.maxAffordableCrafts(2D, 64, requested -> Math.min(20D, requested)));
        assertEquals(256, ECOComputationVirtualCpu.maxAffordableCrafts(3D, 256, requested -> requested));
        assertEquals(0, ECOComputationVirtualCpu.maxAffordableCrafts(Double.NaN, 64, requested -> requested));
    }

    @Test
    void finalOutputBatchLimitSubtractsInFlightOutputs() {
        assertEquals(5328, ECOComputationVirtualCpu.maxCraftsForFinalOutputDemand(100000, 94672, 1));
        assertEquals(0, ECOComputationVirtualCpu.maxCraftsForFinalOutputDemand(100000, 100000, 1));
        assertEquals(1, ECOComputationVirtualCpu.maxCraftsForFinalOutputDemand(100000, 99999, 4));
        assertEquals(Integer.MAX_VALUE, ECOComputationVirtualCpu.maxCraftsForFinalOutputDemand(100, 0, 0));
    }

    @Test
    void totalTickBudgetIncludesSlowVerificationCrafts() {
        assertEquals(16383, ECOComputationVirtualCpu.remainingTickBudget(16384, 1));
        assertEquals(0, ECOComputationVirtualCpu.remainingTickBudget(16384, 16384));
        assertEquals(0, ECOComputationVirtualCpu.remainingTickBudget(16384, 20000));
    }
}
