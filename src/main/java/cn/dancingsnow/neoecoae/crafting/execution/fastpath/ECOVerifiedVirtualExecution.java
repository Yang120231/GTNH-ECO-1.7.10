package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** Verified recipe credential bound to one unlimited-lane, 64-bit virtual batch. */
@com.github.bsideup.jabel.Desugar
public record ECOVerifiedVirtualExecution(ECOVerifiedFastPathRecipe recipe, long craftCount,
    @Nullable UUID craftingJobId, List<GenericStack> inputTotal, List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal) {

    public ECOVerifiedVirtualExecution {
        if (craftCount <= 0L) {
            throw new IllegalArgumentException("virtual craftCount must be positive");
        }

    }

    public boolean isCurrent(long reloadGeneration) {
        return recipe.isCurrent(reloadGeneration);
    }
}
