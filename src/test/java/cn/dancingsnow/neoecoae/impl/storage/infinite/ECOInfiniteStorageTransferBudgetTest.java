package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.platform.TestResourceKey;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTier;

/** Budgeted, ordered and observable finite-to-infinite migration (1.7.10 port). */
class ECOInfiniteStorageTransferBudgetTest {

    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.EMPTY;
    private static final ECOInfiniteStorageTransfer.LegacyReceipt NO_LEGACY = (key, tag, amount) -> UUID
        .nameUUIDFromBytes(("legacy:" + tag + ":" + amount).getBytes(StandardCharsets.UTF_8));

    private static final class Source {

        final ItemStack stack;
        ECOStorageCell cell;
        final UUID migration;
        final Map<AEKey, Long> contents = new java.util.LinkedHashMap<>();
        final AtomicInteger completed = new AtomicInteger();

        Source(String prefix, int keys, UUID domain) {
            stack = new ItemStack(
                new NativeStorageCellItem(
                    StorageTier.L4,
                    AEKeyType.items(),
                    new ECOCellType(Component.literal("test"), 315),
                    false));
            cell = new ECOStorageCell(stack, null);
            for (int i = 0; i < keys; i++) {
                AEKey key = new TestResourceKey(prefix + i, false);
                long amount = 10L + i;
                assertEquals(amount, cell.insert(key, amount, Actionable.MODULATE, IActionSource.empty()));
                contents.put(key, amount);
            }
            migration = ECOInfiniteStorageMember.beginMigration(stack, domain);
        }

        ECOInfiniteStorageTransfer.Result step(ECOInfiniteStorageTransfer transfer, ECOInfiniteStorageEngine engine,
            ECOInfiniteStorageTransfer.Budget budget) {
            return transfer.step(migration, cell, engine, REGISTRIES, () -> {}, () -> {
                completed.incrementAndGet();
                cell.clearMigrationStacks();
            }, NO_LEGACY, budget);
        }
    }

    private static ECOInfiniteStorageTransfer.Budget keys(int max) {
        return ECOInfiniteStorageTransfer.Budget.perTick(() -> 0L, 1_000_000L, max);
    }

    private static SavedDataInfiniteStorageEngine engine() {
        return new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
    }

    private static long stored(ECOInfiniteStorageEngine engine, Source source) {
        long total = 0;
        for (AEKey key : source.contents.keySet()) total += engine.getAmount(key)
            .toBigInteger()
            .longValueExact();
        return total;
    }

    @Test
    void expiredDeadlineStillAdvancesExactlyOneKeyPerTick() {
        UUID domain = UUID.randomUUID();
        Source source = new Source("deadline", 4, domain);
        SavedDataInfiniteStorageEngine engine = engine();
        ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
        AtomicLong now = new AtomicLong();
        // Every clock read lands 3 ms later: the 2000 us budget is spent by the first key.
        java.util.function.LongSupplier clock = () -> now.getAndAdd(3_000_000L);
        for (int tick = 1; tick <= 4; tick++) {
            ECOInfiniteStorageTransfer.Budget budget = ECOInfiniteStorageTransfer.Budget.perTick(clock, 2000, 512);
            ECOInfiniteStorageTransfer.Result result = source.step(transfer, engine, budget);
            assertEquals(1, budget.processed());
            assertEquals(
                tick == 4 ? ECOInfiniteStorageTransfer.Status.COMPLETE : ECOInfiniteStorageTransfer.Status.IN_PROGRESS,
                result.status());
        }
        assertEquals(1, source.completed.get());
        assertEquals(10 + 11 + 12 + 13, stored(engine, source));
    }

    @Test
    void keyLimitBoundsATickAndUnspentBudgetMovesToTheNextDriveInOrder() {
        UUID domain = UUID.randomUUID();
        Source first = new Source("first", 3, domain), second = new Source("second", 3, domain);
        SavedDataInfiniteStorageEngine engine = engine();
        ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();

        ECOInfiniteStorageTransfer.Budget tick1 = keys(4);
        assertTrue(
            first.step(transfer, engine, tick1)
                .isComplete());
        assertFalse(tick1.exhausted());
        assertEquals(
            ECOInfiniteStorageTransfer.Status.IN_PROGRESS,
            second.step(transfer, engine, tick1)
                .status());
        assertTrue(tick1.exhausted());
        assertEquals(4, tick1.processed());
        assertEquals(1.0D / 3.0D, transfer.progress(second.migration), 1e-9);
        assertEquals(0.0D, transfer.progress(first.migration));

        ECOInfiniteStorageTransfer.Budget tick2 = keys(4);
        assertTrue(
            second.step(transfer, engine, tick2)
                .isComplete());
        assertEquals(2, tick2.processed());
        assertEquals(1, first.completed.get());
        assertEquals(1, second.completed.get());
        assertEquals(10 + 11 + 12, stored(engine, first));
        assertEquals(10 + 11 + 12, stored(engine, second));
    }

