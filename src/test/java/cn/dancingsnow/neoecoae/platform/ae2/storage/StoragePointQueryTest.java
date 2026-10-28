package cn.dancingsnow.neoecoae.platform.ae2.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.platform.TestResourceKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** rv3 point queries and filtered listings are answered from the storages' own tables; they must equal the listing. */
class StoragePointQueryTest {

    private static final AEKey IRON = new TestResourceKey("iron", false), WATER = new TestResourceKey("water", true),
        ABSENT = new TestResourceKey("absent", false);

    @Test
    void infiniteEnginePointQueryAndVisitMatchTheListing() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        engine.insert(IRON, 40, Actionable.MODULATE);
        engine.insert(WATER, 7, Actionable.MODULATE);
        engine.insert(
            new TestResourceKey("huge", false),
            BigInteger.valueOf(Long.MAX_VALUE)
                .shiftLeft(4),
            Actionable.MODULATE);
        assertConsistent(new EngineStorage(engine));
    }

    @Test
    void channelViewsOnlyAnswerForTheirOwnChannel() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        engine.insert(IRON, 40, Actionable.MODULATE);
        engine.insert(WATER, 7, Actionable.MODULATE);
        var items = new NativeStorageView<>(new EngineStorage(engine), StorageChannel.ITEMS, 0);
        var fluids = new NativeStorageView<>(new EngineStorage(engine), StorageChannel.FLUIDS, 0);
        assertEquals(40, items.getAvailableAmount(IRON));
        assertEquals(0, items.getAvailableAmount(WATER));
        assertEquals(7, fluids.getAvailableAmount(WATER));
        assertEquals(0, fluids.getAvailableAmount(IRON));
        assertConsistent(items);
        assertConsistent(fluids);
    }

    private static void assertConsistent(MEStorage storage) {
        KeyCounter listed = storage.getAvailableStacks();
        Map<AEKey, Long> visited = new HashMap<>();
        storage.visitAvailableStacks((key, amount) -> assertNull(visited.put(key, amount), "duplicate " + key));
        assertEquals(listed.size(), visited.size());
        for (var entry : listed) {
            assertEquals(entry.getLongValue(), visited.get(entry.getKey()));
            assertEquals(entry.getLongValue(), storage.getAvailableAmount(entry.getKey()));
        }
        assertEquals(0, storage.getAvailableAmount(ABSENT));
        assertEquals(0, storage.getAvailableAmount(null));
    }

    private static final class EngineStorage implements MEStorage {

        private final SavedDataInfiniteStorageEngine engine;

        EngineStorage(SavedDataInfiniteStorageEngine engine) {
            this.engine = engine;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode,
            cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource source) {
            return engine.extract(key, amount, mode);
        }

        @Override
        public void getAvailableStacks(KeyCounter output) {
            engine.getAvailableStacks(output);
        }

        @Override
        public long getAvailableAmount(AEKey key) {
            return key == null ? 0 : engine.getAvailableAmount(key);
        }

        @Override
        public void visitAvailableStacks(java.util.function.ObjLongConsumer<AEKey> visitor) {
            engine.visitAvailableStacks(visitor);
        }

        @Override
        public cn.dancingsnow.neoecoae.platform.minecraft.Component getDescription() {
            return cn.dancingsnow.neoecoae.platform.minecraft.Component.empty();
        }
    }
}
