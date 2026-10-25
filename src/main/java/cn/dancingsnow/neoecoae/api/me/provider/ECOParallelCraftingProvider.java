package cn.dancingsnow.neoecoae.api.me.provider;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Optional capacity contract for providers that can safely accept parallel ordinary crafts. */
public interface ECOParallelCraftingProvider {

    int eco$getAvailableParallelSlots();

    /**
     * Accepts one atomic ordinary-path batch. The counters contain the complete input total for
     * {@code craftCount} crafts, not one copy of the inputs.
     *
     * <p>
     * A provider must either take ownership of the complete total and return {@code true}, or
     * leave it untouched and return {@code false}. This is deliberately separate from FastPath:
     * it is the normal provider contract used by the large integrated working station.
     * </p>
     */
    default boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputTotal, long craftCount,
        @Nullable UUID craftingJobId) {
        return false;
    }
}
