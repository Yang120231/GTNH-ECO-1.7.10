package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.List;
import java.util.Objects;

/** One slot's compressed, ordered concrete inputs. Counts are crafts, amounts are per craft. */
public final class PlannedInputAllocation {

    private final int slot;

    public int slot() {
        return slot;
    }

    private final List<Run> runs;

    public List<Run> runs() {
        return runs;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof PlannedInputAllocation)) return false;
        PlannedInputAllocation other = (PlannedInputAllocation) value;
        return java.util.Objects.equals(slot, other.slot) && java.util.Objects.equals(runs, other.runs);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(slot, runs);
    }

    public PlannedInputAllocation(int slot, List<Run> runs) {
        if (slot < 0) throw new IllegalArgumentException("Negative input slot");
        runs = com.google.common.collect.ImmutableList.copyOf(runs);
        if (runs.isEmpty()) throw new IllegalArgumentException("Empty allocation");

        this.slot = slot;
        this.runs = runs;
    }

    public static final class Run {

        private final Object key;

        public Object key() {
            return key;
        }

        private final long amount;

        public long amount() {
            return amount;
        }

        private final long crafts;

        public long crafts() {
            return crafts;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Run)) return false;
            Run other = (Run) value;
            return java.util.Objects.equals(key, other.key) && java.util.Objects.equals(amount, other.amount)
                && java.util.Objects.equals(crafts, other.crafts);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(key, amount, crafts);
        }

        public Run(Object key, long amount, long crafts) {
            Objects.requireNonNull(key);
            if (amount <= 0 || crafts <= 0) throw new IllegalArgumentException("Invalid input run");

            this.key = key;
            this.amount = amount;
            this.crafts = crafts;
        }
    }

    public long totalCrafts() {
        long total = 0;
        for (var run : runs) total = Math.addExact(total, run.crafts());
        return total;
    }

    public Run at(long completed) {
        if (completed < 0) throw new IllegalArgumentException("Negative progress");
        for (var run : runs) {
            if (completed < run.crafts()) return new Run(run.key(), run.amount(), run.crafts() - completed);
            completed -= run.crafts();
        }
        throw new IllegalStateException("Input allocation exhausted");
    }
}
