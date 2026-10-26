package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.Collections;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;

/** Decode the physical native pattern, retaining its actual recipe and generic item/fluid contracts. */
public final class PatternDetailsHelper {

    private PatternDetailsHelper() {}

    public static IPatternDetails decodePattern(AEItemKey definition, World world) {
        if (definition == null || !(definition.getItem() instanceof ICraftingPatternItem)) return null;
        ItemStack stack = definition.toStack();
        var details = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world);
        return details == null ? null : NativePatternDetails.capture(details, world, Collections.emptyList());
    }
}
