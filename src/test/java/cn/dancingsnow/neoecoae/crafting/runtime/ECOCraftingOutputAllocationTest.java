package cn.dancingsnow.neoecoae.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingOutputAllocationTest {

    @Test
    void distributesPartialAcceptanceProportionally() {
        long[] shares = ECOCraftingOutputAllocation.proportional(new long[] { 2L, 3L, 5L }, 5L);

        assertArrayEquals(new long[] { 1L, 2L, 2L }, shares);
    }

    @Test
    void neverAllocatesMoreThanDemand() {
        long[] shares = ECOCraftingOutputAllocation.proportional(new long[] { 1L, 2L }, 99L);

        assertArrayEquals(new long[] { 1L, 2L }, shares);
    }

    @Test
    void handlesLargeValuesWithoutOverflow() {
        long[] shares = ECOCraftingOutputAllocation
            .proportional(new long[] { Long.MAX_VALUE / 2L, Long.MAX_VALUE / 2L }, Long.MAX_VALUE / 2L);

        assertEquals(Long.MAX_VALUE / 2L, shares[0] + shares[1]);
    }
}
