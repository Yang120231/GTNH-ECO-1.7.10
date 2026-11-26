package cn.dancingsnow.neoecoae.crafting.execution;

/** Feedback for one original pattern at one live provider. A rate learned from singles is never a batch size. */
public final class ECOCraftingDispatchStrategy {

    private long tick = Long.MIN_VALUE;
    private int window = 1;
    private int accepted;
    private boolean paused;

    /**
     * The configured limit is a ceiling, not the initial window. The co-processor parameter is retained for callers.
     */
    public int beginTick(int coProcessors, int configuredLimit) {
        return Math.max(0, Math.min(window, configuredLimit));
    }

    /** Grow only after filling the entire window. Stock, power or time running out is not a refusal. */
    public void finishTick(int acceptedOrdinaryOperations) {
        if (!paused && acceptedOrdinaryOperations >= window) window = (int) Math.min(Integer.MAX_VALUE, 2L * window);
    }

    public void reset() {
        tick = Long.MIN_VALUE;
        window = 1;
        accepted = 0;
        paused = false;
    }

    boolean canSubmitSingle(long now) {
        if (tick != now) {
            finishTick(accepted);
            tick = now;
            accepted = 0;
            paused = false;
        }
        return !paused && accepted < window;
    }

    void acceptedSingle(boolean readyForMore) {
        if (readyForMore) accepted++;
        else paused = true; // Owned by AE2, but its outgoing buffer has not drained.
    }

    void rejectedSingle(boolean blocked) {
        if (!blocked) window = Math.max(1, accepted);
        paused = true; // Never repeat a negative probe for this pair in the same tick.
    }
}
