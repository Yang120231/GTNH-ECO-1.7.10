package cn.dancingsnow.neoecoae.api.me.provider;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.util.inv.MEInventoryCrafting;

/** Optional native AE2 intake contract for linear processing patterns. No class-name registration is needed. */
public interface ECOCountedCraftingMedium extends ICraftingMedium {

    /** Side-effect-free opt-in for this published pattern in the medium's current mode. */
    boolean eco$supportsCountedBatch(ICraftingPatternDetails pattern);

    /**
     * One standalone submission, not an extra push after an ordinary one. The read-only table contains ONE copy,
     * with its physical slots and native stack channels. Return the number of complete copies actually taken
     * (0..offered), with exactly those outputs owed. Recheck live capacity, mode and locks here: the earlier query
     * is not a reservation. Zero must leave ownership unchanged. A throw or invalid receipt after entering the
     * medium is indeterminate: ECO will not refund or retry that offer.
     */
    int eco$pushCountedBatch(ICraftingPatternDetails pattern, MEInventoryCrafting oneCopy, int offered);
}
