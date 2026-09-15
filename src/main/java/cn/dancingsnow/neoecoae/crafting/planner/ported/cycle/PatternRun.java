package cn.dancingsnow.neoecoae.crafting.planner.ported.cycle;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;

/**
 * Compact execution metadata: fire {@code pattern} {@code count} times.
 *
 * <p>
 * This is the run-length form of {@link CycleFiring}. It exists because a single-transition cycle phase
 * has no interleaving to pick — the order of {@code N} firings of the only pattern in the phase is not a
 * choice — so a plan of a million firings is one entry, never a million witness steps.
 *
 * <p>
 * For a normal-sized witness this is its order-preserving run-length encoding. For a large batch search result it
 * can be the only materialized execution trace, retaining the exact order without allocating one object per firing.
 */
public final class PatternRun {

    private final CompiledPattern pattern;

    public CompiledPattern pattern() {
        return pattern;
    }

    private final long count;

    public long count() {
        return count;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof PatternRun)) return false;
        PatternRun other = (PatternRun) value;
        return java.util.Objects.equals(pattern, other.pattern) && java.util.Objects.equals(count, other.count);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pattern, count);
    }

    public PatternRun(CompiledPattern pattern, long count) {
        if (pattern == null) throw new IllegalArgumentException("A pattern run must name a pattern");
        if (count < 0) throw new IllegalArgumentException("A pattern run count must not be negative");

        this.pattern = pattern;
        this.count = count;
    }

    public Object details() {
        return pattern.details();
    }
}