    @Test
    void progressIsMonotonicAndSurvivesAReplacedStackAndHandler() {
        UUID domain = UUID.randomUUID();
        Source source = new Source("monotonic", 10, domain);
        SavedDataInfiniteStorageEngine engine = engine();
        ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
        List<Double> progress = new ArrayList<>();
        assertEquals(
            ECOInfiniteStorageTransfer.Status.IN_PROGRESS,
            source.step(transfer, engine, keys(3))
                .status());
        progress.add(transfer.progress(source.migration));

        // 1.7.10 inventories replace ItemStack instances: same sealed contents, new stack and new handler.
        ItemStack replaced = source.stack.copy();
        source.cell = new ECOStorageCell(replaced, null);
        assertEquals(source.migration, ECOInfiniteStorageMember.getMigrationId(replaced));
        ECOInfiniteStorageTransfer.Result result;
        do {
            result = source.step(transfer, engine, keys(3));
            progress.add(result.isComplete() ? 1.0D : transfer.progress(source.migration));
        } while (!result.isComplete());

        for (int i = 1; i < progress.size(); i++)
            assertTrue(progress.get(i) >= progress.get(i - 1), progress::toString);
        // Resumed at the old position: 10 keys at 3 per tick is 4 ticks, not 1 + 4.
        assertEquals(4, progress.size());
        assertEquals(
            source.contents.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum(),
            stored(engine, source));
    }

    @Test
    void restartedCursorReplaysThroughReceiptsWithoutDuplicating() {
        UUID domain = UUID.randomUUID();
        Source source = new Source("restart", 6, domain);
        SavedDataInfiniteStorageEngine engine = engine();
        assertEquals(
            ECOInfiniteStorageTransfer.Status.IN_PROGRESS,
            source.step(new ECOInfiniteStorageTransfer(), engine, keys(4))
                .status());
        // A reload loses every cursor; the replay must not import the first four keys again.
        ECOInfiniteStorageTransfer reloaded = new ECOInfiniteStorageTransfer();
        assertTrue(
            source.step(reloaded, engine, keys(512))
                .isComplete());
        assertEquals(10 + 11 + 12 + 13 + 14 + 15, stored(engine, source));
    }

    @Test
    void blockedDomainReportsItsReasonKeepsTheKeyAndResumes() {
        UUID domain = UUID.randomUUID();
        Source source = new Source("blocked", 3, domain);
        ToggleEngine engine = new ToggleEngine(engine());
        ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
        engine.blocked = true;
        ECOInfiniteStorageTransfer.Result result = source.step(transfer, engine, keys(512));
        assertTrue(result.isBlocked());
        assertEquals(ECOInfiniteStorageTransfer.BLOCKED_RESTORE_PENDING, result.reason());
        assertEquals(0.0D, transfer.progress(source.migration));
        assertEquals(0, stored(engine, source));

        engine.blocked = false;
        engine.commitFails = true;
        result = source.step(transfer, engine, keys(512));
        assertTrue(result.isBlocked());
        assertEquals(ECOInfiniteStorageTransfer.BLOCKED_COMMIT_FAILED, result.reason());
        assertEquals("disk full", result.detail());
        assertEquals(0, source.completed.get(), "complete must wait for a successful commit");

        engine.commitFails = false;
        assertTrue(
            source.step(transfer, engine, keys(512))
                .isComplete());
        assertEquals(1, source.completed.get());
        assertEquals(10 + 11 + 12, stored(engine, source));
    }

