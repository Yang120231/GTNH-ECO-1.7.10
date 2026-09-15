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
public final class ECOStorageBackend implements ECOStorageEngine {

    private ECOCapacityPolicy capacityPolicy;
    private final Map<ECOStorageKey, ECOAmount> entries;
    private ECOAmount used;
    private ECOAmount itemAmount;
    private ECOAmount fluidAmount;
    private ECOAmount customAmount;
    private long revision;
    private NBTTagCompound quarantinedSnapshot;
    private String failureReason;
    private Runnable mutationListener;
    private String acceptedChannel;
    private int maximumTypes = Integer.MAX_VALUE;

    public void setMaximumTypes(int maximumTypes) {
        this.maximumTypes = Math.max(0, maximumTypes);
    }

    public int getMaximumTypes() {
        return this.maximumTypes;
    }

    /** Restricts new inserts without making legacy contents inaccessible. */
    public void setAcceptedChannel(String channel) {
        this.acceptedChannel = channel;
    }

    public String getAcceptedChannel() {
        return this.acceptedChannel;
    }

    public ECOStorageBackend() {
        this(ECOCapacityPolicy.infinite());
    }

    public ECOStorageBackend(ECOCapacityPolicy capacityPolicy) {
        this.capacityPolicy = capacityPolicy == null ? ECOCapacityPolicy.infinite() : capacityPolicy;
        this.entries = new LinkedHashMap<ECOStorageKey, ECOAmount>();
        this.used = ECOAmount.ZERO;
        this.itemAmount = ECOAmount.ZERO;
        this.fluidAmount = ECOAmount.ZERO;
        this.customAmount = ECOAmount.ZERO;
        this.revision = 0L;
    }

