package cn.dancingsnow.neoecoae.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.fastpath.ported.ECOBatchCraftingHelper;

class ECOBatchCraftingHelperPortTest {

    @Test
    void energySearchKeepsLongCapacityAndFractionalBoundary() {
        assertEquals(1_000_000_000L, ECOBatchCraftingHelper.maxAffordableCrafts(0, 1_000_000_000L, value -> 0));
        assertEquals(7, ECOBatchCraftingHelper.maxAffordableCrafts(3, 100, value -> Math.min(value, 21)));
        assertEquals(0, ECOBatchCraftingHelper.maxAffordableCrafts(Double.NaN, 100, value -> value));
        assertEquals((1L << 42) / 3, ECOBatchCraftingHelper.maxBatchSizeForAmount(3));
    }

    @Test
    void providerOwnershipTransfersOrRollsBackExactlyOnce() {
        long[] stock = { 10L };
        ECOBatchCraftingHelper.BatchInventory inventory = new ECOBatchCraftingHelper.BatchInventory() {

            public long available(Object key) {
                return stock[0];
            }

            public long extract(Object key, long amount, Actionable mode) {
                long taken = Math.min(stock[0], amount);
                if (mode == Actionable.MODULATE) stock[0] -= taken;
                return taken;
            }

            public void insert(Object key, long amount, Actionable mode) {
                if (mode == Actionable.MODULATE) stock[0] += amount;
            }
        };
        var rejected = cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOProviderInputTransaction
            .begin(inventory, Arrays.asList(new GenericStack("a", 4)));
        assertNotNull(rejected);
        assertEquals(6, stock[0]);
        rejected.rollback();
        rejected.rollback();
        assertEquals(10, stock[0]);
        var accepted = cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOProviderInputTransaction
            .begin(inventory, Arrays.asList(new GenericStack("a", 4)));
        assertNotNull(accepted);
        accepted.transferOwnership();
        accepted.rollback();
        assertEquals(6, stock[0]);
    }

    @Test
    void partialExtractionRollsBackEveryPreviouslyExtractedInput() {
        Map<Object, Long> stock = new HashMap<>();
        stock.put("a", 5L);
        stock.put("b", 1L);
        ECOBatchCraftingHelper.BatchInventory inventory = new ECOBatchCraftingHelper.BatchInventory() {

            public long available(Object key) {
                return stock.getOrDefault(key, 0L);
            }

            public long extract(Object key, long amount, Actionable mode) {
                long extracted = Math.min(available(key), amount);
                if (mode == Actionable.MODULATE) stock.put(key, available(key) - extracted);
                return extracted;
            }

            public void insert(Object key, long amount, Actionable mode) {
                if (mode == Actionable.MODULATE) stock.merge(key, amount, Math::addExact);
            }
        };
        assertFalse(
            ECOBatchCraftingHelper
                .extractExact(inventory, Arrays.asList(new GenericStack("a", 4), new GenericStack("b", 2))));
        assertEquals(
            5L,
            stock.get("a")
                .longValue());
        assertEquals(
            1L,
            stock.get("b")
                .longValue());
    }
}
