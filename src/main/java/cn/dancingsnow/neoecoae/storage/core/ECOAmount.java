package cn.dancingsnow.neoecoae.storage.core;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

public final class ECOAmount implements Comparable<ECOAmount> {

    public static final ECOAmount ZERO = new ECOAmount(0L, null);

    private final long longValue;
    private final BigInteger bigValue;

    private ECOAmount(long longValue, BigInteger bigValue) {
        this.longValue = longValue;
        this.bigValue = bigValue;
    }

    public static ECOAmount of(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value == 0L) {
            return ZERO;
        }
        return new ECOAmount(value, null);
    }

    public static ECOAmount of(BigInteger value) {
        if (value == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value.signum() == 0) {
            return ZERO;
        }
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return of(value.longValue());
        }
        return new ECOAmount(0L, value);
    }

    public static ECOAmount readFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return ZERO;
        }
        if (tag.hasKey("big")) {
            return of(new BigInteger(tag.getString("big")));
        }
        return of(tag.getLong("long"));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (this.isBig()) {
            tag.setString("big", this.bigValue.toString());
        } else {
            tag.setLong("long", this.longValue);
        }
        return tag;
    }

    public ECOAmount add(ECOAmount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (this.isZero()) {
            return other;
        }
        if (!this.isBig() && !other.isBig()) {
            if (Long.MAX_VALUE - this.longValue >= other.longValue) {
                return of(this.longValue + other.longValue);
            }
        }
        return of(
            this.toBigInteger()
                .add(other.toBigInteger()));
    }

    public ECOAmount subtract(ECOAmount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (this.compareTo(other) < 0) {
            throw new IllegalArgumentException("Amount subtraction would become negative");
        }
        if (!this.isBig() && !other.isBig()) {
            return of(this.longValue - other.longValue);
        }
        return of(
            this.toBigInteger()
                .subtract(other.toBigInteger()));
    }

    public ECOAmount min(ECOAmount other) {
        if (other == null) {
            return this;
        }
        return this.compareTo(other) <= 0 ? this : other;
    }

    public long toLongSaturated() {
        if (this.isBig()) {
            return Long.MAX_VALUE;
        }
        return this.longValue;
    }

    public BigInteger toBigInteger() {
        if (this.isBig()) {
            return this.bigValue;
        }
        return BigInteger.valueOf(this.longValue);
    }

    public boolean isZero() {
        return !this.isBig() && this.longValue == 0L;
    }

    public boolean isBig() {
        return this.bigValue != null;
    }

    public int compare(ECOAmount other) {
        return this.compareTo(other);
    }

    @Override
    public int compareTo(ECOAmount other) {
        if (other == null) {
            return 1;
        }
        if (!this.isBig() && !other.isBig()) {
            if (this.longValue < other.longValue) {
                return -1;
            }
            if (this.longValue > other.longValue) {
                return 1;
            }
            return 0;
        }
        return this.toBigInteger()
            .compareTo(other.toBigInteger());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ECOAmount)) {
            return false;
        }
        ECOAmount other = (ECOAmount) obj;
        return this.compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return this.toBigInteger()
            .hashCode();
    }

    @Override
    public String toString() {
        if (this.isBig()) {
            return this.bigValue.toString();
        }
        return Long.toString(this.longValue);
    }
}
