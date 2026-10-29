package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/**
 * Marks only the target AE2's plain PatternHelper processing contract, preserving the high-version exact-type guard.
 */
public final class AEProcessingPattern extends NativePatternDetails {

    AEProcessingPattern(ICraftingPatternDetails pattern, World world, Substitutes substitutes) {
        super(pattern, world, substitutes);
    }
}
