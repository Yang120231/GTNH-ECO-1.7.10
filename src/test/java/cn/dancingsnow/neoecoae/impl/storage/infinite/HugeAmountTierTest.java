package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class HugeAmountTierTest {

    @Test
    void exactArithmeticCrossesEveryTierInBothDirections() {
        var random = new Random(126);
        for (int run = 0; run < 20_000; run++) {
            BigInteger left = new BigInteger(random.nextInt(160), random);
            BigInteger right = new BigInteger(random.nextInt(160), random);
            var a = HugeAmount.of(left);
            var b = HugeAmount.of(right);
            assertEquals(
                left.add(right),
                a.add(b)
                    .toBigInteger());
            if (left.compareTo(right) >= 0) assertEquals(
                left.subtract(right),
                a.subtract(b)
                    .toBigInteger());
            else assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
            assertEquals(Integer.signum(left.compareTo(right)), Integer.signum(a.compareTo(b)));
            assertEquals(
                left.min(BigInteger.valueOf(Long.MAX_VALUE))
                    .longValueExact(),
                a.toLongSaturated());
            long small = random.nextLong() & Long.MAX_VALUE;
            assertEquals(
                left.add(BigInteger.valueOf(small)),
                a.add(small)
                    .toBigInteger());
            if (left.compareTo(BigInteger.valueOf(small)) >= 0) assertEquals(
                left.subtract(BigInteger.valueOf(small)),
                a.subtract(small)
                    .toBigInteger());
            assertEquals(
                left,
                HugeAmount.read(a.write())
                    .toBigInteger());
        }
    }

    @Test
    void oldLongAndDecimalTagsKeepTheirFormatAtTheNewTierBoundaries() {
        for (BigInteger value : new BigInteger[] { BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE),
            BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(126)
                .subtract(BigInteger.ONE),
            BigInteger.ONE.shiftLeft(126), BigInteger.ONE.shiftLeft(200) }) {
            var old = new CompoundTag();
            if (value.bitLength() < 64) old.putLong("long", value.longValueExact());
            else old.putString("big", value.toString());
            var amount = HugeAmount.read(old);
            assertEquals(value, amount.toBigInteger());
            assertEquals(old, amount.write());
            assertEquals(value.hashCode(), amount.hashCode());
            assertEquals(HugeAmount.ZERO, amount.subtract(amount));
        }
        assertEquals(
            HugeAmount.of(Long.MAX_VALUE),
            HugeAmount.of(BigInteger.ONE.shiftLeft(63))
                .subtract(1));
    }
}
