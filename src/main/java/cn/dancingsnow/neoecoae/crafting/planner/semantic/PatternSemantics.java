package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/**
 * Normalized static semantics consumed by the graph compiler. It intentionally keeps contract facts separate from
 * the raw {@link IPatternDetails} implementation so SCC detection does not need integration-specific instanceof
 * checks.
 */
@com.github.bsideup.jabel.Desugar
public record PatternSemantics(IPatternDetails physicalPattern, @Nullable Object physicalDefinition,
    List<Input> consumedInputs, List<GenericStack> producedOutputs, List<GenericStack> returnedOutputs,
    List<FeedbackEdge> feedbackEdges, MatchingMode matchingMode, ExecutionRestriction executionRestriction,
    boolean exactStaticAnalysis, boolean cycleSafe, @Nullable String unsupportedReason) {

    public PatternSemantics {
        Objects.requireNonNull(physicalPattern, "physicalPattern");
        consumedInputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(consumedInputs);
        producedOutputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(producedOutputs);
        returnedOutputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(returnedOutputs);
        feedbackEdges = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(feedbackEdges);
        Objects.requireNonNull(matchingMode, "matchingMode");
        Objects.requireNonNull(executionRestriction, "executionRestriction");
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

    public Set<AEKey> consumedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (Input input : consumedInputs) if (input.key() != null) keys.add(input.key());
        return keys;
    }

    public Set<AEKey> producedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (GenericStack output : producedOutputs)
            if (output != null && output.what() != null) keys.add(output.what());
        return keys;
    }

    public Set<AEKey> returnedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (GenericStack output : returnedOutputs)
            if (output != null && output.what() != null) keys.add(output.what());
        return keys;
    }

    public static PatternSemantics unsupported(IPatternDetails pattern, @Nullable Object definition, String reason) {
        return new PatternSemantics(
            pattern,
            definition,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            MatchingMode.UNKNOWN,
            ExecutionRestriction.UNKNOWN,
            false,
            false,
            reason);
    }

    @com.github.bsideup.jabel.Desugar
    public record Input(IPatternDetails.IInput source, AEKey key, PlannerAmount amountPerPattern,
        @Nullable AEKey returnedKey, PlannerAmount returnedAmountPerPattern) {

        public Input {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(amountPerPattern, "amountPerPattern");
            Objects.requireNonNull(returnedAmountPerPattern, "returnedAmountPerPattern");
        }
    }

    /** A returned/reusable key creates a dependency from that key back to the dependent produced key. */
    @com.github.bsideup.jabel.Desugar
    public record FeedbackEdge(AEKey returnedKey, AEKey dependentOutput, PlannerAmount amountPerFiring) {

        public FeedbackEdge {
            Objects.requireNonNull(returnedKey, "returnedKey");
            Objects.requireNonNull(dependentOutput, "dependentOutput");
            Objects.requireNonNull(amountPerFiring, "amountPerFiring");
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
