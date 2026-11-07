package cn.dancingsnow.neoecoae.crafting.display.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;

class ExactAmountFormatterTest {

    @Test
    void displaysNhFluidLitersWithoutChangingOperationalBucketUnits() {
        var key = AEFluidKey.of(TestFluids.WATER);
        var amount = ExactAmount.finite(BigInteger.valueOf(1_000_000));
        assertEquals("1M", ExactAmountFormatter.slot(key, amount));
        assertEquals("1,000,000", ExactAmountFormatter.full(key, amount));
        assertEquals("1", ExactAmountFormatter.slot(key, ExactAmount.finite(BigInteger.ONE)));
        assertEquals(1000, key.getAmountPerUnit());
    }

    @Test
    void formatsBeyondLongWithoutNarrowing() {
        assertEquals("12.3E", ExactAmountFormatter.slot(ExactAmount.finite(new BigInteger("12345678901234567890"))));
    }

    @Test
    void limitsCompactDisplayToThreeSignificantDigits() {
        assertEquals("1.23K", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(1234))));
        assertEquals("12.3K", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(12345))));
        assertEquals("123K", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(123456))));
    }

    @Test
    void formatsUnboundedSeparatelyFromFiniteBigInteger() {
        assertEquals("∞", ExactAmountFormatter.slot(ExactAmount.unbounded()));
    }

    @Test
    void usesScientificNotationAfterTwoQ() {
        int[] exponents = { 30, 33, 36, 57, 60 };
        String[] units = { "Q", "KQ", "MQ", "RQ", "QQ" };
        for (int i = 0; i < exponents.length; i++) {
            BigInteger unit = BigInteger.TEN.pow(exponents[i]);
            assertEquals("1" + units[i], ExactAmountFormatter.slot(ExactAmount.finite(unit)));
            String expected = exponents[i] == 60 ? "1.23×10^61" : "12.3" + units[i];
            assertEquals(
                expected,
                ExactAmountFormatter.slot(
                    ExactAmount.finite(
                        unit.multiply(BigInteger.valueOf(12345))
                            .divide(BigInteger.valueOf(1000)))));
        }
        assertEquals(
            "999Q",
            ExactAmountFormatter.slot(
                ExactAmount.finite(
                    BigInteger.TEN.pow(33)
                        .subtract(BigInteger.ONE))));
        assertEquals("1×10^63", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.TEN.pow(63))));
        assertEquals(
            "1.23×10^63",
            ExactAmountFormatter.slot(
                ExactAmount.finite(
                    BigInteger.TEN.pow(63)
                        .multiply(BigInteger.valueOf(12345))
                        .divide(BigInteger.valueOf(10000)))));
        assertEquals("1×10^63", ExactAmountFormatter.full(ExactAmount.finite(BigInteger.TEN.pow(63))));
    }
}
