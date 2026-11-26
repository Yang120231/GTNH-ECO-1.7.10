package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;

/** One CPU's soft deadline and submission ceilings, shared by every dispatch pass in the server tick. */
final class ECOCraftingDispatchBudget {

    private long tick = Long.MIN_VALUE;
    private long deadline;
    private int ordinaryRemaining;
    private int scaledRemaining;
    private int passRemaining;
    private boolean attempted;

    void beginTick(long currentTick) {
        if (tick == currentTick) return;
        tick = currentTick;
        attempted = false;
        deadline = System.nanoTime() + Math.max(1L, PlanningSettings.Crafting.ecoCpuTickTimeBudgetMs) * 1_000_000L;
        ordinaryRemaining = scaledRemaining = Math.max(0, PlanningSettings.Crafting.ecoCpuPushTickLimit);
    }

    long tick() {
        return tick;
    }

    boolean hasTime() {
        return System.nanoTime() - deadline < 0;
    }

    /** Let a costly first lookup make progress; after any attempt, yield before starting another transaction. */
    boolean canStartSubmission() {
        return !attempted || hasTime();
    }

    void recordProviderAttempt() {
        attempted = true;
    }

    void beginPass(int maxPatterns) {
        passRemaining = Math.min(ordinaryRemaining, Math.max(0, maxPatterns));
    }

    int remainingOrdinary() {
        return ordinaryRemaining;
    }

    boolean canAttemptOrdinary() {
        return passRemaining > 0 && ordinaryRemaining > 0;
    }

    void recordNormalProbe() {
        ordinaryRemaining--;
        passRemaining--;
    }

    int remainingScaled() {
        return scaledRemaining;
    }

    void recordScaledProbe() {
        scaledRemaining--;
    }
}
