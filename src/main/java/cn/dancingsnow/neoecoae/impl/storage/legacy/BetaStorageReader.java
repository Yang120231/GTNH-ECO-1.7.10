package cn.dancingsnow.neoecoae.impl.storage.legacy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.NbtAccounter;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/** Read-only decoder for the format shipped by v7.3.0-beta1. */
public final class BetaStorageReader {

    public static final String DOMAIN_FILE = "neoecoae_storage_domains.dat";

    private BetaStorageReader() {}

    public static ListTag entries(CompoundTag storage, boolean finite) {
        if (storage.getBoolean("unavailable")) throw new IllegalStateException("Quarantined beta storage snapshot");
        int version = storage.getInt("version");
        if (version < 0 || version > 3) throw new IllegalStateException("Unsupported beta storage version: " + version);
        requireList(storage, "entries");
        ListTag result = new ListTag();
        for (Tag raw : storage.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag old = (CompoundTag) raw;
            if (!old.contains("key", Tag.TAG_COMPOUND) || !old.contains("amount", Tag.TAG_COMPOUND))
                throw new IllegalStateException("Incomplete beta storage entry");
            CompoundTag amount = old.getCompound("amount");
            BigInteger count;
            if (amount.contains("big")) {
                if (!amount.contains("big", Tag.TAG_STRING)) throw new IllegalStateException("Invalid beta big amount");
                try {
                    count = new BigInteger(amount.getString("big"));
                } catch (NumberFormatException failure) {
                    throw new IllegalStateException("Invalid beta big amount", failure);
                }
            } else if (amount.contains("long", Tag.TAG_LONG)) count = BigInteger.valueOf(amount.getLong("long"));
            else throw new IllegalStateException("Invalid beta storage amount");
            if (count.signum() < 0) throw new IllegalStateException("Negative beta storage amount");
            CompoundTag entry = new CompoundTag();
            entry.put("key", key(old.getCompound("key")));
            if (finite) {
                if (count.bitLength() > 63) throw new IllegalStateException("Beta finite amount exceeds long");
                entry.putLong("amount", count.longValue());
            }
            else entry.put("amount", amount.copy());
            result.add(entry);
        }
        return result;
    }

    private static CompoundTag key(CompoundTag old) {
        String channel = old.getString("channel"), identity = old.getString("identity");
        if (identity.isEmpty()) throw new IllegalStateException("Missing beta storage identity");
        CompoundTag key = new CompoundTag();
        boolean item = "item".equals(channel);
        if (!item && !"fluid".equals(channel)) throw new IllegalStateException("Unknown beta channel: " + channel);
        key.putString("#type", item ? "ae2:i" : "ae2:f");
        if (item) {
            key.putString("id", identity);
            key.putInt("damage", old.getInt("metadata"));
        } else {
            key.putString("FluidName", identity);
            key.putInt("Amount", 1);
        }
        String variant = old.getString("variant");
        if (!variant.isEmpty()) {
            try {
                key.put(
                    item ? "tag" : "Tag",
                    new CompoundTag(
                        CompressedStreamTools.readCompressed(
                            new ByteArrayInputStream(
                                Base64.getDecoder()
                                    .decode(variant)))));
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Unreadable beta storage variant", failure);
            }
        }
        return key;
    }

    /**
     * Beta kept every domain in one compressed file. Index it once per file version instead of decompressing
     * the whole file for each host, drive and domain during world load.
     */
    public static CompoundTag domain(Path file, UUID id) throws IOException {
        if (!Files.exists(file)) return null;
        DomainIndex index = index(file);
        if (index.failure != null) throw new IOException(index.failure.getMessage(), index.failure);
        if (index.duplicates.contains(id.toString())) throw new IOException("Duplicate beta domain: " + id);
        CompoundTag found = index.domains.get(id.toString());
        return found == null ? null : found.copy();
    }

    /** Drops the cached beta domain index; called when the server stops. */
    public static synchronized void clearCache() {
        DOMAIN_CACHE.clear();
    }