    public ECOAmount insert(ECOStorageKey key, ECOAmount amount, boolean simulate) {
        requireKey(key);
        if (this.acceptedChannel != null && !this.acceptedChannel.equals(key.getChannel())) {
            return ECOAmount.ZERO;
        }
        if (!this.isHealthy() || amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        ECOAmount current = this.getAmount(key);
        if (current.isZero() && this.entries.size() >= this.maximumTypes) return ECOAmount.ZERO;
        ECOAmount accepted = this.limitInsert(key, current, amount);
        if (accepted.isZero() || simulate) {
            return accepted;
        }
        ECOAmount previousChannelAmount = this.getChannelAmount(key);
        ECOAmount next = current.add(accepted);
        this.entries.put(key, next);
        ECOAmount nextChannelAmount = previousChannelAmount.add(accepted);
        this.setChannelAmount(key, nextChannelAmount);
        if (this.capacityPolicy.isInfinite()) {
            this.used = this.used.add(accepted);
        } else {
            ECOAmount contentDelta = contentBytesForAmount(nextChannelAmount, amountPerByte(key))
                .subtract(contentBytesForAmount(previousChannelAmount, amountPerByte(key)));
            this.used = this.used.add(contentDelta);
            if (current.isZero()) {
                this.used = this.used.add(ECOAmount.of(this.capacityPolicy.getBytesPerType()));
            }
        }
        this.markDirty();
        return accepted;
    }

    public ECOAmount extract(ECOStorageKey key, ECOAmount amount, boolean simulate) {
        requireKey(key);
        if (!this.isHealthy() || amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        ECOAmount current = this.getAmount(key);
        ECOAmount extracted = amount.min(current);
        if (extracted.isZero() || simulate) {
            return extracted;
        }
        ECOAmount previousChannelAmount = this.getChannelAmount(key);
        ECOAmount remaining = current.subtract(extracted);
        if (remaining.isZero()) {
            this.entries.remove(key);
        } else {
            this.entries.put(key, remaining);
        }
        ECOAmount nextChannelAmount = previousChannelAmount.subtract(extracted);
        this.setChannelAmount(key, nextChannelAmount);
        if (this.capacityPolicy.isInfinite()) {
            this.used = this.used.subtract(extracted);
        } else {
            ECOAmount contentDelta = contentBytesForAmount(previousChannelAmount, amountPerByte(key))
                .subtract(contentBytesForAmount(nextChannelAmount, amountPerByte(key)));
            this.used = this.used.subtract(contentDelta);
            if (remaining.isZero()) {
                this.used = this.used.subtract(ECOAmount.of(this.capacityPolicy.getBytesPerType()));
            }
        }
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
        if (!this.isHealthy()) {
            throw new IllegalStateException("Cannot change an unavailable storage backend");
        }
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
        if (!this.isHealthy()) {
            throw new IllegalStateException("Cannot clear an unavailable storage backend");
        }
        if (!this.entries.isEmpty()) {
            this.entries.clear();
            this.used = ECOAmount.ZERO;
            this.itemAmount = ECOAmount.ZERO;
            this.fluidAmount = ECOAmount.ZERO;
            this.customAmount = ECOAmount.ZERO;
            this.markDirty();
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        ECOStorageCodec.read(tag, this);
    }

    public void writeToNBT(NBTTagCompound tag) {
        ECOStorageCodec.write(tag, this);
    }

    @Override
    public boolean isHealthy() {
        return this.quarantinedSnapshot == null;
    }

    @Override
    public String getFailureReason() {
        return this.failureReason == null ? "" : this.failureReason;
    }

    public void quarantine(NBTTagCompound original, String reason) {
        this.entries.clear();
        this.used = ECOAmount.ZERO;
        this.itemAmount = ECOAmount.ZERO;
        this.fluidAmount = ECOAmount.ZERO;
        this.customAmount = ECOAmount.ZERO;
        this.quarantinedSnapshot = original == null ? new NBTTagCompound() : (NBTTagCompound) original.copy();
        this.failureReason = reason == null ? "Unreadable storage snapshot" : reason;
    }

    public void setMutationListener(Runnable mutationListener) {
        this.mutationListener = mutationListener;
    }

    NBTTagCompound getQuarantinedSnapshot() {
        return this.quarantinedSnapshot;
    }

    void loadFromCodec(ECOCapacityPolicy capacityPolicy, Map<ECOStorageKey, ECOAmount> entries, ECOAmount used,
        long revision) {
        ECOCapacityPolicy nextPolicy = capacityPolicy == null ? ECOCapacityPolicy.infinite() : capacityPolicy;
        this.capacityPolicy = nextPolicy;
        this.entries.clear();
        this.entries.putAll(entries);
        this.recalculateChannelAmounts();
        this.used = calculateUsed(this.entries, nextPolicy);
        this.revision = revision;
        this.quarantinedSnapshot = null;
        this.failureReason = null;
    }

    Map<ECOStorageKey, ECOAmount> getEntriesForCodec() {
        return this.entries;
    }

    static ECOAmount calculateUsed(Map<ECOStorageKey, ECOAmount> entries, ECOCapacityPolicy policy) {
        if (policy == null || policy.isInfinite()) {
            ECOAmount total = ECOAmount.ZERO;
            for (ECOAmount amount : entries.values()) {
                total = total.add(amount);
            }
            return total;
        }
        ECOAmount itemAmount = ECOAmount.ZERO;
        ECOAmount fluidAmount = ECOAmount.ZERO;
        ECOAmount customAmount = ECOAmount.ZERO;
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : entries.entrySet()) {
            if (entry.getKey()
                .isItem()) {
                itemAmount = itemAmount.add(entry.getValue());
            } else if (entry.getKey()
                .isFluid()) {
                    fluidAmount = fluidAmount.add(entry.getValue());
                } else {
                    customAmount = customAmount.add(entry.getValue());
                }
        }
        ECOAmount content = contentBytesForAmount(itemAmount, 8L).add(contentBytesForAmount(fluidAmount, 8000L))
            .add(contentBytesForAmount(customAmount, 1L));
        return content.add(
            ECOAmount.of(
                BigInteger.valueOf(policy.getBytesPerType())
                    .multiply(BigInteger.valueOf(entries.size()))));
    }

    private ECOAmount limitInsert(ECOStorageKey key, ECOAmount current, ECOAmount requested) {
        if (this.capacityPolicy.isInfinite()) {
            return requested;
        }
        // Old releases had larger cells. Preserve their contents for extraction, but never
        // let partially used bytes admit more resources while the cell exceeds its new limit.
        if (!this.capacityPolicy.canHold(this.used)) {
            return ECOAmount.ZERO;
        }
        if (current.isZero() && (long) this.entries.size() >= this.capacityPolicy.getMaxTypes()) {
            return ECOAmount.ZERO;
        }
        ECOAmount remainingBytes = this.capacityPolicy.getRemaining(this.used);
        if (remainingBytes == null) {
            return ECOAmount.ZERO;
        }

        BigInteger keyHeadroom = BigInteger.valueOf(Long.MAX_VALUE)
            .subtract(current.toBigInteger());
        if (keyHeadroom.signum() <= 0) {
            return ECOAmount.ZERO;
        }
        BigInteger typeCost = current.isZero() ? BigInteger.valueOf(this.capacityPolicy.getBytesPerType())
            : BigInteger.ZERO;
        BigInteger writableBytes = remainingBytes.toBigInteger()
            .subtract(typeCost);
        if (writableBytes.signum() < 0) {
            return ECOAmount.ZERO;
        }
        ECOAmount channelAmount = this.getChannelAmount(key);
        BigInteger maximumChannelAmount = contentBytesForAmount(channelAmount, amountPerByte(key)).toBigInteger()
            .add(writableBytes)
            .multiply(BigInteger.valueOf(amountPerByte(key)));
        BigInteger accepted = maximumChannelAmount.subtract(channelAmount.toBigInteger())
            .min(requested.toBigInteger())
            .min(keyHeadroom);
        return accepted.signum() <= 0 ? ECOAmount.ZERO : ECOAmount.of(accepted);
    }

    private ECOAmount getChannelAmount(ECOStorageKey key) {
        return key.isItem() ? this.itemAmount : key.isFluid() ? this.fluidAmount : this.customAmount;
    }

    private void setChannelAmount(ECOStorageKey key, ECOAmount amount) {
        if (key.isItem()) {
            this.itemAmount = amount;
        } else if (key.isFluid()) {
            this.fluidAmount = amount;
        } else {
            this.customAmount = amount;
        }
    }

    private void recalculateChannelAmounts() {
        this.itemAmount = ECOAmount.ZERO;
        this.fluidAmount = ECOAmount.ZERO;
        this.customAmount = ECOAmount.ZERO;
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : this.entries.entrySet()) {
            this.setChannelAmount(
                entry.getKey(),
                this.getChannelAmount(entry.getKey())
                    .add(entry.getValue()));
        }
    }

    private static ECOAmount contentBytesForAmount(ECOAmount amount, long amountPerByte) {
        if (amount == null || amount.isZero()) {
            return ECOAmount.ZERO;
        }
        BigInteger divisor = BigInteger.valueOf(amountPerByte);
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
        if (this.mutationListener != null) {
            this.mutationListener.run();
        }
    }

    private static void requireKey(ECOStorageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Storage key must not be null");
        }
    }
}
