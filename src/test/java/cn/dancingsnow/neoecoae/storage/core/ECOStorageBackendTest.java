package cn.dancingsnow.neoecoae.storage.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class ECOStorageBackendTest {

    private static final ECOStorageKey ITEM = ECOStorageKey.item("minecraft:stone", 0, "");
    private static final ECOStorageKey OTHER_ITEM = ECOStorageKey.item("minecraft:dirt", 0, "");
    private static final ECOStorageKey THIRD_ITEM = ECOStorageKey.item("minecraft:gravel", 0, "");
    private static final ECOStorageKey FLUID = ECOStorageKey.fluid("water", "");

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
}
