package cn.dancingsnow.neoecoae.crafting.planner.ported.semantic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

/**
 * Normalized static semantics consumed by the graph compiler. It intentionally keeps contract facts separate from
 * the raw {@link ICraftingPatternDetails} implementation so SCC detection does not need integration-specific instanceof
 * checks.
 */
public final class PatternSemantics {

    private final ICraftingPatternDetails physicalPattern;

    public ICraftingPatternDetails physicalPattern() {
        return physicalPattern;
    }

    private final Object physicalDefinition;

    public Object physicalDefinition() {
        return physicalDefinition;
    }

    private final List<Input> consumedInputs;

    public List<Input> consumedInputs() {
        return consumedInputs;
    }

    private final List<GenericStack> producedOutputs;

    public List<GenericStack> producedOutputs() {
        return producedOutputs;
    }

    private final List<GenericStack> returnedOutputs;

    public List<GenericStack> returnedOutputs() {
        return returnedOutputs;
    }

    private final List<FeedbackEdge> feedbackEdges;

    public List<FeedbackEdge> feedbackEdges() {
        return feedbackEdges;
    }

    private final MatchingMode matchingMode;

    public MatchingMode matchingMode() {
        return matchingMode;
    }

    private final ExecutionRestriction executionRestriction;

    public ExecutionRestriction executionRestriction() {
        return executionRestriction;
    }

    private final boolean exactStaticAnalysis;

    public boolean exactStaticAnalysis() {
        return exactStaticAnalysis;
    }

    private final boolean cycleSafe;

    public boolean cycleSafe() {
        return cycleSafe;
    }

    private final String unsupportedReason;

