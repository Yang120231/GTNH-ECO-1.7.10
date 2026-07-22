package cn.dancingsnow.neoecoae.storage.core;

import java.math.BigInteger;
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
        ECOAmount current = this.getAmount(key);
        ECOAmount accepted = this.limitInsert(key, current, amount);
        if (accepted.isZero() || simulate) {
            return accepted;
        }
        ECOAmount next = current.add(accepted);
        this.entries.put(key, next);
        this.used = this.used.add(
            storageBytes(key, next, this.capacityPolicy).subtract(storageBytes(key, current, this.capacityPolicy)));
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
        this.used = this.used.subtract(
            storageBytes(key, current, this.capacityPolicy)
                .subtract(storageBytes(key, remaining, this.capacityPolicy)));
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
        ECOAmount nextUsed = calculateUsed(this.entries, nextPolicy);
        if (!nextPolicy.canHold(nextUsed)) {
            throw new IllegalArgumentException("Capacity policy cannot hold current contents");
        }
        this.capacityPolicy = nextPolicy;
        this.used = nextUsed;
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
        this.capacityPolicy = nextPolicy;
        this.entries.clear();
        this.entries.putAll(entries);
        this.used = calculateUsed(this.entries, nextPolicy);
        this.revision = revision;
    }

    Map<ECOStorageKey, ECOAmount> getEntriesForCodec() {
        return this.entries;
    }

    static ECOAmount calculateUsed(Map<ECOStorageKey, ECOAmount> entries, ECOCapacityPolicy policy) {
        ECOAmount total = ECOAmount.ZERO;
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : entries.entrySet()) {
            total = total.add(storageBytes(entry.getKey(), entry.getValue(), policy));
        }
        return total;
    }

    private ECOAmount limitInsert(ECOStorageKey key, ECOAmount current, ECOAmount requested) {
        if (this.capacityPolicy.isInfinite()) {
            return requested;
        }
        if (current.isZero() && (long) this.entries.size() >= this.capacityPolicy.getMaxTypes()) {
            return ECOAmount.ZERO;
        }
        ECOAmount remainingBytes = this.capacityPolicy.getRemaining(this.used);
        if (remainingBytes == null) {
            return ECOAmount.ZERO;
        }

        BigInteger typeCost = current.isZero() ? BigInteger.valueOf(this.capacityPolicy.getBytesPerType())
            : BigInteger.ZERO;
        BigInteger writableBytes = remainingBytes.toBigInteger()
            .subtract(typeCost);
        if (writableBytes.signum() < 0) {
            return ECOAmount.ZERO;
        }
        BigInteger maximumTotalAmount = contentBytes(key, current).toBigInteger()
            .add(writableBytes)
            .multiply(BigInteger.valueOf(amountPerByte(key)));
        BigInteger accepted = maximumTotalAmount.subtract(current.toBigInteger())
            .min(requested.toBigInteger());
        return accepted.signum() <= 0 ? ECOAmount.ZERO : ECOAmount.of(accepted);
    }

    private static ECOAmount storageBytes(ECOStorageKey key, ECOAmount amount, ECOCapacityPolicy policy) {
        if (amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        if (policy == null || policy.isInfinite()) {
            return amount;
        }
        return contentBytes(key, amount).add(ECOAmount.of(policy.getBytesPerType()));
    }

    private static ECOAmount contentBytes(ECOStorageKey key, ECOAmount amount) {
        if (amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        BigInteger divisor = BigInteger.valueOf(amountPerByte(key));
        BigInteger[] quotient = amount.toBigInteger()
            .divideAndRemainder(divisor);
        return ECOAmount.of(quotient[0].add(quotient[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE));
    }

    private static long amountPerByte(ECOStorageKey key) {
        return key != null && key.isFluid() ? 8000L : key != null && key.isItem() ? 8L : 1L;
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
