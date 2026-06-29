package cn.dancingsnow.neoecoae.storage.core;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

public final class ECOCapacityPolicy {

    private static final ECOCapacityPolicy INFINITE = new ECOCapacityPolicy(true, null);

    private final boolean infinite;
    private final ECOAmount capacity;

    private ECOCapacityPolicy(boolean infinite, ECOAmount capacity) {
        this.infinite = infinite;
        this.capacity = capacity;
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
        if (capacity == null) {
            throw new IllegalArgumentException("Capacity must not be null");
        }
        return new ECOCapacityPolicy(false, capacity);
    }

    public static ECOCapacityPolicy readFromNBT(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags() || tag.getBoolean("infinite")) {
            return infinite();
        }
        return finite(ECOAmount.readFromNBT(tag.getCompoundTag("capacity")));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("infinite", this.infinite);
        if (!this.infinite) {
            tag.setTag("capacity", this.capacity.writeToNBT());
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
}
