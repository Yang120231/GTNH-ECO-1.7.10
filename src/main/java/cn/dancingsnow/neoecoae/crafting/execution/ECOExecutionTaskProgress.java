package cn.dancingsnow.neoecoae.crafting.execution;

public final class ECOExecutionTaskProgress {

    public long value;
    private java.math.BigInteger exactTotal;
    private java.math.BigInteger exactRemaining;

    void setExact(java.math.BigInteger total, java.math.BigInteger remaining) {
        if (remaining.signum() < 0 || total.compareTo(remaining) < 0)
            throw new IllegalArgumentException("Invalid exact task progress");
        exactTotal = total;
        exactRemaining = remaining;
        value = cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(remaining);
    }

    java.math.BigInteger remainingExact() {
        return exactRemaining == null ? java.math.BigInteger.valueOf(value) : exactRemaining;
    }

    boolean isExact() {
        return exactTotal != null;
    }

    long completedBounded(long total) {
        return exactTotal == null ? Math.max(0L, total - value)
            : cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan
                .bounded(exactTotal.subtract(exactRemaining));
    }

    void accept(long count) {
        if (count < 0 || count > value) throw new IllegalArgumentException("Dispatch exceeds remaining task");
        if (exactRemaining == null) value -= count;
        else setExact(exactTotal, exactRemaining.subtract(java.math.BigInteger.valueOf(count)));
    }

    void accept(java.math.BigInteger count) {
        if (count.signum() < 0 || count.compareTo(remainingExact()) > 0)
            throw new IllegalArgumentException("Dispatch exceeds remaining task");
        if (exactRemaining == null) accept(count.longValueExact());
        else setExact(exactTotal, exactRemaining.subtract(count));
    }
}
