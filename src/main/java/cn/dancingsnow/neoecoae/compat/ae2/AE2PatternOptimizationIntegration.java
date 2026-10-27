package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimization;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationRegistry;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingPattern;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** AE2-specific guarantees live here rather than in the generic execution contract. */
public final class AE2PatternOptimizationIntegration {

    private static boolean initialized;

    private AE2PatternOptimizationIntegration() {}

    public static synchronized void init() {
        if (initialized) return;
        // GTNH AE2 crafting patterns are captured as NativeCraftingPattern: the pinned PatternHelper recipe is
        // deterministic for identical concrete inputs, matching the source AECraftingPattern guarantee.
        ECOExecutionOptimizationRegistry.register(
            ResourceLocation.fromNamespaceAndPath("neoecoae", "ae2_assembler"),
            0,
            new ECOExecutionOptimizationAdapter() {

                @Override
                public boolean supports(IPatternDetails pattern) {
                    return pattern instanceof NativeCraftingPattern;
                }

                @Override
                public ECOExecutionOptimization describe(IPatternDetails pattern) {
                    return ECOExecutionOptimization.VERIFIED_ASSEMBLER;
                }
            });
        initialized = true;
    }
}