    private static synchronized DomainIndex index(Path file) throws IOException {
        Path key = file.toAbsolutePath()
            .normalize();
        long size = Files.size(file), modified = Files.getLastModifiedTime(file)
            .toMillis();
        var cached = DOMAIN_CACHE.get(key);
        DomainIndex index = cached == null ? null : cached.get();
        if (index != null && index.size == size && index.modified == modified) return index;
        index = readIndex(file, size, modified);
        // Failures are tiny and kept strongly so an oversized file is not decompressed again on every call.
        DOMAIN_CACHE.put(key, index.failure != null ? new StrongRef(index) : new java.lang.ref.SoftReference<>(index));
        return index;
    }

    private static DomainIndex readIndex(Path file, long size, long modified) {
        var domains = new java.util.HashMap<String, CompoundTag>();
        var duplicates = new java.util.HashSet<String>();
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(256L * 1024 * 1024));
            if (!root.contains("data", Tag.TAG_COMPOUND)) throw new IOException("Missing beta domain data");
            CompoundTag data = root.getCompound("data");
            if (data.getInt("dataVersion") > 2) throw new IOException("Unsupported beta domain data version");
            requireList(data, "domains");
            for (Tag raw : data.getList("domains", Tag.TAG_COMPOUND)) {
                CompoundTag domain = (CompoundTag) raw;
                String id = domain.getString("id");
                if (domains.put(id, domain) != null) duplicates.add(id);
            }
            return new DomainIndex(size, modified, domains, duplicates, null);
        } catch (IOException | RuntimeException failure) {
            return new DomainIndex(size, modified, domains, duplicates, failure);
        }
    }

    private static final java.util.Map<Path, java.lang.ref.Reference<DomainIndex>> DOMAIN_CACHE = new java.util.HashMap<>();

    @com.github.bsideup.jabel.Desugar
    private record DomainIndex(long size, long modified, java.util.Map<String, CompoundTag> domains,
        java.util.Set<String> duplicates, Exception failure) {}

    private static final class StrongRef extends java.lang.ref.SoftReference<DomainIndex> {

        private final DomainIndex index;

        private StrongRef(DomainIndex index) {
            super(index);
            this.index = index;
        }

        @Override
        public DomainIndex get() {
            return index;
        }
    }

    public static CompoundTag convertDomain(CompoundTag old) {
        if (!old.contains("storage", Tag.TAG_COMPOUND)) throw new IllegalStateException("Missing beta domain storage");
        // Before Completed is saved, the beta domain still owns every resource and all
        // target disks remain sealed. Resume using the new restore protocol from that source.
        CompoundTag result = new CompoundTag();
        result.putInt("version", 3);
        result.put("entries", entries(old.getCompound("storage"), false));
        ListTag receipts = new ListTag();
        if (old.contains("committedSources")) {
            requireList(old, "committedSources");
            for (Tag raw : old.getList("committedSources", Tag.TAG_COMPOUND)) {
                UUID id = uuid(((CompoundTag) raw).getString("diskId"));
                CompoundTag uuid = new CompoundTag();
                uuid.putUUID("id", id);
                receipts.add(uuid.get("id"));
            }
        }
        result.put("migrations", receipts);
        return result;
    }

    public static void requireList(CompoundTag tag, String name) {
        Tag raw = tag.get(name);
        if (!(raw instanceof ListTag)
            || !((ListTag) raw).isEmpty() && ((ListTag) raw).getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalStateException("Missing or invalid beta list: " + name);
    }

    /** Virtual metadata view: reading a tooltip or simulating a transfer never edits the item. */
    public static CompoundTag customData(CompoundTag root) {
        CompoundTag result = new CompoundTag();
        String mode = root.getString("ECOStorageMode");
        if (root.contains("ECODiskId"))
            result.putUUID("neoecoae_member_identity", uuid(root.getString("ECODiskId")));
        if (!"domain_member".equals(mode) && !"migrating".equals(mode)) return result;
        UUID domain = uuid(root.getString("ECOHostDomainId"));
        UUID disk = uuid(root.getString("ECODiskId"));
        if ("domain_member".equals(mode)) {
            result.putBoolean("neoecoae_infinite_member", true);
            result.putUUID("neoecoae_infinite_domain", domain);
        } else {
            result.putUUID("neoecoae_migration_id", disk);
            result.putUUID("neoecoae_migration_domain", domain);
        }
        return result;
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Invalid beta UUID: " + raw, failure);
        }
    }
}
