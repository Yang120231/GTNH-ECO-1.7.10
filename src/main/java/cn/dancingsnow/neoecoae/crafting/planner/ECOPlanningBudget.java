package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.function.LongSupplier;

import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;

/** One calculation's shared allowance: recipe discovery, alternate routes, seeds and cycle search. */
public final class ECOPlanningBudget implements ECOCancellation {

    private static final long OPTIMIZATION_MAX_NANOS = 50_000_000L;
    private static final long OPTIMIZATION_TOTAL_NANOS = 200_000_000L;
    private long optimizationDeadline = Long.MAX_VALUE;

    private final ECOCancellation cancellation;
    private final long maxWork;
    private final long maxNanos;
    private final LongSupplier clock;
    private final long started;
    private long work;

    public ECOPlanningBudget(ECOCancellation cancellation) {
        this(
            cancellation,
            Math.max(1L, PlanningSettings.Crafting.ecoPlanningMaxWork),
            Math.max(1L, PlanningSettings.Crafting.ecoPlanningMaxMillis) * 1_000_000L,
            System::nanoTime);
    }

    public ECOPlanningBudget(ECOCancellation cancellation, long maxWork, long maxNanos, LongSupplier clock) {
        if (maxWork < 1 || maxNanos < 1) throw new IllegalArgumentException("Planning budgets must be positive");
        this.cancellation = cancellation;
        this.maxWork = maxWork;
        this.maxNanos = maxNanos;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    @Override
    public boolean improveCompletedPlans() {
        return cancellation.improveCompletedPlans();
    }

    @Override
    public synchronized void checkpoint() throws InterruptedException {
        cancellation.checkpoint();
        long elapsed = clock.getAsLong() - started;
        if (elapsed >= optimizationDeadline)
            throw new Exhausted("Optional route optimization window exhausted; retaining the validated plan");
        if (++work > maxWork || elapsed >= maxNanos) {
            throw new Exhausted(
                "Shared planning allowance exhausted after " + work
                    + " checkpoints; reason="
                    + (work > maxWork ? "WORK_LIMIT" : "TIME_LIMIT")
                    + " elapsedMs="
                    + elapsed / 1_000_000L
                    + " maxMillis="
                    + maxNanos / 1_000_000L
                    + " maxWork="
                    + maxWork
                    + "; result remains unknown");
        }
    }

    /** Starts only after material closure and execution validation succeeded. */
    public synchronized void beginOptimization() {
        if (optimizationDeadline == Long.MAX_VALUE) optimizationDeadline = Math
            .min(OPTIMIZATION_TOTAL_NANOS, clock.getAsLong() - started + OPTIMIZATION_MAX_NANOS);
    }

    /**
     * Nested allowance for optional searches: never more than half of what remains, so exhausting it leaves this budget
     * usable.
     */
    public synchronized ECOPlanningBudget slice(long maxSliceWork, long maxSliceNanos) {
        long remainingWork = Math.max(0L, maxWork - work);
        long remainingNanos = Math.max(0L, maxNanos - (clock.getAsLong() - started));
        return new ECOPlanningBudget(
            this,
            Math.max(1L, Math.min(maxSliceWork, remainingWork / 2)),
            Math.max(1L, Math.min(maxSliceNanos, remainingNanos / 2)),
            clock);
    }

    /** Distinct from cancellation, internal errors and a proof of missing materials. */
    public static final class Exhausted extends RuntimeException {

        private Exhausted(String message) {
            super(message, null, false, false);
        }
    }
}
