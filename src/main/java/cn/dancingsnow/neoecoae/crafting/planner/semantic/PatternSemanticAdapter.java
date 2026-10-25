package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** @deprecated Use the public pattern semantics API for new integrations. */
@Deprecated
public interface PatternSemanticAdapter {

    boolean supports(IPatternDetails pattern);

    PatternSemantics analyze(IPatternDetails pattern);

    /**
     * Returns whether the input at the supplied semantic slot ignores item components during matching.
     * Optional integrations override this at their compatibility boundary so the core compiler does not
     * link against an integration-only pattern interface.
     */
    default boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        return false;
    }

    /** Chooses an accepted, equal-amount child template without changing the physical pattern. */
    default AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
        ICraftingService craftingService) {
        return encodedKey;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
