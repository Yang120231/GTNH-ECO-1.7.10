package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * Bounded finite-to-infinite transfer. The sealed source stays intact until the destination is durable.
 */
public final class ECOInfiniteStorageTransfer {

    /** Restore reconciliation keys per tick ({@code ECOStorageInfiniteRestore}); migration uses {@link Budget}. */
    public static final int KEYS_PER_TICK = 5;
    /** Source drives whose contents are persisted and sealed per tick before their keys are migrated. */
    public static final int SEAL_SOURCES_PER_TICK = 5;
    public static final int DEFAULT_MIGRATION_MICROS_PER_TICK = 2000;
    public static final int DEFAULT_MIGRATION_KEYS_PER_TICK = 512;

    private static final String BLOCKED = "gui.neoecoae.storage.migration_blocked.";
    public static final String BLOCKED_DOMAIN_UNAVAILABLE = BLOCKED + "domain_unavailable";
    public static final String BLOCKED_DOMAIN_UNREADABLE = BLOCKED + "domain_unreadable";
    public static final String BLOCKED_LEGACY_INCOMPLETE = BLOCKED + "legacy_incomplete";
    public static final String BLOCKED_WRITE_FAILED = BLOCKED + "write_failed";
    public static final String BLOCKED_UNRESOLVED_ENTRIES = BLOCKED + "unresolved_entries";
    public static final String BLOCKED_RESTORE_PENDING = BLOCKED + "restore_pending";
    public static final String BLOCKED_REJECTED = BLOCKED + "rejected";
    public static final String BLOCKED_COMMIT_FAILED = BLOCKED + "commit_failed";
    public static final String BLOCKED_SOURCE_INELIGIBLE = BLOCKED + "source_ineligible";

    private final Map<UUID, Cursor> cursors = new HashMap<>();

    public enum Status {
        COMPLETE,
        IN_PROGRESS,
        BLOCKED
    }

    /** Outcome of one drive's step; a blocked step leaves its cursor on the refused key. */
    public static final class Result {

        public static final Result COMPLETE = new Result(Status.COMPLETE, null, null);
        public static final Result IN_PROGRESS = new Result(Status.IN_PROGRESS, null, null);

        private final Status status;
        @Nullable
        private final String reason;
        @Nullable
        private final String detail;

        private Result(Status status, @Nullable String reason, @Nullable String detail) {
            this.status = status;
            this.reason = reason;
            this.detail = detail;
        }

        /** {@code reason} is a translation key; {@code detail} is optional untranslated diagnostic text. */
        public static Result blocked(String reason, @Nullable String detail) {
            return new Result(Status.BLOCKED, reason, detail);
        }

        public Status status() {
            return status;
        }

        public boolean isComplete() {
            return status == Status.COMPLETE;
        }

        public boolean isBlocked() {
            return status == Status.BLOCKED;
        }

        @Nullable
        public String reason() {
            return reason;
        }

        @Nullable
        public String detail() {
            return detail;
        }

        /** Log text: the reason key plus its diagnostic detail. */
        public String describe() {
            return detail == null || detail.isEmpty() ? String.valueOf(reason) : reason + " (" + detail + ")";
        }
    }

    /**
     * One tick's allowance, shared by every drive migrated in that tick. The first key of a tick is always allowed,
     * so a slow server still advances; afterwards the key limit or the deadline stops the tick.
     */
    public static final class Budget {

        private final LongSupplier clock;
        private final long deadline;
        private final boolean timed;
        private final int maxKeys;
        private int processed;

        private Budget(LongSupplier clock, long deadline, boolean timed, int maxKeys) {
            this.clock = clock;
            this.deadline = deadline;
            this.timed = timed;
            this.maxKeys = Math.max(1, maxKeys);
        }

        public static Budget perTick(LongSupplier clock, long micros, int maxKeys) {
            long nanos = Math.max(0L, micros) * 1000L;
            return new Budget(clock, clock.getAsLong() + nanos, true, maxKeys);
        }

