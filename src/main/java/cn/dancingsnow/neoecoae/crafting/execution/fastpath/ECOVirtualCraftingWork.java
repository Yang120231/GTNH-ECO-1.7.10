package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** A single physical FX lane carrying an entire 64-bit remaining task in virtual mode. */
@com.github.bsideup.jabel.Desugar
public record ECOVirtualCraftingWork(long craftCount, List<GenericStack> inputTotal, List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal, @Nullable UUID craftingJobId) {

    public ECOVirtualCraftingWork {
        if (craftCount <= 0L) {
            throw new IllegalArgumentException("virtual craftCount must be positive");
        }

        // Verified state models may return a partially worn tool when the last batch does not exhaust it.
        if (!ECOFastPathStacks.areValidItemStacks(
            inputTotal,
            Long.MAX_VALUE,
            false,
            ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT)
            || !ECOFastPathStacks
                .areValidItemStacks(outputTotal, Long.MAX_VALUE, true, ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            || !ECOFastPathStacks.areValidItemStacks(
                remainingTotal,
                Long.MAX_VALUE,
                false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH_MUTATION)) {
            throw new IllegalArgumentException("Virtual batch contains invalid item totals");
        }
    }
}