    @Test
    void domainThatCannotMigrateNamesTheCause() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", ECOInfiniteStorageData.CURRENT_VERSION);
        tag.put("entries", new cn.dancingsnow.neoecoae.platform.nbt.ListTag());
        CompoundTag unresolved = new CompoundTag();
        unresolved.putString("kept", "x");
        tag.put("unresolvedMetadata", unresolved);
        SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(
            ECOInfiniteStorageData.load(tag, REGISTRIES));
        assertTrue(engine.isHealthy());
        Source source = new Source("unresolved", 2, UUID.randomUUID());
        ECOInfiniteStorageTransfer.Result result = source.step(new ECOInfiniteStorageTransfer(), engine, keys(512));
        assertTrue(result.isBlocked());
        assertEquals(ECOInfiniteStorageTransfer.BLOCKED_UNRESOLVED_ENTRIES, result.reason());
    }

    @Test
    void receiptsKeepThePreviousFormulaByteForByte() {
        UUID domain = UUID.randomUUID();
        long packedPos = cn.dancingsnow.neoecoae.platform.minecraft.BlockPos.of(-123_456_789L)
            .asLong();
        Source source = new Source("receipt", 3, domain);
        RecordingEngine engine = new RecordingEngine(engine());
        assertTrue(
            new ECOInfiniteStorageTransfer()
                .step(
                    source.migration,
                    source.cell,
                    engine,
                    REGISTRIES,
                    () -> {},
                    () -> {},
                    (key, tag, amount) -> ECOInfiniteStorageTransfer
                        .slotReceipt(domain, "to-domain", packedPos, tag, amount),
                    ECOInfiniteStorageTransfer.Budget.unbounded())
                .isComplete());
        assertEquals(3, engine.transactions.size());
        for (Map.Entry<AEKey, Long> entry : source.contents.entrySet()) {
            AEKey key = entry.getKey();
            long amount = entry.getValue();
            // The exact expressions used before this change.
            UUID sealed = UUID.nameUUIDFromBytes(
                (source.migration + ":" + key.toTagGeneric(REGISTRIES)).getBytes(StandardCharsets.UTF_8));
            String value = domain + ":"
                + "to-domain"
                + ":"
                + packedPos
                + ":"
                + key.toTagGeneric(REGISTRIES)
                + ":"
                + amount;
            UUID legacy = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
            Set<UUID> expected = new HashSet<>();
            expected.add(legacy);
            expected.add(sealed);
            assertTrue(engine.transactions.contains(expected), key + " receipts changed");
            assertTrue(engine.hasMigrationReceipt(sealed));
            assertTrue(engine.hasMigrationReceipt(legacy));
        }
    }

    /** Delegating engine that can refuse migration inserts or commits on demand. */
    private static class ToggleEngine implements ECOInfiniteStorageEngine {

        final ECOInfiniteStorageEngine delegate;
        boolean blocked, commitFails;

        ToggleEngine(ECOInfiniteStorageEngine delegate) {
            this.delegate = delegate;
        }

        @Override
        public long insertOnce(Set<UUID> transactionIds, AEKey key, long amount) {
            return blocked ? 0 : delegate.insertOnce(transactionIds, key, amount);
        }

        @Override
        public String migrationBlockedReason(AEKey key) {
            return blocked ? ECOInfiniteStorageTransfer.BLOCKED_RESTORE_PENDING : delegate.migrationBlockedReason(key);
        }

        @Override
        public CommitResult commit() {
            return commitFails ? new CommitResult(false, 0L, "disk full") : delegate.commit();
        }

        @Override
        public boolean hasMigrationReceipt(UUID transactionId) {
            return delegate.hasMigrationReceipt(transactionId);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            return delegate.insert(key, amount, mode);
        }

        @Override
        public BigInteger insert(AEKey key, BigInteger amount, Actionable mode) {
            return delegate.insert(key, amount, mode);
        }

        @Override
        public BigInteger usedBytes() {
            return delegate.usedBytes();
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            return delegate.extract(key, amount, mode);
        }

        @Override
        public HugeAmount getAmount(AEKey key) {
            return delegate.getAmount(key);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            delegate.getAvailableStacks(out);
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Collection<TypeStats> getTypeStats() {
            return delegate.getTypeStats();
        }

        @Override
        public boolean hasHugeStacks() {
            return delegate.hasHugeStacks();
        }

        @Override
        public boolean reserveRestores(Map<AEKey, UUID> transactions, Set<UUID> targets,
            Map<AEKey, Map<UUID, RestoreTargetAmounts>> plans) {
            return delegate.reserveRestores(transactions, targets, plans);
        }

        @Override
        public Map<UUID, RestoreTargetAmounts> restorePlan(AEKey key) {
            return delegate.restorePlan(key);
        }

        @Override
        public boolean finishRestores(Map<AEKey, UUID> transactions) {
            return delegate.finishRestores(transactions);
        }
    }

    private static final class RecordingEngine extends ToggleEngine {

        final List<Set<UUID>> transactions = new ArrayList<>();

        RecordingEngine(ECOInfiniteStorageEngine delegate) {
            super(delegate);
        }

        @Override
        public long insertOnce(Set<UUID> transactionIds, AEKey key, long amount) {
            transactions.add(new HashSet<>(transactionIds));
            return super.insertOnce(transactionIds, key, amount);
        }
    }
}