    public String unsupportedReason() {
        return unsupportedReason;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof PatternSemantics)) return false;
        PatternSemantics other = (PatternSemantics) value;
        return java.util.Objects.equals(physicalPattern, other.physicalPattern)
            && java.util.Objects.equals(physicalDefinition, other.physicalDefinition)
            && java.util.Objects.equals(consumedInputs, other.consumedInputs)
            && java.util.Objects.equals(producedOutputs, other.producedOutputs)
            && java.util.Objects.equals(returnedOutputs, other.returnedOutputs)
            && java.util.Objects.equals(feedbackEdges, other.feedbackEdges)
            && java.util.Objects.equals(matchingMode, other.matchingMode)
            && java.util.Objects.equals(executionRestriction, other.executionRestriction)
            && java.util.Objects.equals(exactStaticAnalysis, other.exactStaticAnalysis)
            && java.util.Objects.equals(cycleSafe, other.cycleSafe)
            && java.util.Objects.equals(unsupportedReason, other.unsupportedReason);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            physicalPattern,
            physicalDefinition,
            consumedInputs,
            producedOutputs,
            returnedOutputs,
            feedbackEdges,
            matchingMode,
            executionRestriction,
            exactStaticAnalysis,
            cycleSafe,
            unsupportedReason);
    }

    public PatternSemantics(ICraftingPatternDetails physicalPattern, Object physicalDefinition,
        List<Input> consumedInputs, List<GenericStack> producedOutputs, List<GenericStack> returnedOutputs,
        List<FeedbackEdge> feedbackEdges, MatchingMode matchingMode, ExecutionRestriction executionRestriction,
        boolean exactStaticAnalysis, boolean cycleSafe, String unsupportedReason) {
        Objects.requireNonNull(physicalPattern, "physicalPattern");
        consumedInputs = com.google.common.collect.ImmutableList.copyOf(consumedInputs);
        producedOutputs = com.google.common.collect.ImmutableList.copyOf(producedOutputs);
        returnedOutputs = com.google.common.collect.ImmutableList.copyOf(returnedOutputs);
        feedbackEdges = com.google.common.collect.ImmutableList.copyOf(feedbackEdges);
        Objects.requireNonNull(matchingMode, "matchingMode");
        Objects.requireNonNull(executionRestriction, "executionRestriction");

        this.physicalPattern = physicalPattern;
        this.physicalDefinition = physicalDefinition;
        this.consumedInputs = consumedInputs;
        this.producedOutputs = producedOutputs;
        this.returnedOutputs = returnedOutputs;
        this.feedbackEdges = feedbackEdges;
        this.matchingMode = matchingMode;
        this.executionRestriction = executionRestriction;
        this.exactStaticAnalysis = exactStaticAnalysis;
        this.cycleSafe = cycleSafe;
        this.unsupportedReason = unsupportedReason;
    }

    public boolean supported() {
        return exactStaticAnalysis && unsupportedReason == null;
    }

    /** Unknown matching/restriction semantics can never be approximated into a SUCCESS result. */
    public boolean completeForStaticPlanning() {
        return supported() && matchingMode == MatchingMode.EXACT && executionRestriction == ExecutionRestriction.NONE;
    }

    public boolean cycleSafeForStaticPlanning() {
        // A normalized AE2 substitution slot is safe here when the adapter has committed the plan to one concrete
        // member and independently proved its reusable-stock contract. Fuzzy/unknown matching remains forbidden.
        return supported() && matchingMode != MatchingMode.FUZZY
            && matchingMode != MatchingMode.UNKNOWN
            && executionRestriction == ExecutionRestriction.NONE
            && cycleSafe;
    }

    public Set<Object> consumedKeys() {
        Set<Object> keys = new LinkedHashSet<>();
        for (Input input : consumedInputs) if (input.key() != null) keys.add(input.key());
        return com.google.common.collect.ImmutableSet.copyOf(keys);
    }

    public Set<Object> producedKeys() {
        Set<Object> keys = new LinkedHashSet<>();
        for (GenericStack output : producedOutputs)
            if (output != null && output.what() != null) keys.add(output.what());
        return com.google.common.collect.ImmutableSet.copyOf(keys);
    }

    public Set<Object> returnedKeys() {
        Set<Object> keys = new LinkedHashSet<>();
        for (GenericStack output : returnedOutputs)
            if (output != null && output.what() != null) keys.add(output.what());
        return com.google.common.collect.ImmutableSet.copyOf(keys);
    }

    public static PatternSemantics unsupported(ICraftingPatternDetails pattern, Object definition, String reason) {
        return new PatternSemantics(
            pattern,
            definition,
            com.google.common.collect.ImmutableList.of(),
            com.google.common.collect.ImmutableList.of(),
            com.google.common.collect.ImmutableList.of(),
            com.google.common.collect.ImmutableList.of(),
            MatchingMode.UNKNOWN,
            ExecutionRestriction.UNKNOWN,
            false,
            false,
            reason);
    }

    public static final class Input {

        private final Object source;

        public Object source() {
            return source;
        }

        private final Object key;

        public Object key() {
            return key;
        }

        private final PlannerAmount amountPerPattern;

        public PlannerAmount amountPerPattern() {
            return amountPerPattern;
        }

        private final Object returnedKey;

        public Object returnedKey() {
            return returnedKey;
        }

        private final PlannerAmount returnedAmountPerPattern;

        public PlannerAmount returnedAmountPerPattern() {
            return returnedAmountPerPattern;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Input)) return false;
            Input other = (Input) value;
            return java.util.Objects.equals(source, other.source) && java.util.Objects.equals(key, other.key)
                && java.util.Objects.equals(amountPerPattern, other.amountPerPattern)
                && java.util.Objects.equals(returnedKey, other.returnedKey)
                && java.util.Objects.equals(returnedAmountPerPattern, other.returnedAmountPerPattern);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(source, key, amountPerPattern, returnedKey, returnedAmountPerPattern);
        }

        public Input(Object source, Object key, PlannerAmount amountPerPattern, Object returnedKey,
            PlannerAmount returnedAmountPerPattern) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(amountPerPattern, "amountPerPattern");
            Objects.requireNonNull(returnedAmountPerPattern, "returnedAmountPerPattern");

            this.source = source;
            this.key = key;
            this.amountPerPattern = amountPerPattern;
            this.returnedKey = returnedKey;
            this.returnedAmountPerPattern = returnedAmountPerPattern;
        }
    }

    /** A returned/reusable key creates a dependency from that key back to the dependent produced key. */
    public static final class FeedbackEdge {

        private final Object returnedKey;

        public Object returnedKey() {
            return returnedKey;
        }

        private final Object dependentOutput;

        public Object dependentOutput() {
            return dependentOutput;
        }

        private final PlannerAmount amountPerFiring;

        public PlannerAmount amountPerFiring() {
            return amountPerFiring;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof FeedbackEdge)) return false;
            FeedbackEdge other = (FeedbackEdge) value;
            return java.util.Objects.equals(returnedKey, other.returnedKey)
                && java.util.Objects.equals(dependentOutput, other.dependentOutput)
                && java.util.Objects.equals(amountPerFiring, other.amountPerFiring);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(returnedKey, dependentOutput, amountPerFiring);
        }

        public FeedbackEdge(Object returnedKey, Object dependentOutput, PlannerAmount amountPerFiring) {
            Objects.requireNonNull(returnedKey, "returnedKey");
            Objects.requireNonNull(dependentOutput, "dependentOutput");
            Objects.requireNonNull(amountPerFiring, "amountPerFiring");

            this.returnedKey = returnedKey;
            this.dependentOutput = dependentOutput;
            this.amountPerFiring = amountPerFiring;
        }
    }

    public enum MatchingMode {
        EXACT,
        SUBSTITUTION,
        FUZZY,
        UNKNOWN
    }

    public enum ExecutionRestriction {
        NONE,
        PROVIDER_LOOKUP,
        CPU,
        FIRING_EXPANSION,
        UNKNOWN
    }
}
