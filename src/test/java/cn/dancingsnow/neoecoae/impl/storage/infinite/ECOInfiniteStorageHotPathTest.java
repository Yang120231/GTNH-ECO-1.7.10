package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCell;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

class ECOInfiniteStorageHotPathTest {

    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void primitiveAndOverflowTransfersMatchExactReferenceAndImmutableStatistics() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        AEKey[] keys = { AEItemKey.of(TestItems.STONE), AEItemKey.of(TestItems.DIRT),
            AEItemKey.of(TestItems.COBBLESTONE) };
        BigInteger[] amounts = { BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO };
        Random random = new Random(741852);
        for (int i = 0; i < 2_000; i++) {
            int index = random.nextInt(keys.length);
            long delta = i % 29 == 0 ? Long.MAX_VALUE : random.nextInt(1_000) + 1;
            if (random.nextBoolean()) {
                assertEquals(delta, engine.insert(keys[index], delta, Actionable.MODULATE));
                amounts[index] = amounts[index].add(BigInteger.valueOf(delta));
            } else {
                long expected = amounts[index].min(BigInteger.valueOf(delta))
                    .longValueExact();
                assertEquals(expected, engine.extract(keys[index], delta, Actionable.MODULATE));
                amounts[index] = amounts[index].subtract(BigInteger.valueOf(expected));
            }
            assertEquals(
                amounts[index],
                engine.getAmount(keys[index])
                    .toBigInteger());
            if (i % 31 == 0) {
                BigInteger sum = amounts[0].add(amounts[1])
                    .add(amounts[2]);
                long types = java.util.Arrays.stream(amounts)
                    .filter(a -> a.signum() > 0)
                    .count();
                if (types == 0) {
                    assertTrue(
                        engine.getTypeStats()
                            .isEmpty());
                    continue;
                }
                var stats = engine.getTypeStats()
                    .iterator()
                    .next();
                assertEquals(
                    sum,
                    stats.storedAmount()
                        .toBigInteger());
                assertEquals(types, stats.storedTypes());
                engine.insert(keys[index], 1, Actionable.MODULATE);
                amounts[index] = amounts[index].add(BigInteger.ONE);
                assertEquals(
                    sum,
                    stats.storedAmount()
                        .toBigInteger(),
                    "published snapshots stay immutable");
            }
        }
        for (int i = 0; i < keys.length; i++) {
            while (amounts[i].signum() > 0) {
                long taken = engine.extract(keys[i], Long.MAX_VALUE, Actionable.MODULATE);
                amounts[i] = amounts[i].subtract(BigInteger.valueOf(taken));
            }
        }
        assertTrue(
            engine.getTypeStats()
                .isEmpty());
        engine.insert(keys[0], 7, Actionable.MODULATE);
        assertEquals(
            HugeAmount.of(7),
            engine.getTypeStats()
                .iterator()
                .next()
                .storedAmount());
    }

    @Test
    void hugeStatisticsDeltaFlushesInBothDirectionsAndDowngrades() {
        var total = new MutableInfiniteStorageTotal();
        BigInteger expected = MAX.multiply(BigInteger.valueOf(4));
        total.add(expected);
        for (int i = 0; i < 4; i++) {
            total.subtract(Long.MAX_VALUE);
            expected = expected.subtract(MAX);
        }
        assertEquals(HugeAmount.ZERO, total.snapshot());
        for (int i = 0; i < 4; i++) {
            total.add(Long.MAX_VALUE);
            expected = expected.add(MAX);
        }
        assertEquals(
            expected,
            total.snapshot()
                .toBigInteger());
        total.subtract(expected.subtract(BigInteger.ONE));
        assertEquals(HugeAmount.of(1), total.snapshot());
    }

    @Test
    void simulationsDoNotChangeInventoryStatisticsDirtyFlagOrRevision() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        AEKey key = AEItemKey.of(TestItems.STONE);
        engine.insert(key, 7, Actionable.MODULATE);
        var stats = engine.getTypeStats();
        long revision = engine.revision();
        data.setDirty(false);
        for (int i = 0; i < 100; i++) {
            assertEquals(Long.MAX_VALUE, engine.insert(key, Long.MAX_VALUE, Actionable.SIMULATE));
            assertEquals(7, engine.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        }
        assertSame(stats, engine.getTypeStats());
        assertFalse(data.isDirty());
        assertEquals(revision, engine.revision());
        assertEquals(HugeAmount.of(7), engine.getAmount(key));
    }

    @Test
    void exactListingAndCombinedCollectionKeepAllKeysAndSaturateVisibleTotals() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        AEKey stone = AEItemKey.of(TestItems.STONE), dirt = AEItemKey.of(TestItems.DIRT);
        engine.insert(stone, MAX.add(BigInteger.TEN), Actionable.MODULATE);
        engine.insert(dirt, 7, Actionable.MODULATE);
        Map<AEKey, ExactAmount> exact = new HashMap<>();
        storage.neoecoae$visitExactAmounts(exact::put);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                stone,
                ExactAmount.finite(MAX.add(BigInteger.TEN)),
                dirt,
                ExactAmount.finite(BigInteger.valueOf(7))),
            exact);
        var out = new KeyCounter();
        out.set(stone, 64);
        storage.neoecoae$listWithExactAmounts(out, (key, amount) -> {});
        assertEquals(Long.MAX_VALUE, out.get(stone));
        assertEquals(7, out.get(dirt));
        ExactAmountCollector.begin();
        try {
            out = new KeyCounter();
            assertTrue(ExactAmountCollector.collectCombined(storage, out));
            assertEquals(exact, ExactAmountCollector.finish());
        } finally {
            ExactAmountCollector.abort();
        }
        assertFalse(ExactAmountCollector.collectCombined(storage, new KeyCounter()));
    }

    @Test
    void restoreAvailabilityUpdatesImmediatelyAndStatisticsFollowRestoration() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        AEKey stone = AEItemKey.of(TestItems.STONE), dirt = AEItemKey.of(TestItems.DIRT);
        engine.insert(stone, 10, Actionable.MODULATE);
        engine.insert(dirt, 3, Actionable.MODULATE);
        UUID transaction = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(engine.canUseMountedStorage());
        assertTrue(
            engine.reserveRestores(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(stone, transaction),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(target),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                    stone,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                        .mapOf(target, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 10, 10)))));
        assertFalse(engine.canUseMountedStorage());
        assertFalse(engine.contains(stone));
        Map<AEKey, BigInteger> exact = new HashMap<>();
        engine.visitExactAmounts(exact::put);
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(dirt, BigInteger.valueOf(3)), exact);
        assertEquals(0, engine.insert(stone, 1, Actionable.MODULATE));
        assertTrue(engine.finishRestore(stone, transaction));
        assertTrue(engine.canUseMountedStorage());
        assertEquals(
            HugeAmount.of(3),
            engine.getTypeStats()
                .iterator()
                .next()
                .storedAmount());
    }

    @Test
    void recoveryReadOnlyKeepsReadsButRejectsRealWrites() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        AEKey key = AEItemKey.of(TestItems.STONE);
        engine.insert(key, 10, Actionable.MODULATE);
        data.markIncompleteLegacy(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf("fixture"));
        assertFalse(engine.canUseMountedStorage());
        assertEquals(10, engine.extract(key, 20, Actionable.SIMULATE));
        assertEquals(0, engine.extract(key, 20, Actionable.MODULATE));
        assertEquals(0, engine.insert(key, 1, Actionable.MODULATE));
        assertTrue(engine.contains(key));
        Map<AEKey, BigInteger> exact = new HashMap<>();
        engine.visitExactAmounts(exact::put);
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, BigInteger.TEN), exact);
    }

    @Test
    void combinedLongSourcesAggregateExactlyWithOrdinaryStorageInEitherOrder() {
        AEKey key = AEItemKey.of(TestItems.STONE);
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var ordinary = mock(cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage.class);
        for (boolean reverse : new boolean[] { false, true }) {
            ExactAmountCollector.begin();
            try {
                var contribution = new KeyCounter();
                contribution.set(key, 64);
                if (reverse) ExactAmountCollector.observe(ordinary, contribution);
                assertTrue(ExactAmountCollector.collectCombined(storage, new KeyCounter()));
                if (!reverse) ExactAmountCollector.observe(ordinary, contribution);
                assertEquals(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                        .mapOf(key, ExactAmount.finite(MAX.add(BigInteger.valueOf(64)))),
                    ExactAmountCollector.finish());
            } finally {
                ExactAmountCollector.abort();
            }
        }
    }

    @Test
    void stableAdmissionIsCachedButExternalInventoryStateIsRechecked() {
        AEItemKey key = AEItemKey.of(TestItems.STONE);
        var source = IActionSource.empty();
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        try (var cells = mockStatic(StorageCells.class)) {
            assertEquals(1, storage.insert(key, 1, Actionable.SIMULATE, source));
            assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, source));
            cells.verify(
                () -> StorageCells.getCellInventory(argThat(stack -> key.equals(AEItemKey.of(stack))), isNull()),
                times(1));
        }
        storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var dynamic = mock(StorageCell.class);
        when(dynamic.canFitInsideCell()).thenReturn(true, false);
        try (var cells = mockStatic(StorageCells.class)) {
            cells.when(() -> StorageCells.getCellInventory(argThat(stack -> key.equals(AEItemKey.of(stack))), isNull()))
                .thenReturn(dynamic);
            assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, source));
            assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, source));
            cells.verify(
                () -> StorageCells.getCellInventory(argThat(stack -> key.equals(AEItemKey.of(stack))), isNull()),
                times(2));
        }
    }
}
