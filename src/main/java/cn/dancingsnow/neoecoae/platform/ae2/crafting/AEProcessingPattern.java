package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.Collection;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/**
 * Marks only the target AE2's plain PatternHelper processing contract, preserving the high-version exact-type guard.
 */
public final class AEProcessingPattern extends NativePatternDetails {

    AEProcessingPattern(ICraftingPatternDetails pattern, World world, Collection<AEKey> candidates) {
        super(pattern, world, candidates);
    }
}
