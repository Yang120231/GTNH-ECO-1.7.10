package cn.dancingsnow.neoecoae.storage.core;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

public final class ECOCapacityPolicy {

    private static final ECOCapacityPolicy INFINITE = new ECOCapacityPolicy(true, null, 0L);

    private final boolean infinite;
    private final ECOAmount capacity;
    private final long bytesPerType;

    private ECOCapacityPolicy(boolean infinite, ECOAmount capacity, long bytesPerType) {
        this.infinite = infinite;
        this.capacity = capacity;
        this.bytesPerType = Math.max(0L, bytesPerType);
    }

    public static ECOCapacityPolicy infinite() {
        return INFINITE;
    }

    public static ECOCapacityPolicy finite(long capacity) {
        return finite(ECOAmount.of(capacity));
    }

    public static ECOCapacityPolicy finite(BigInteger capacity) {
        return finite(ECOAmount.of(capacity));
    }

    public static ECOCapacityPolicy finite(ECOAmount capacity) {
        return finite(capacity, 0L);
    }

    public static ECOCapacityPolicy finite(long capacity, long bytesPerType) {
        return finite(ECOAmount.of(capacity), bytesPerType);
    }

    public static ECOCapacityPolicy finite(ECOAmount capacity, long bytesPerType) {
        if (capacity == null) {
            throw new IllegalArgumentException("Capacity must not be null");
        }
        return new ECOCapacityPolicy(false, capacity, bytesPerType);
    }

    public static ECOCapacityPolicy readFromNBT(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags() || tag.getBoolean("infinite")) {
            return infinite();
        }
        return finite(ECOAmount.readFromNBT(tag.getCompoundTag("capacity")), tag.getLong("bytesPerType"));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("infinite", this.infinite);
        if (!this.infinite) {
            tag.setTag("capacity", this.capacity.writeToNBT());
            tag.setLong("bytesPerType", this.bytesPerType);
        }
        return tag;
    }

    public ECOAmount limitInsert(ECOAmount used, ECOAmount requested) {
        if (requested == null || requested.isZero()) {
            return ECOAmount.ZERO;
        }
        if (this.infinite) {
            return requested;
        }
        ECOAmount remaining = this.getRemaining(used);
        return requested.min(remaining);
    }

    public ECOAmount getRemaining(ECOAmount used) {
        if (this.infinite) {
            return null;
        }
        ECOAmount normalizedUsed = used == null ? ECOAmount.ZERO : used;
        if (normalizedUsed.compareTo(this.capacity) >= 0) {
            return ECOAmount.ZERO;
        }
        return this.capacity.subtract(normalizedUsed);
    }

    public boolean canHold(ECOAmount used) {
        if (this.infinite) {
            return true;
        }
        ECOAmount normalizedUsed = used == null ? ECOAmount.ZERO : used;
        return normalizedUsed.compareTo(this.capacity) <= 0;
    }

    public boolean isInfinite() {
        return this.infinite;
    }

    public ECOAmount getCapacity() {
        return this.capacity;
    }

    public long getBytesPerType() {
        return this.bytesPerType;
    }

    public long getMaxTypes() {
        if (this.infinite || this.bytesPerType <= 0L) {
            return Long.MAX_VALUE;
        }
        return this.capacity.toBigInteger()
            .divide(BigInteger.valueOf(this.bytesPerType + 1L))
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }
}
