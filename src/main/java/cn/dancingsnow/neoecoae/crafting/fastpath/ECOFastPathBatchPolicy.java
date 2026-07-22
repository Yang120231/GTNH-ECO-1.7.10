package cn.dancingsnow.neoecoae.crafting.fastpath;

/** Pure batch-size rules shared by the planner and CPU dispatch path. */
public final class ECOFastPathBatchPolicy {

    private ECOFastPathBatchPolicy() {}

    public static int normalizeRequested(int requested, boolean processingMatrix) {
        int bounded = Math.min(ECOFastPathConfig.MAX_BATCH_SIZE, Math.max(0, requested));
        return processingMatrix ? highestPowerOfTwoAtMost(bounded) : bounded;
    }

    static int highestPowerOfTwoAtMost(int requested) {
        int bounded = Math.max(0, requested);
        if (bounded <= 1) {
            return bounded;
        }
        return Integer.highestOneBit(bounded);
    }
}
