package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.SavedData;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.IOUtilities;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.NbtAccounter;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;
import cn.dancingsnow.neoecoae.platform.nbt.NbtUtils;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/**
 * World-owned inventory and transfer receipts, saved together in one atomic snapshot by the vanilla
 * {@link net.minecraft.world.level.storage.DimensionDataStorage} cycle (autosave, {@code /save-all}, shutdown).
 */
public final class ECOInfiniteStorageData extends SavedData {

    public static final int CURRENT_VERSION = 3;

    public enum DomainStatus {
        HEALTHY,
        DEGRADED,
        RECOVERY_READ_ONLY,
        UNAVAILABLE
    }

    interface KeyCodec {

        CompoundTag encode(AEKey key);

        AEKey decode(CompoundTag tag);
    }

    @com.github.bsideup.jabel.Desugar
    private record Restore(UUID transaction, Set<UUID> targets, String failure,
        Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {}

    final InfiniteStorageAmounts amounts = new InfiniteStorageAmounts();
    private final List<CompoundTag> rawEntries = new ArrayList<>();
    private final Set<UUID> migrationReceipts = new HashSet<>();
    private final Map<AEKey, Restore> restores = new HashMap<>();
    private CompoundTag unresolvedMetadata = new CompoundTag();
    private boolean unreadable;
    private boolean incompleteLegacy;
    private boolean mountedStorageAvailable = true;
    private String failure;
    // Written by the IO worker when an asynchronous snapshot write fails; the next save retries.
    private volatile String writeFailure;
    private long revision;
    // Last sequence of the retired write-ahead journal already folded into this snapshot.
    private long journalSequence;
    private KeyCodec codec;

    public static ECOInfiniteStorageData createNew() {
        return new ECOInfiniteStorageData();
    }

    /**
     * Vanilla load path. The deserializer runs only when {@code dataFile} was read successfully; if vanilla failed to
     * read an existing file, the constructor fallback leaves the domain unreadable so that file is never overwritten.
     */
    public static SavedData.Factory<ECOInfiniteStorageData> factory(Path dataFile,
        java.util.function.Supplier<ECOInfiniteStorageData> fresh) {
        return new SavedData.Factory<>(() -> {
            if (Files.exists(dataFile)) {
                ECOInfiniteStorageData data = createNew();
                data.markUnreadable(
                    "Cannot read authoritative domain snapshot " + dataFile.getFileName() + "; original file retained");
                return data;
            }
            return fresh.get();
        }, (tag, registries) -> {
            ECOInfiniteStorageData data = load(tag, registries);
            if (data.canRead()) {
                try {
                    // Upgrade worlds written by the old write-ahead journal; the next save folds it in.
                    replayJournal(data, dataFile, registries);
                } catch (IOException | RuntimeException e) {
                    data = createNew();
                    data.markUnreadable("Cannot replay legacy domain journal; original files retained: " + e);
                }
            }
            return data;
        });
    }

    public static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag, registries, null);
    }

    static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries, KeyCodec codec) {
        ECOInfiniteStorageData data = createNew();
        data.codec = codec;
        int version = tag.getInt("version");
        if (version < 1 || version > CURRENT_VERSION || tag.getBoolean("journal")) {
            data.markUnreadable("Unsupported or unresolved infinite storage format: " + version);
            return data;
        }
        if (!validList(tag, "entries", Tag.TAG_COMPOUND)) {
            data.markUnreadable("Missing or invalid inventory entries");
            return data;
        }
        data.unresolvedMetadata = tag.getCompound("unresolvedMetadata")
            .copy();
        data.journalSequence = tag.getLong("journalSequence");
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                CompoundTag amountTag = entry.getCompound("amount");
                if (amountTag.contains("big") ? !amountTag.contains("big", Tag.TAG_STRING)
                    : !amountTag.contains("long", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid amount");
                HugeAmount amount = HugeAmount.read(amountTag);
                AEKey key = codec == null ? AEKey.fromTagGeneric(registries, entry.getCompound("key"))
                    : codec.decode(entry.getCompound("key"));
                if (key == null) throw new IllegalArgumentException("Unknown key");
                Restore restore = null;
                if (entry.contains("restore")) {
                    if (!entry.hasUUID("restore")) throw new IllegalArgumentException("Invalid restore transaction");
                    Set<UUID> targets = new HashSet<>();
                    if (entry.contains("restoreTargets")) {
                        if (!validList(entry, "restoreTargets", Tag.TAG_INT_ARRAY))
                            throw new IllegalArgumentException("Invalid restore targets");
                        for (Tag target : entry.getList("restoreTargets", Tag.TAG_INT_ARRAY))
                            targets.add(NbtUtils.loadUUID(target));
                    }
                    Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan = new HashMap<>();
                    if (entry.contains("restorePlan")) {
                        if (!validList(entry, "restorePlan", Tag.TAG_COMPOUND))
                            throw new IllegalArgumentException("Invalid restore plan");
                        for (Tag value : entry.getList("restorePlan", Tag.TAG_COMPOUND)) {
                            CompoundTag target = (CompoundTag) value;
                            if (!target.hasUUID("target") || !target.contains("before", Tag.TAG_LONG)
                                || !target.contains("after", Tag.TAG_LONG)
                                || !target.contains("transferred", Tag.TAG_LONG)) {
                                throw new IllegalArgumentException("Incomplete restore target");
                            }
                            if (plan.put(
                                target.getUUID("target"),
                                new ECOInfiniteStorageEngine.RestoreTargetAmounts(
                                    target.getLong("before"),
                                    target.getLong("after"),
                                    target.getLong("transferred")))
                                != null) {
                                throw new IllegalArgumentException("Duplicate restore target");
                            }
                        }
                    }
                    if (!validRestorePlan(amount, targets, plan))
                        throw new IllegalArgumentException("Incomplete restore allocation");
                    restore = new Restore(entry.getUUID("restore"), targets, entry.getString("restoreFailure"), plan);
                }
                if (amount.isZero() && restore != null) throw new IllegalArgumentException("Empty reserved entry");
                // Decode all metadata before changing the authoritative quantity.
                data.amounts.set(
                    key,
                    data.amounts.get(key)
                        .add(amount));
                if (restore != null) data.restores.put(key, restore);
            } catch (RuntimeException e) {
                data.rawEntries.add(entry.copy());
            }
        }
        if (tag.contains("migrations") && !validList(tag, "migrations", Tag.TAG_INT_ARRAY)) {
            data.unresolvedMetadata.put(
                "migrations",
                tag.get("migrations")
                    .copy());
        } else {
            ListTag invalid = new ListTag();
            for (Tag receipt : tag.getList("migrations", Tag.TAG_INT_ARRAY)) {
                try {
                    data.migrationReceipts.add(NbtUtils.loadUUID(receipt));
                } catch (RuntimeException e) {
                    invalid.add(receipt.copy());
                }
            }
            if (!invalid.isEmpty()) data.unresolvedMetadata.put("migrations", invalid);
        }
        data.refreshMountedStorageAvailability();
        return data;
    }

    private static boolean validList(CompoundTag tag, String name, int type) {
        return tag.get(name) instanceof ListTag list && (list.isEmpty() || list.getElementType() == type);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return capture(registries).write(tag, registries);
    }

    /**
     * Server-thread copy of everything {@link #save(CompoundTag, HolderLookup.Provider)} writes. Building the NBT of a
     * 100k-key domain takes tens of milliseconds, so the file path captures primitives here and encodes on the IO
     * worker. Item and fluid keys only read their immutable identity and the registry name table; any other key type
     * (or a test codec) is encoded during the capture.
     */
    private Snapshot capture(HolderLookup.Provider registries) {
        if (unreadable || incompleteLegacy) throw new IllegalStateException("Refusing to save an incomplete domain");
        int size = amounts.size();
        AEKey[] keys = new AEKey[size];
        long[] values = new long[size];
        CompoundTag[] encoded = new CompoundTag[size];
        Map<AEKey, java.math.BigInteger> big = new HashMap<>();
        int[] index = { 0 };
        amounts.visitVisible((key, amount) -> {
            int i = index[0]++;
            keys[i] = key;
            values[i] = amount;
            if (amount == Long.MAX_VALUE) {
                HugeAmount exact = amounts.get(key);
                if (exact.isBig()) big.put(key, exact.toBigInteger());
            }
            if (codec != null) encoded[i] = codec.encode(key);
            else if (!(key instanceof cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey)
                && !(key instanceof cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey))
                encoded[i] = key.toTagGeneric(registries);
        });
        Map<AEKey, Restore> restoreCopies = new HashMap<>();
        restores.forEach(
            (key, restore) -> restoreCopies.put(
                key,
                new Restore(
                    restore.transaction(),
                    new java.util.LinkedHashSet<>(restore.targets()),
                    restore.failure(),
                    new java.util.LinkedHashMap<>(restore.plan()))));
        List<CompoundTag> raw = new ArrayList<>(rawEntries.size());
        rawEntries.forEach(entry -> raw.add(entry.copy()));
        return new Snapshot(
            keys,
            values,
            encoded,
            big,
            restoreCopies,
            raw,
            new ArrayList<>(migrationReceipts),
            unresolvedMetadata.isEmpty() ? null : unresolvedMetadata.copy(),
            journalSequence);
    }

    @com.github.bsideup.jabel.Desugar
    private record Snapshot(AEKey[] keys, long[] values, CompoundTag[] encoded, Map<AEKey, java.math.BigInteger> big,
        Map<AEKey, Restore> restores, List<CompoundTag> raw, List<UUID> receipts, CompoundTag unresolved,
        long journalSequence) {

        CompoundTag write(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("version", CURRENT_VERSION);
            ListTag entries = new ListTag();
            for (int i = 0; i < keys.length; i++) {
                AEKey key = keys[i];
                CompoundTag entry = new CompoundTag();
                entry.put("key", encoded[i] != null ? encoded[i] : key.toTagGeneric(registries));
                java.math.BigInteger exact = big.get(key);
                entry.put("amount", (exact == null ? HugeAmount.of(values[i]) : HugeAmount.of(exact)).write());
                Restore restore = restores.get(key);
                if (restore != null) {
                    entry.putUUID("restore", restore.transaction());
                    ListTag targets = new ListTag();
                    restore.targets()
                        .forEach(id -> targets.add(NbtUtils.createUUID(id)));
                    entry.put("restoreTargets", targets);
                    ListTag plan = new ListTag();
                    restore.plan()
                        .forEach((id, amounts) -> {
                            CompoundTag target = new CompoundTag();
                            target.putUUID("target", id);
                            target.putLong("before", amounts.before());
                            target.putLong("after", amounts.after());
                            target.putLong("transferred", amounts.transferred());
                            plan.add(target);
                        });
                    entry.put("restorePlan", plan);
                    if (!restore.failure()
                        .isEmpty()) entry.putString("restoreFailure", restore.failure());
                }
                entries.add(entry);
            }
            raw.forEach(entries::add);
            tag.put("entries", entries);
            ListTag receiptTags = new ListTag();
            receipts.forEach(id -> receiptTags.add(NbtUtils.createUUID(id)));
            tag.put("migrations", receiptTags);
            if (unresolved != null) tag.put("unresolvedMetadata", unresolved);
            tag.putLong("journalSequence", journalSequence);
            return tag;
        }
    }

    /**
     * Same contract as vanilla {@link SavedData#save(File, HolderLookup.Provider)}: capture on the server thread,
     * encode and write atomically on the IO worker. Unreadable or partially imported domains are never written, and a
     * failed encode or write keeps the domain due for the next save.
     */
    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (unreadable || incompleteLegacy) return;
        if (!isDirty() && writeFailure == null) return;
        Snapshot snapshot;
        try {
            snapshot = capture(registries);
        } catch (RuntimeException e) {
            LogManager.getLogger(ECOInfiniteStorageData.class)
                .error("Cannot serialize infinite domain {}", file, e);
            writeFailure = e.toString();
            return;
        }
        Path target = file.toPath();
        IOUtilities.withIOWorker(() -> {
            try {
                CompoundTag root = new CompoundTag();
                root.put("data", snapshot.write(new CompoundTag(), registries));
                NbtUtils.addCurrentDataVersion(root);
                IOUtilities.writeNbtCompressed(root, target);
                // The snapshot records journalSequence, so a journal left behind by a crash here is harmless.
                Files.deleteIfExists(journalPath(target.toAbsolutePath()));
                writeFailure = null;
            } catch (IOException | RuntimeException e) {
                if (writeFailure == null) LogManager.getLogger(ECOInfiniteStorageData.class)
                    .error("Cannot save infinite domain {}", file, e);
                writeFailure = e.toString();
            }
        });
        setDirty(false);
    }

    /** Structural change: also advances {@link #revision()} so revision-keyed caches refresh. */
    @Override
    public void setDirty() {
        revision++;
        refreshMountedStorageAvailability();
        super.setDirty();
    }

    private void refreshMountedStorageAvailability() {
        mountedStorageAvailable = canWrite() && restores.isEmpty();
    }

    boolean canUseMountedStorage() {
        return mountedStorageAvailable;
    }

    public long revision() {
        return revision;
    }

    public String lastFailureReason() {
        String write = writeFailure;
        return write != null ? write : failure;
    }

    public HugeAmount getAmount(AEKey key) {
        return amounts.get(key);
    }

    public boolean isEmpty() {
        return !unreadable && !incompleteLegacy && amounts.isEmpty() && rawEntries.isEmpty() && restores.isEmpty();
    }

    public boolean canRead() {
        return !unreadable;
    }

    public boolean canRead(AEKey key) {
        return canRead() && !restores.containsKey(key);
    }

    public boolean canWrite() {
        return !unreadable && !incompleteLegacy;
    }

    public boolean canWrite(AEKey key) {
        return canWrite() && !restores.containsKey(key);
    }

    public boolean canExitOrRestore() {
        // Leaving infinite mode relies on the next snapshot landing; wait until a failed write has recovered.
        return canWrite() && writeFailure == null && rawEntries.isEmpty() && unresolvedMetadata.isEmpty();
    }

    public boolean canMigrate() {
        return canExitOrRestore();
    }

    /** The first condition that makes {@link #canMigrate()} or {@link #canWrite(AEKey)} refuse, or null. */
    public String migrationBlockedReason(AEKey key) {
        if (unreadable) return ECOInfiniteStorageTransfer.BLOCKED_DOMAIN_UNREADABLE;
        if (incompleteLegacy) return ECOInfiniteStorageTransfer.BLOCKED_LEGACY_INCOMPLETE;
        if (writeFailure != null) return ECOInfiniteStorageTransfer.BLOCKED_WRITE_FAILED;
        if (!rawEntries.isEmpty() || !unresolvedMetadata.isEmpty())
            return ECOInfiniteStorageTransfer.BLOCKED_UNRESOLVED_ENTRIES;
        if (key != null && restores.containsKey(key)) return ECOInfiniteStorageTransfer.BLOCKED_RESTORE_PENDING;
        return null;
    }

    // Quantity changes only mark the snapshot due; they leave revision() alone so hot FE/item transfers don't
    // invalidate revision-keyed caches every operation. Callers bump the revision for structural changes.
    void add(AEKey key, long amount) {
        amounts.add(key, amount);
        setDirty(true);
    }

    void add(AEKey key, BigInteger amount) {
        amounts.add(key, amount);
        setDirty(true);
    }

    void subtract(AEKey key, long amount) {
        amounts.subtract(key, amount);
        setDirty(true);
    }

    public boolean hasMigrationReceipt(UUID transaction) {
        return migrationReceipts.contains(transaction);
    }

    public void addMigrationReceipt(UUID transaction) {
        if (migrationReceipts.add(transaction)) setDirty();
    }

    public void addMigrationReceipts(Set<UUID> transactions) {
        boolean changed = false;
        for (UUID transaction : transactions) {
            if (transaction != null) changed |= migrationReceipts.add(transaction);
        }
        if (changed) setDirty();
    }

    static void replayJournal(ECOInfiniteStorageData data, Path snapshot, HolderLookup.Provider registries)
        throws IOException {
        Path path = journalPath(snapshot.toAbsolutePath());
        if (!Files.isRegularFile(path)) return;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            while (true) {
                int length;
                try {
                    length = input.readInt();
                } catch (EOFException end) {
                    break;
                }
                if (length <= 0 || length > 16 * 1024 * 1024) {
                    throw new IOException("Invalid infinite-domain journal record length " + length);
                }
                byte[] payload = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.readNBytes(input, length);
                // An interrupted buffered FE batch can leave an unacknowledged tail.
                if (payload.length != length) break;
                CompoundTag record;
                try (DataInputStream recordInput = new DataInputStream(new ByteArrayInputStream(payload))) {
                    record = NbtIo.read(recordInput, NbtAccounter.create(16L * 1024 * 1024));
                }
                long sequence = record.getLong("sequence");
                if (sequence <= data.journalSequence) continue;
                if (sequence != data.journalSequence + 1L) {
                    throw new IOException("Non-contiguous infinite-domain journal sequence " + sequence);
                }
                AEKey key = data.codec == null ? AEKey.fromTagGeneric(registries, record.getCompound("key"))
                    : data.codec.decode(record.getCompound("key"));
                long amount = record.getLong("amount");
                if (key == null || amount <= 0L) throw new IOException("Invalid infinite-domain journal change");
                if (record.getBoolean("added")) {
                    data.amounts.add(key, amount);
                } else {
                    if (data.amounts.get(key)
                        .compareTo(HugeAmount.of(amount)) < 0) {
                        throw new IOException("Infinite-domain journal extraction exceeds snapshot amount");
                    }
                    data.amounts.subtract(key, amount);
                }
                data.journalSequence = sequence;
                data.setDirty();
            }
        }
    }

    private static Path journalPath(Path snapshot) {
        return snapshot.resolveSibling(snapshot.getFileName() + ".journal");
    }

    public DomainStatus status() {
        if (unreadable) return DomainStatus.UNAVAILABLE;
        if (incompleteLegacy) return DomainStatus.RECOVERY_READ_ONLY;
        if (writeFailure != null || !rawEntries.isEmpty()
            || !unresolvedMetadata.isEmpty()
            || restores.values()
                .stream()
                .anyMatch(
                    r -> !r.failure()
                        .isEmpty()))
            return DomainStatus.DEGRADED;
        return DomainStatus.HEALTHY;
    }

    public List<String> failures() {
        List<String> result = new ArrayList<>();
        String write = writeFailure;
        if (write != null) result.add("Last snapshot write failed, retrying on next save: " + write);
        if (failure != null) result.add(failure);
        if (!rawEntries.isEmpty()) result.add("Unresolved entries retained: " + rawEntries.size());
        if (!unresolvedMetadata.isEmpty()) result.add("Unresolved transfer metadata retained");
        restores.values()
            .stream()
            .map(Restore::failure)
            .filter(s -> !s.isEmpty())
            .forEach(result::add);
        return result;
    }

    public void markUnreadable(String reason) {
        unreadable = true;
        refreshMountedStorageAvailability();
        failure = reason;
    }

    public void markIncompleteLegacy(List<String> failures) {
        incompleteLegacy = true;
        refreshMountedStorageAvailability();
        failure = String.join("; ", failures);
    }

    public boolean reserveRestore(AEKey key, UUID transaction, Set<UUID> targets,
        Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
        if (!canReserveRestore(key, transaction, targets, plan)) return false;
        Restore existing = restores.get(key);
        if (existing != null) {
            if (!existing.plan()
                .isEmpty()) return true;
            // Old reservations have no target quantities. Upgrade them before touching any target.
        }
        restores.put(key, new Restore(transaction, targets, "", plan));
        setDirty();
        return true;
    }

    public boolean canReserveRestore(AEKey key, UUID transaction, Set<UUID> targets,
        Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
        if (!canWrite() || amounts.visible(key) == 0 || !validRestorePlan(getAmount(key), targets, plan)) return false;
        Restore existing = restores.get(key);
        return existing == null || (existing.failure()
            .isEmpty()
            && existing.transaction()
                .equals(transaction)
            && targets.containsAll(existing.targets())
            && (existing.plan()
                .isEmpty()
                || existing.plan()
                    .equals(plan)));
    }

    private static boolean validRestorePlan(HugeAmount amount, Set<UUID> targets,
        Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
        if (plan.isEmpty()) return true; // v1/v2 reservation, upgraded before the next target mutation
        if (!targets.containsAll(plan.keySet())) return false;
        java.math.BigInteger allocated = java.math.BigInteger.ZERO;
        for (var target : plan.values()) allocated = allocated.add(java.math.BigInteger.valueOf(target.transferred()));
        return allocated.equals(
            amount.toBigInteger()
                .min(java.math.BigInteger.valueOf(Long.MAX_VALUE)));
    }

    public Set<UUID> restoreTargetIds(AEKey key) {
        Restore restore = restores.get(key);
        return restore == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf() : restore.targets();
    }

    public UUID restoreTransaction(AEKey key) {
        Restore restore = restores.get(key);
        return restore == null ? null : restore.transaction();
    }

    public boolean hasPendingRestore() {
        return !restores.isEmpty();
    }

    public void failRestore(AEKey key, String reason) {
        Restore restore = restores.get(key);
        if (restore != null) {
            restores.put(key, new Restore(restore.transaction(), restore.targets(), reason, restore.plan()));
            setDirty();
        }
    }

    public Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> restorePlan(AEKey key) {
        Restore restore = restores.get(key);
        return restore == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf() : restore.plan();
    }

    public boolean finishRestore(AEKey key, UUID transaction) {
        if (!canFinishRestore(key, transaction)) return false;
        Restore restore = restores.remove(key);
        long transferred = 0L;
        for (var target : restore.plan()
            .values()) {
            transferred = Math.addExact(transferred, target.transferred());
        }
        amounts.subtract(key, transferred);
        setDirty();
        return true;
    }

    public boolean canFinishRestore(AEKey key, UUID transaction) {
        Restore restore = restores.get(key);
        return canWrite() && restore != null
            && restore.failure()
                .isEmpty()
            && restore.transaction()
                .equals(transaction);
    }
}
