package cn.dancingsnow.neoecoae.crafting.planner.compile;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

@com.github.bsideup.jabel.Desugar
public record CompiledInput(IPatternDetails.IInput source, AEKey key, PlannerAmount amountPerPattern,
    boolean fastSupported, String unsupportedReason, AEKey remainderKey, PlannerAmount remainderAmountPerPattern,
    boolean ignoresComponents) {

    public CompiledInput(IPatternDetails.IInput source, AEKey key, PlannerAmount amountPerPattern,
        boolean fastSupported, String unsupportedReason, AEKey remainderKey, PlannerAmount remainderAmountPerPattern) {
        this(
            source,
            key,
            amountPerPattern,
            fastSupported,
            unsupportedReason,
            remainderKey,
            remainderAmountPerPattern,
            false);
    }

    /** Exact one-item, same-key return that can be held as working stock across a batch. */
    public boolean reusable() {
        return remainderKey != null && remainderKey.equals(key)
            && amountPerPattern.fitsLong()
            && remainderAmountPerPattern.fitsLong()
            && amountPerPattern.longValueExact() == 1L
            && remainderAmountPerPattern.longValueExact() == 1L;
    }

    public CompiledInput(IPatternDetails.IInput source, AEKey key, long amountPerPattern, boolean fastSupported,
        String unsupportedReason) {
        this(
            source,
            key,
            PlannerAmount.of(amountPerPattern),
            fastSupported,
            unsupportedReason,
            null,
            PlannerAmount.ZERO,
            false);
    }

    public CompiledInput(IPatternDetails.IInput source, AEKey key, long amountPerPattern, boolean fastSupported,
        String unsupportedReason, AEKey remainderKey, long remainderAmountPerPattern) {
        this(
            source,
            key,
            PlannerAmount.of(amountPerPattern),
            fastSupported,
            unsupportedReason,
            remainderKey,
            PlannerAmount.of(remainderAmountPerPattern),
            false);
    }

    public CompiledInput(IPatternDetails.IInput source, AEKey key, PlannerAmount amountPerPattern,
        boolean fastSupported, String unsupportedReason) {
        this(source, key, amountPerPattern, fastSupported, unsupportedReason, null, PlannerAmount.ZERO);
    }
}
