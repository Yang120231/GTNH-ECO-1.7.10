package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class ECOBigBatchSafetyTest {

    private final AEKey key = mock(AEKey.class, RETURNS_DEEP_STUBS);

    @Test
    void unitRecipeCanExceedTheOldCeilingAndDoubleOutputHalvesTheLimit() {
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxBatchSizeForAmount(1));
        assertEquals(
            Long.MAX_VALUE / 2,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 2)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        long count = (1L << 42) + 1;
        assertEquals(
            count,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(
                ECOBatchCraftingHelper
                    .multiply(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1)), count))
                .amount());
    }

    @Test
    void duplicateEntriesAreAggregatedBeforeComputingTheLimit() {
        var same = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .listOf(new GenericStack(key, 1), new GenericStack(key, 1));
        assertEquals(
            Long.MAX_VALUE / 2,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                same,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        assertThrows(ArithmeticException.class, () -> ECOBatchCraftingHelper.multiply(same, Long.MAX_VALUE));
        assertEquals(
            Long.MAX_VALUE / 3,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 3))));
    }

    @Test
    void manyIngredientArithmeticFollowsAmountsInsteadOfFastPathEntryLimit() {
        var ingredients = new ArrayList<GenericStack>();
        for (int slot = 0; slot < 81; slot++) {
            ingredients.add(new GenericStack(mock(AEKey.class, RETURNS_DEEP_STUBS), 1));
        }
        assertEquals(
            Long.MAX_VALUE,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                ingredients,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var multiplied = ECOBatchCraftingHelper.multiply(ingredients, 2);
        assertEquals(81, multiplied.size());
        for (var ingredient : multiplied) assertEquals(2, ingredient.amount());
    }

    @Test
    void manyRepeatedSlotsAggregateBeforeDebitingAndStillRejectOverflow() {
        var repeated = java.util.Collections.nCopies(81, new GenericStack(key, 1));
        assertEquals(
            Long.MAX_VALUE / 81,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                repeated,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 162)),
            ECOBatchCraftingHelper.multiply(repeated, 2));
        assertThrows(
            ArithmeticException.class,
            () -> ECOBatchCraftingHelper.multiply(repeated, Long.MAX_VALUE / 81 + 1));
    }

    @Test
    void nonzeroEnergyCanReachLongMaxAndZeroEnergyDoesNotProbeTheNetwork() {
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxAffordableCrafts(2, Long.MAX_VALUE, amount -> amount));
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxAffordableCrafts(0, Long.MAX_VALUE, amount -> {
            fail("Zero-energy batches must not query the network");
            return 0;
        }));
        assertEquals(0, ECOBatchCraftingHelper.maxAffordableCrafts(1, 100L, amount -> Double.POSITIVE_INFINITY));
        assertEquals(0, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.NaN));
        assertEquals(0, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.POSITIVE_INFINITY));
    }

    @Test
    void physicalBatchSlotsRemainIntBounded() {
        assertEquals(
            Integer.MAX_VALUE,
            ECOBatchCraftingHelper.maxBatchSizeFromTotals(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, Long.MAX_VALUE))));
    }

    @Test
    void outputAndRemainderSharingAWaitingKeyCannotOverflowTogether() {
        assertEquals(
            Long.MAX_VALUE / 3,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 2)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1))));
    }

    @Test
    void energyBoundaryDoesNotAcceptRoundedDownCostsOrInfiniteTotals() {
        long exactBudget = 1L << 54;
        assertEquals(
            exactBudget,
            ECOBatchCraftingHelper
                .maxAffordableCrafts(1, Long.MAX_VALUE, amount -> Math.min(amount, (double) exactBudget)));
        assertEquals(1L, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.MIN_VALUE));
        assertEquals(0L, ECOBatchCraftingHelper.maxAffordableCrafts(Double.MAX_VALUE, Long.MAX_VALUE, amount -> 0));
    }
}
