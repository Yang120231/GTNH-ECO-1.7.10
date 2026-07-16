package cn.dancingsnow.neoecoae.crafting.runtime;

import java.math.BigInteger;

public final class ECOCraftingOutputAllocation {

    private ECOCraftingOutputAllocation() {}

    public static long[] proportional(long[] demands, long accepted) {
        long[] shares = new long[demands == null ? 0 : demands.length];
        if (demands == null || accepted <= 0L) {
            return shares;
        }
        long total = 0L;
        for (long demand : demands) {
            if (demand > 0L) {
                total = saturatingAdd(total, demand);
            }
        }
        long distributable = Math.min(total, accepted);
        if (distributable <= 0L) {
            return shares;
        }

        long allocated = 0L;
        BigInteger[] fractions = new BigInteger[demands.length];
        BigInteger divisor = BigInteger.valueOf(total);
        for (int i = 0; i < demands.length; i++) {
            long demand = Math.max(0L, demands[i]);
            if (total <= distributable) {
                shares[i] = demand;
                fractions[i] = BigInteger.ZERO;
            } else {
                BigInteger[] divided = BigInteger.valueOf(demand)
                    .multiply(BigInteger.valueOf(distributable))
                    .divideAndRemainder(divisor);
                shares[i] = divided[0].longValue();
                fractions[i] = divided[1];
            }
            allocated += shares[i];
        }
        long remainder = distributable - allocated;
        while (remainder > 0L) {
            int best = -1;
            for (int i = 0; i < demands.length; i++) {
                if (shares[i] < Math.max(0L, demands[i]) && (best < 0 || fractions[i].compareTo(fractions[best]) > 0)) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            shares[best]++;
            fractions[best] = BigInteger.valueOf(-1L);
            remainder--;
        }
        return shares;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
