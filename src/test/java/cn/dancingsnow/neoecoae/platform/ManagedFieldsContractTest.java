package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.ManagedFields;
import cn.dancingsnow.neoecoae.platform.sync.ManagedValue;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

class ManagedFieldsContractTest {

    static class Parent {

        @Persisted(key = "legacy_amount")
        private long quantity = Long.MAX_VALUE;
    }

    static final class Fixture extends Parent {

        @Persisted
        private UUID receipt = UUID.randomUUID();
        @Persisted
        private CompoundTag payload = new CompoundTag();
        @DescSynced
        private boolean online = true;
        @DescSynced
        private UUID interaction = UUID.randomUUID();
        @Persisted
        private final Counter counter = new Counter();
    }

    static final class Counter implements ManagedValue {

        int amount;

        @Override
        public CompoundTag writeManaged() {
            var tag = new CompoundTag();
            tag.putInt("amount", amount);
            return tag;
        }

        @Override
        public void readManaged(CompoundTag tag) {
            amount = tag.getInt("amount");
        }
    }

    @Test
    void persistenceAndDescriptionHaveDistinctSchemasAndInheritedAliasesRoundTrip() {
        Fixture original = new Fixture();
        original.payload.putIntArray("large", new int[10_000]);
        original.counter.amount = 37;
        var save = ManagedFields.write(original, false);
        var description = ManagedFields.write(original, true);
        assertTrue(save.contains("legacy_amount"));
        assertTrue(save.contains("payload"));
        assertFalse(save.contains("online"));
        assertTrue(description.contains("online"));
        assertFalse(description.contains("payload"));
        assertFalse(description.contains("counter"));
        Fixture loaded = new Fixture();
        UUID transientIdentity = loaded.interaction;
        Counter stable = loaded.counter;
        ManagedFields.read(loaded, save, false);
        assertEquals(Long.MAX_VALUE, ((Parent) loaded).quantity);
        assertEquals(original.receipt, loaded.receipt);
        assertEquals(original.payload, loaded.payload);
        assertNotSame(original.payload, loaded.payload);
        assertEquals(transientIdentity, loaded.interaction);
        assertSame(stable, loaded.counter);
        assertEquals(37, stable.amount);
        ManagedFields.read(loaded, description, true);
        assertEquals(original.interaction, loaded.interaction);
    }

    @Test
    void nullDescriptionClearsPreviouslyInstalledIdentityAndSnapshotsDoNotAlias() {
        Fixture server = new Fixture(), client = new Fixture();
        server.interaction = null;
        ManagedFields.read(client, ManagedFields.write(server, true), true);
        assertNull(client.interaction);
        server.payload.putLong("n", 17);
        var saved = ManagedFields.write(server, false);
        server.payload.putLong("n", 99);
        ManagedFields.read(client, saved, false);
        assertEquals(17, client.payload.getLong("n"));
    }

    @Test
    void unavailableCodecsFailInsteadOfDroppingPersistedFields() {
        class Unsupported {

            @Persisted
            Object value = new Object();
        }
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.write(new Unsupported(), false));
    }
}
