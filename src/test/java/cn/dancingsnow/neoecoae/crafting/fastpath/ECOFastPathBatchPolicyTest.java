package cn.dancingsnow.neoecoae.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOFastPathBatchPolicyTest {

    @Test
    void processingBatchUsesLargestPowerOfTwoWithinSafeLimit() {
        assertEquals(1, ECOFastPathBatchPolicy.normalizeRequested(1, true));
        assertEquals(2, ECOFastPathBatchPolicy.normalizeRequested(3, true));
        assertEquals(8, ECOFastPathBatchPolicy.normalizeRequested(15, true));
        assertEquals(65536, ECOFastPathBatchPolicy.normalizeRequested(100000, true));
    }

    @Test
    void craftingBatchKeepsExistingIntegerScheduling() {
        assertEquals(3, ECOFastPathBatchPolicy.normalizeRequested(3, false));
        assertEquals(0, ECOFastPathBatchPolicy.normalizeRequested(-4, false));
    }
}