        public static Budget unbounded() {
            return new Budget(() -> 0L, 0L, false, Integer.MAX_VALUE);
        }

        public boolean exhausted() {
            if (processed == 0) return false;
            if (processed >= maxKeys) return true;
            return timed && clock.getAsLong() - deadline >= 0L;
        }

        void consume() {
            processed++;
        }

        public int processed() {
            return processed;
        }
    }

    /**
     * Controller-slot receipt. Byte-for-byte the previous
     * {@code domain + ":" + direction + ":" + pos.asLong() + ":" + key.toTagGeneric(registries) + ":" + amount}.
     */
    public static UUID slotReceipt(UUID domain, String direction, long packedPos, String keyTag, long amount) {
        String value = domain + ":" + direction + ":" + packedPos + ":" + keyTag + ":" + amount;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isEligible(IECOStorageCell cell) {
        return cell instanceof IECOStorageMigrationCell && cell.isInfiniteStorageEligible()
            && cell.getTier()
                .supportsFeature(ECOFeature.INFINITE_STORAGE_CELL);
    }

    public static boolean isEmptyCandidate(IECOStorageCell cell) {
        return isEligible(cell) && !((IECOStorageMigrationCell) cell).migrationEntries()
            .hasNext();
    }

    /** Called only after the containing chunk has saved the sealed source. */
    public Result step(ItemStack source, IECOStorageMigrationCell cell, UUID domain, ECOInfiniteStorageEngine engine,
        HolderLookup.Provider registries, long packedPos, Runnable complete, Budget budget) {
        if (!isEligible(cell)) return Result.blocked(BLOCKED_SOURCE_INELIGIBLE, null);
        if (!engine.isHealthy()) return domainBlocked(engine);
        UUID migration = ECOInfiniteStorageMember.getMigrationId(source);
        if (migration == null) throw new IllegalStateException("Missing saved migration seal");
        if (engine.isMigrationComplete(migration)) return finish(migration, engine, complete);
        boolean hasLegacy = engine.hasLegacyKeyReceipts();
        if (hasLegacy && !ECOInfiniteStorageMember.usesExactMigration(source)) {
            return Result.blocked(BLOCKED_REJECTED, "Cannot establish ownership from legacy migration receipts");
        }
        return step(migration, cell, domain, engine, registries, packedPos, complete, budget, hasLegacy);
    }

    /**
     * The cursor belongs to the migration id stored in the sealed stack, not to an ItemStack or handler instance: a
     * reloaded or replaced stack keeps its position when its sealed contents are unchanged, and otherwise restarts
     * from a fresh snapshot, where the receipts skip every key that was already inserted.
     */
    private Result step(UUID migration, IECOStorageMigrationCell cell, UUID domain, ECOInfiniteStorageEngine engine,
        HolderLookup.Provider registries, long packedPos, Runnable complete, Budget budget, boolean hasLegacy) {
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.cell != cell) {
            cursor = Cursor.resume(cursor, cell);
            cursors.put(migration, cursor);
        }
        while (cursor.index < cursor.keys.length) {
            if (budget.exhausted()) return Result.IN_PROGRESS;
            AEKey key = cursor.keys[cursor.index];
            long amount = cursor.amounts[cursor.index];
            if (amount > 0) {
                var keyTag = key.toTagGeneric(registries);
                if (hasLegacy && engine
                    .hasMigrationReceipt(slotReceipt(domain, "to-domain", packedPos, keyTag.toString(), amount))) {
                    return Result
                        .blocked(BLOCKED_REJECTED, "Legacy slot receipt cannot establish ownership of this key");
                }
                long inserted = engine.insertOnce(migration, keyTag, key, amount);
                if (inserted != amount) {
                    String reason = engine.migrationBlockedReason(key);
                    return Result.blocked(
                        reason == null ? BLOCKED_REJECTED : reason,
                        inserted == 0 ? null : "accepted " + inserted + " of " + amount);
                }
            }
            cursor.index++;
            budget.consume();
        }
        engine.completeMigration(migration);
        return finish(migration, engine, complete);
    }

