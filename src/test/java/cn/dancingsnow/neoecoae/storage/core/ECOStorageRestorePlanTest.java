package cn.dancingsnow.neoecoae.storage.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ECOStorageRestorePlanTest {

    @Test
    void routesResourcesWithoutMutatingInputs() {
        ECOStorageKey item = ECOStorageKey.item("minecraft:stone", 0, "");
        ECOStorageKey fluid = ECOStorageKey.fluid("water", "");
        ECOStorageBackend source = new ECOStorageBackend();
        source.insert(item, ECOAmount.of(8L), false);
        source.insert(fluid, ECOAmount.of(1000L), false);
        Map<UUID, ECOStorageBackend> targets = new LinkedHashMap<>();
        UUID itemId = UUID.randomUUID();
        UUID fluidId = UUID.randomUUID();
        ECOStorageBackend items = new ECOStorageBackend(ECOCapacityPolicy.finite(100L));
        items.setAcceptedChannel("item");
        ECOStorageBackend fluids = new ECOStorageBackend(ECOCapacityPolicy.finite(100L));
        fluids.setAcceptedChannel("fluid");
        targets.put(itemId, items);
        assertNull(ECOStorageRestorePlan.create(source, targets));
        targets.put(fluidId, fluids);
        Map<UUID, ECOStorageBackend> plan = ECOStorageRestorePlan.create(source, targets);
        assertNotNull(plan);
        assertEquals(
            ECOAmount.of(8L),
            plan.get(itemId)
                .getAmount(item));
        assertEquals(
            ECOAmount.of(1000L),
            plan.get(fluidId)
                .getAmount(fluid));
        assertTrue(items.isEmpty());
        assertTrue(fluids.isEmpty());
        assertEquals(ECOAmount.of(1000L), source.getAmount(fluid));
    }

    @Test
    void leavesFivePercentReserve() {
        ECOStorageKey key = ECOStorageKey.item("minecraft:stone", 0, "");
        ECOStorageBackend source = new ECOStorageBackend();
        Map<UUID, ECOStorageBackend> targets = new LinkedHashMap<>();
        targets.put(UUID.randomUUID(), new ECOStorageBackend(ECOCapacityPolicy.finite(100L)));
        source.insert(key, ECOAmount.of(760L), false);
        assertNotNull(ECOStorageRestorePlan.create(source, targets));
        source.insert(key, ECOAmount.of(1L), false);
        assertNull(ECOStorageRestorePlan.create(source, targets));
    }
}
