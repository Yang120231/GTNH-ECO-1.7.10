package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.List;

/** Structural eligibility shared by every concrete resolution of one encoded pattern. */
@com.github.bsideup.jabel.Desugar
public record ECOPatternEligibility(boolean supported, List<InputType> inputTypes, String rejectReason) {

    public enum InputType {
        EXACT,
        TAG_OR_SUBSTITUTION
    }

    public ECOPatternEligibility {

        rejectReason = rejectReason == null ? "" : rejectReason;
    }

}
