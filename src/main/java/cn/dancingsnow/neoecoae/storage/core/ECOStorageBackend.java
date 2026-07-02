package cn.dancingsnow.neoecoae.storage.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Mutable storage backend for ECO cells and host domains.
 *
 * <p>
 * This class is intentionally not thread-safe. AE2 1.7.10 storage access is expected to run on
 * the server thread; asynchronous users must work on {@link #snapshot()} instead of the live
 * backend.
 */
public final class ECOStorageBackend {

    private ECOCapacityPolicy capacityPolicy;
    private final Map<ECOStorageKey, ECOAmount> entries;
    private ECOAmount used;
    private long revision;

    public ECOStorageBackend() {
        this(ECOCapacityPolicy.infinite());
    }

    public ECOStorageBackend(ECOCapacityPolicy capacityPolicy) {
        this.capacityPolicy = capacityPolicy == null ? ECOCapacityPolicy.infinite() : capacityPolicy;
        this.entries = new LinkedHashMap<ECOStorageKey, ECOAmount>();
        this.used = ECOAmount.ZERO;
        this.revision = 0L;
    }

    public ECOAmount insert(ECOStorageKey key, ECOAmount amount, boolean simulate) {
        requireKey(key);
        if (amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        ECOAmount accepted = this.capacityPolicy.limitInsert(this.used, amount);
        if (accepted.isZero() || simulate) {
            return accepted;
        }
        ECOAmount current = this.getAmount(key);
        this.entries.put(key, current.add(accepted));
        this.used = this.used.add(accepted);
        this.markDirty();
        return accepted;
    }

    public ECOAmount extract(ECOStorageKey key, ECOAmount amount, boolean simulate) {
        requireKey(key);
        if (amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        ECOAmount current = this.getAmount(key);
        ECOAmount extracted = amount.min(current);
        if (extracted.isZero() || simulate) {
            return extracted;
        }
        ECOAmount remaining = current.subtract(extracted);
        if (remaining.isZero()) {
            this.entries.remove(key);
        } else {
            this.entries.put(key, remaining);
        }
        this.used = this.used.subtract(extracted);
        this.markDirty();
        return extracted;
    }

    public ECOAmount getAmount(ECOStorageKey key) {
        requireKey(key);
        ECOAmount amount = this.entries.get(key);
        return amount == null ? ECOAmount.ZERO : amount;
    }

    public ECOStorageSnapshot snapshot() {
        return new ECOStorageSnapshot(this.revision, this.used, this.entries);
    }

    public Map<ECOStorageKey, ECOAmount> getEntriesView() {
        return Collections.unmodifiableMap(this.entries);
    }

    public int getTypeCount() {
        return this.entries.size();
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public long getRevision() {
        return this.revision;
    }

    public ECOAmount getUsed() {
        return this.used;
    }

    public ECOCapacityPolicy getCapacityPolicy() {
        return this.capacityPolicy;
    }

    public void setCapacityPolicy(ECOCapacityPolicy capacityPolicy) {
        ECOCapacityPolicy nextPolicy = capacityPolicy == null ? ECOCapacityPolicy.infinite() : capacityPolicy;
        if (!nextPolicy.canHold(this.used)) {
            throw new IllegalArgumentException("Capacity policy cannot hold current contents");
        }
        this.capacityPolicy = nextPolicy;
        this.markDirty();
    }

    public void clear() {
        if (!this.entries.isEmpty()) {
            this.entries.clear();
            this.used = ECOAmount.ZERO;
            this.markDirty();
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        ECOStorageCodec.read(tag, this);
    }

    public void writeToNBT(NBTTagCompound tag) {
        ECOStorageCodec.write(tag, this);
    }

    void loadFromCodec(ECOCapacityPolicy capacityPolicy, Map<ECOStorageKey, ECOAmount> entries, ECOAmount used,
        long revision) {
        ECOCapacityPolicy nextPolicy = capacityPolicy == null ? ECOCapacityPolicy.infinite() : capacityPolicy;
        ECOAmount nextUsed = used == null ? ECOAmount.ZERO : used;
        if (!nextPolicy.canHold(nextUsed)) {
            throw new IllegalArgumentException("Stored contents exceed configured capacity");
        }
        this.capacityPolicy = nextPolicy;
        this.entries.clear();
        this.entries.putAll(entries);
        this.used = nextUsed;
        this.revision = revision;
    }

    Map<ECOStorageKey, ECOAmount> getEntriesForCodec() {
        return this.entries;
    }

    private void markDirty() {
        if (this.revision == Long.MAX_VALUE) {
            this.revision = 0L;
        } else {
            this.revision++;
        }
    }

    private static void requireKey(ECOStorageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Storage key must not be null");
        }
    }
}