    private Result finish(UUID migration, ECOInfiniteStorageEngine engine, Runnable complete) {
        if (!engine.isCheckpointSaved(engine.requestCheckpoint())) return Result.IN_PROGRESS;
        complete.run();
        cursors.remove(migration);
        return Result.COMPLETE;
    }

    private static Result domainBlocked(ECOInfiniteStorageEngine engine) {
        String reason = engine.migrationBlockedReason(null);
        return Result.blocked(reason == null ? BLOCKED_DOMAIN_UNAVAILABLE : reason, null);
    }

    /** Fraction of the current source's keys already handled, in [0, 1]; 0 when no cursor is open. */
    public double progress(@Nullable UUID migration) {
        Cursor cursor = migration == null ? null : cursors.get(migration);
        if (cursor == null || cursor.keys.length == 0) return 0.0D;
        return cursor.index / (double) cursor.keys.length;
    }

    public void reset() {
        cursors.clear();
    }

    /**
     * Reconciles a target against a durable plan, including a write whose chunk receipt was not saved yet.
     */
    public static boolean restoreTarget(IECOStorageMigrationCell cell, AEKey key,
        ECOInfiniteStorageEngine.RestoreTargetAmounts goal, IActionSource source) {
        long current = cell.getMigrationAmount(key);
        if (current < goal.before() || current > goal.after()) {
            throw new IllegalStateException("Restore target quantity differs from its saved plan");
        }
        long remaining = goal.after() - current;
        if (remaining == 0) return true;
        long inserted = cell.insertForMigration(key, remaining, Actionable.MODULATE, source);
        return inserted == remaining && cell.getMigrationAmount(key) == goal.after();
    }

    /**
     * A snapshot of the sealed source, taken in one tick: no live map iterator is held across ticks, so a later
     * persist, clear or handler rebuild cannot throw or silently skip entries. Two parallel arrays keep it compact.
     */
    private static final class Cursor {

        private IECOStorageMigrationCell cell;
        private final AEKey[] keys;
        private final long[] amounts;
        private int index;

        private Cursor(IECOStorageMigrationCell cell, AEKey[] keys, long[] amounts) {
            this.cell = cell;
            this.keys = keys;
            this.amounts = amounts;
        }

        static Cursor resume(@Nullable Cursor previous, IECOStorageMigrationCell cell) {
            java.util.List<AEKey> keys = new java.util.ArrayList<>();
            LongArrayList amounts = new LongArrayList();
            Iterator<Object2LongMap.Entry<AEKey>> entries = cell.migrationEntries();
            while (entries.hasNext()) {
                Object2LongMap.Entry<AEKey> entry = entries.next();
                keys.add(entry.getKey());
                amounts.add(entry.getLongValue());
            }
            if (previous != null && previous.hasSameContents(keys, amounts)) {
                // Same sealed contents behind a new handler or stack instance: keep the position.
                previous.cell = cell;
                return previous;
            }
            return new Cursor(cell, keys.toArray(new AEKey[0]), amounts.toLongArray());
        }

        private boolean hasSameContents(java.util.List<AEKey> otherKeys, LongArrayList otherAmounts) {
            if (otherKeys.size() != keys.length) return false;
            Object2LongOpenHashMap<AEKey> other = new Object2LongOpenHashMap<>(otherKeys.size());
            other.defaultReturnValue(Long.MIN_VALUE);
            for (int i = 0; i < otherKeys.size(); i++) other.put(otherKeys.get(i), otherAmounts.getLong(i));
            if (other.size() != keys.length) return false;
            for (int i = 0; i < keys.length; i++) {
                if (other.getLong(keys[i]) != amounts[i]) return false;
            }
            return true;
        }
    }
}
