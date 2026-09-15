package cn.dancingsnow.neoecoae.crafting.planner.ported.cycle;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;

/**
 * One step of an execution witness: fire {@code pattern} exactly once at position {@code step}.
 *
 * <p>
 * Single-firing granularity is deliberate. Run-length compression would pick one particular
 * interleaving, and the witness has to stay replayable step by step for the non-negativity check.
 */
public final class CycleFiring {

    private final int step;

    public int step() {
        return step;
    }

    private final CompiledPattern pattern;

    public CompiledPattern pattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CycleFiring)) return false;
        CycleFiring other = (CycleFiring) value;
        return java.util.Objects.equals(step, other.step) && java.util.Objects.equals(pattern, other.pattern);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(step, pattern);
    }

    public CycleFiring(int step, CompiledPattern pattern) {
        if (step < 0) throw new IllegalArgumentException("Witness step must not be negative");
        if (pattern == null) throw new IllegalArgumentException("Witness step must name a pattern");

        this.step = step;
        this.pattern = pattern;
    }
}
