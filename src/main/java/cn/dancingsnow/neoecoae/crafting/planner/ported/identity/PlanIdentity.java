package cn.dancingsnow.neoecoae.crafting.planner.ported.identity;

import java.util.Map;
import java.util.Objects;

public final class PlanIdentity {

    public static PatternIdentity patternIdentityFor(Object value) {
        if (!(value instanceof appeng.api.networking.crafting.ICraftingPatternDetails)) return null;
        var pattern = (appeng.api.networking.crafting.ICraftingPatternDetails) value;
        if (pattern.getPattern() == null) return null;
        return new cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOGtnhExecutionCodec(null).identity(pattern);
    }

    public static boolean samePattern(appeng.api.networking.crafting.ICraftingPatternDetails first,
        appeng.api.networking.crafting.ICraftingPatternDetails second) {
        if (first == second) return true;
        PatternIdentity identity = patternIdentityFor(first);
        return identity != null && identity.equals(patternIdentityFor(second));
    }

    public static Map<PatternIdentity, Long> taskSignature(Map<?, Long> tasks) {
        Map<PatternIdentity, Long> result = new java.util.LinkedHashMap<>();
        for (var entry : tasks.entrySet()) {
            PatternIdentity identity = patternIdentityFor(entry.getKey());
            if (identity == null) return null;
            result.merge(identity, entry.getValue(), Math::addExact);
        }
        return result;
    }

    public enum Kind {
        DEFINITION,
        STRUCTURAL,
        OBJECT
    }

    /** Stable pattern identity when AE2 supplies an encoded definition. */
    public static final class PatternIdentity {

        private final Kind kind;

        public Kind kind() {
            return kind;
        }

        private final Object value;

        public Object value() {
            return value;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PatternIdentity)) return false;
            PatternIdentity other = (PatternIdentity) value;
            return java.util.Objects.equals(kind, other.kind) && java.util.Objects.equals(this.value, other.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(kind, value);
        }

        public PatternIdentity(Kind kind, Object value) {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(value, "value");

            this.kind = kind;
            this.value = value;
        }
    }

    public static final class Signature {

        private final Object finalWhat;

        public Object finalWhat() {
            return finalWhat;
        }

        private final long finalAmount;

        public long finalAmount() {
            return finalAmount;
        }

        private final Map<PatternIdentity, Long> patternTimes;

        public Map<PatternIdentity, Long> patternTimes() {
            return patternTimes;
        }

        private final Map<Object, Long> usedItems;

        public Map<Object, Long> usedItems() {
            return usedItems;
        }

        private final Map<Object, Long> emittedItems;

        public Map<Object, Long> emittedItems() {
            return emittedItems;
        }

        private final Map<Object, Long> missingItems;

        public Map<Object, Long> missingItems() {
            return missingItems;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Signature)) return false;
            Signature other = (Signature) value;
            return java.util.Objects.equals(finalWhat, other.finalWhat)
                && java.util.Objects.equals(finalAmount, other.finalAmount)
                && java.util.Objects.equals(patternTimes, other.patternTimes)
                && java.util.Objects.equals(usedItems, other.usedItems)
                && java.util.Objects.equals(emittedItems, other.emittedItems)
                && java.util.Objects.equals(missingItems, other.missingItems);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(finalWhat, finalAmount, patternTimes, usedItems, emittedItems, missingItems);
        }

        public Signature(Object finalWhat, long finalAmount, Map<PatternIdentity, Long> patternTimes,
            Map<Object, Long> usedItems, Map<Object, Long> emittedItems, Map<Object, Long> missingItems) {
            Objects.requireNonNull(finalWhat, "finalWhat");
            patternTimes = com.google.common.collect.ImmutableMap.copyOf(patternTimes);
            usedItems = com.google.common.collect.ImmutableMap.copyOf(usedItems);
            emittedItems = com.google.common.collect.ImmutableMap.copyOf(emittedItems);
            missingItems = com.google.common.collect.ImmutableMap.copyOf(missingItems);

            this.finalWhat = finalWhat;
            this.finalAmount = finalAmount;
            this.patternTimes = patternTimes;
            this.usedItems = usedItems;
            this.emittedItems = emittedItems;
            this.missingItems = missingItems;
        }

        public long executionCount() {
            long total = 0L;
            for (long value : patternTimes.values()) {
                if (value <= 0L) continue;
                if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
                total += value;
            }
            return total;
        }

        public boolean sameFinalOutput(Signature other) {
            return other != null && finalAmount == other.finalAmount && finalWhat.equals(other.finalWhat);
        }
    }

}
