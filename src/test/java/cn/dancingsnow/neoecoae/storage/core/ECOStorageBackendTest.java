package cn.dancingsnow.neoecoae.storage.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class ECOStorageBackendTest {

    private static final ECOStorageKey ITEM = ECOStorageKey.item("minecraft:stone", 0, "");
    private static final ECOStorageKey OTHER_ITEM = ECOStorageKey.item("minecraft:dirt", 0, "");
    private static final ECOStorageKey THIRD_ITEM = ECOStorageKey.item("minecraft:gravel", 0, "");
    private static final ECOStorageKey FLUID = ECOStorageKey.fluid("water", "");

    @Test
    void oversizedLegacyCellPreservesContentsButRejectsInsertsUntilDrained() {
        ECOStorageBackend legacy = new ECOStorageBackend(ECOCapacityPolicy.finite(100L, 2L));
        legacy.insert(ITEM, ECOAmount.of(81L), false);
        NBTTagCompound saved = new NBTTagCompound();
        legacy.writeToNBT(saved);
        ECOStorageBackend aligned = new ECOStorageBackend(ECOCapacityPolicy.finite(10L, 2L));
        aligned.readFromNBT(saved);

        assertEquals(ECOAmount.of(81L), aligned.getAmount(ITEM));
        assertEquals(ECOAmount.ZERO, aligned.insert(ITEM, ECOAmount.of(1L), true));
        assertEquals(ECOAmount.ZERO, aligned.insert(ITEM, ECOAmount.of(1L), false));
        assertEquals(ECOAmount.of(25L), aligned.extract(ITEM, ECOAmount.of(25L), false));
        assertEquals(ECOAmount.of(8L), aligned.insert(ITEM, ECOAmount.of(20L), false));

        aligned.writeToNBT(saved);
        ECOStorageBackend reloaded = new ECOStorageBackend(ECOCapacityPolicy.finite(10L, 2L));
        reloaded.readFromNBT(saved);
        assertEquals(ECOAmount.of(64L), reloaded.getAmount(ITEM));
    }

    @Test
    void emptyNbtDoesNotTurnAFiniteCellIntoAnInfiniteCell() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(10L, 2L));
        backend.readFromNBT(new NBTTagCompound());
        assertFalse(
            backend.getCapacityPolicy()
                .isInfinite());
        assertEquals(ECOAmount.of(64L), backend.insert(ITEM, ECOAmount.of(100L), false));
    }

    @Test
    void itemAmountsUseAe2BytesAndChargeEachType() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(10L, 2L));

        assertEquals(ECOAmount.of(64L), backend.insert(ITEM, ECOAmount.of(100L), false));
        assertEquals(ECOAmount.of(10L), backend.getUsed());
        assertEquals(1, backend.getTypeCount());
    }

    @Test
    void fluidAmountsUseEightThousandUnitsPerByte() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(5L, 2L));

        assertEquals(ECOAmount.of(24000L), backend.insert(FLUID, ECOAmount.of(30000L), false));
        assertEquals(ECOAmount.of(5L), backend.getUsed());
    }

    @Test
    void aFullByteStillAcceptsTheUnusedAmountInsideThatByte() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(3L, 2L));

        assertEquals(ECOAmount.of(1L), backend.insert(ITEM, ECOAmount.of(1L), false));
        assertEquals(ECOAmount.of(3L), backend.getUsed());
        assertEquals(ECOAmount.of(7L), backend.insert(ITEM, ECOAmount.of(20L), false));
        assertEquals(ECOAmount.of(8L), backend.getAmount(ITEM));
        assertEquals(ECOAmount.of(3L), backend.getUsed());
    }

    @Test
    void typeLimitIsEnforcedOnRealAndSimulatedInserts() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(6L, 2L));

        assertEquals(
            2L,
            backend.getCapacityPolicy()
                .getMaxTypes());
        assertEquals(ECOAmount.of(1L), backend.insert(ITEM, ECOAmount.of(1L), false));
        assertEquals(ECOAmount.of(1L), backend.insert(OTHER_ITEM, ECOAmount.of(1L), false));
        assertEquals(ECOAmount.ZERO, backend.insert(THIRD_ITEM, ECOAmount.of(1L), true));
        assertEquals(ECOAmount.ZERO, backend.insert(THIRD_ITEM, ECOAmount.of(1L), false));
        assertEquals(2, backend.getTypeCount());
    }

    @Test
    void simulationReturnsTheSameAcceptedAmountWithoutMutating() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(4L, 2L));

        assertEquals(ECOAmount.of(16L), backend.insert(ITEM, ECOAmount.of(100L), true));
        assertEquals(ECOAmount.ZERO, backend.getUsed());
        assertEquals(0, backend.getTypeCount());
        assertEquals(ECOAmount.of(16L), backend.insert(ITEM, ECOAmount.of(100L), false));
    }

    @Test
    void legacyNbtRecalculatesUsedBytesWithTheConfiguredCellPolicy() {
        ECOStorageBackend legacy = new ECOStorageBackend(ECOCapacityPolicy.infinite());
        legacy.insert(ITEM, ECOAmount.of(80L), false);
        NBTTagCompound tag = new NBTTagCompound();
        legacy.writeToNBT(tag);
        tag.setInteger("version", 1);

        ECOStorageBackend restored = new ECOStorageBackend(ECOCapacityPolicy.finite(64L, 2L));
        restored.readFromNBT(tag);

        assertEquals(ECOAmount.of(12L), restored.getUsed());
        assertEquals(ECOAmount.of(80L), restored.getAmount(ITEM));
    }

    @Test
    void finiteCellsSharePartiallyUsedBytesAcrossKeysInTheSameChannel() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(6L, 2L));

        assertEquals(ECOAmount.of(1L), backend.insert(ITEM, ECOAmount.of(1L), false));
        assertEquals(ECOAmount.of(15L), backend.insert(OTHER_ITEM, ECOAmount.of(20L), false));
        assertEquals(ECOAmount.of(6L), backend.getUsed());
    }

    @Test
    void finiteCellsCapEachKeyAtLongMaxValue() {
        ECOStorageBackend backend = new ECOStorageBackend(
            ECOCapacityPolicy.finite(
                ECOAmount.of(
                    BigInteger.valueOf(Long.MAX_VALUE)
                        .add(BigInteger.TEN)),
                0L));

        assertEquals(ECOAmount.of(Long.MAX_VALUE), backend.insert(ITEM, ECOAmount.of(Long.MAX_VALUE), false));
        assertEquals(ECOAmount.ZERO, backend.insert(ITEM, ECOAmount.of(1L), false));
    }

    @Test
    void infiniteDomainsRetainAmountsBeyondLongMaxValue() {
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.infinite());

        backend.insert(ITEM, ECOAmount.of(Long.MAX_VALUE), false);
        backend.insert(ITEM, ECOAmount.of(1L), false);

        assertEquals(
            ECOAmount.of(
                BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.ONE)),
            backend.getAmount(ITEM));
    }

    @Test
    void quarantinedSnapshotsRemainReadOnlyAndRoundTripTheirOriginalData() {
        NBTTagCompound original = new NBTTagCompound();
        original.setString("legacyPayload", "keep-me");
        ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.finite(64L, 2L));
        backend.quarantine(original, "broken entry");

        assertFalse(backend.isHealthy());
        assertEquals(ECOAmount.ZERO, backend.insert(ITEM, ECOAmount.of(1L), false));

        NBTTagCompound encoded = new NBTTagCompound();
        backend.writeToNBT(encoded);
        ECOStorageBackend restored = new ECOStorageBackend(ECOCapacityPolicy.finite(64L, 2L));
        restored.readFromNBT(encoded);

        assertFalse(restored.isHealthy());
        assertTrue(
            restored.getFailureReason()
                .contains("broken entry"));
        assertEquals(
            "keep-me",
            restored.getQuarantinedSnapshot()
                .getString("legacyPayload"));
    }
}
