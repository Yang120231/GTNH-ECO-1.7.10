package cn.dancingsnow.neoecoae.crafting.planner.ported.provenance;

import java.util.Objects;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** One origin of material consumed by the numeric plan. */
public interface MaterialSource {

    public final class Stock implements MaterialSource {

        public Stock() {

        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Stock)) return false;
            Stock other = (Stock) value;
            return true;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash();
        }

        public static final Stock INSTANCE = new Stock();
    }

    public final class Emitted implements MaterialSource {

        public Emitted() {

        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Emitted)) return false;
            Emitted other = (Emitted) value;
            return true;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash();
        }

        public static final Emitted INSTANCE = new Emitted();
    }

    public final class PatternOutput implements MaterialSource {

        private final ICraftingPatternDetails pattern;

        public ICraftingPatternDetails pattern() {
            return pattern;
        }

        private final boolean primary;

        public boolean primary() {
            return primary;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PatternOutput)) return false;
            PatternOutput other = (PatternOutput) value;
            return java.util.Objects.equals(this.pattern, other.pattern)
                && java.util.Objects.equals(this.primary, other.primary);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pattern, primary);
        }

        public PatternOutput(ICraftingPatternDetails pattern, boolean primary) {
            Objects.requireNonNull(pattern, "pattern");

            this.pattern = pattern;
            this.primary = primary;
        }
    }

    public final class CycleOutput implements MaterialSource {

        private final int componentId;

        public int componentId() {
            return componentId;
        }

        public CycleOutput(int componentId) {
            this.componentId = componentId;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof CycleOutput)) return false;
            CycleOutput other = (CycleOutput) value;
            return java.util.Objects.equals(this.componentId, other.componentId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(componentId);
        }
    }
}
