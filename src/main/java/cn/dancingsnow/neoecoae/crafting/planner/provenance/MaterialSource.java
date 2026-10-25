package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import java.util.Objects;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;

/** One origin of material consumed by the numeric plan. */
public interface MaterialSource {

    @com.github.bsideup.jabel.Desugar
    record Stock() implements MaterialSource {

        public static final Stock INSTANCE = new Stock();
    }

    @com.github.bsideup.jabel.Desugar
    record Emitted() implements MaterialSource {

        public static final Emitted INSTANCE = new Emitted();
    }

    @com.github.bsideup.jabel.Desugar
    record PatternOutput(IPatternDetails pattern, boolean primary) implements MaterialSource {

        public PatternOutput {
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    @com.github.bsideup.jabel.Desugar
    record CycleOutput(int componentId) implements MaterialSource {}
}
