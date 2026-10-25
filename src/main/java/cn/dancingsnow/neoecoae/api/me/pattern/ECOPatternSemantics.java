package cn.dancingsnow.neoecoae.api.me.pattern;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/**
 * Immutable description of one pattern firing. Amounts are exact, in AE2 key units, and independent of
 * ECO planner internals. Referenced AE2 pattern/input objects retain their original identity.
 */
@com.github.bsideup.jabel.Desugar
public record ECOPatternSemantics(IPatternDetails physicalPattern, @Nullable AEItemKey physicalDefinition,
    List<Input> consumedInputs, List<GenericStack> producedOutputs, List<GenericStack> returnedOutputs,
    List<FeedbackEdge> feedbackEdges, MatchingMode matchingMode, ExecutionRestriction executionRestriction,
    boolean exactStaticAnalysis, boolean cycleSafe, @Nullable String unsupportedReason) {

    public ECOPatternSemantics {
        Objects.requireNonNull(physicalPattern, "physicalPattern");
        consumedInputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(consumedInputs);
        producedOutputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(producedOutputs);
        returnedOutputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(returnedOutputs);
        feedbackEdges = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(feedbackEdges);
        for (GenericStack output : producedOutputs) validateOutput(output);
        for (GenericStack output : returnedOutputs) validateOutput(output);
        Objects.requireNonNull(matchingMode, "matchingMode");
        Objects.requireNonNull(executionRestriction, "executionRestriction");
    }

    public boolean supported() {
        return exactStaticAnalysis && unsupportedReason == null;
    }

    private static void validateOutput(GenericStack output) {
        if (output.what() == null || output.amount() <= 0) {
            throw new IllegalArgumentException("Output key and positive amount are required");
        }
    }

    public static ECOPatternSemantics unsupported(IPatternDetails pattern, @Nullable AEItemKey definition,
        String reason) {
        return new ECOPatternSemantics(
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
            Objects.requireNonNull(reason, "reason"));
    }

    @com.github.bsideup.jabel.Desugar
    public record Input(IPatternDetails.IInput source, AEKey key, BigInteger amountPerPattern,
        @Nullable AEKey returnedKey, BigInteger returnedAmountPerPattern) {

        public Input {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(key, "key");
            if (Objects.requireNonNull(amountPerPattern, "amountPerPattern")
                .signum() <= 0) {
                throw new IllegalArgumentException("Input amount must be positive");
            }
            if (Objects.requireNonNull(returnedAmountPerPattern, "returnedAmountPerPattern")
                .signum() < 0) {
                throw new IllegalArgumentException("Returned amount must be nonnegative");
            }
            if ((returnedKey == null) != (returnedAmountPerPattern.signum() == 0)) {
                throw new IllegalArgumentException("A returned key requires a positive returned amount and vice versa");
            }
        }
    }

    @com.github.bsideup.jabel.Desugar
    public record FeedbackEdge(AEKey returnedKey, AEKey dependentOutput, BigInteger amountPerFiring) {

        public FeedbackEdge {
            Objects.requireNonNull(returnedKey, "returnedKey");
            Objects.requireNonNull(dependentOutput, "dependentOutput");
            if (Objects.requireNonNull(amountPerFiring, "amountPerFiring")
                .signum() <= 0) {
                throw new IllegalArgumentException("Feedback amount must be positive");
            }
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
