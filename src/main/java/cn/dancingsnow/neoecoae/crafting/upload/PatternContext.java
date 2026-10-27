package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Immutable view of the encoded pattern being routed. */
public final class PatternContext {

    public final ItemStack pattern;
    public final ICraftingPatternDetails details;
    public final boolean processing;
    /** Null unless the NEI hint is still bound to exactly this pattern in this window. */
    public final RouteHint hint;
    public final World world;

    private PatternContext(ItemStack pattern, ICraftingPatternDetails details, RouteHint hint, World world) {
        this.pattern = PatternStacks.copy(pattern, 1);
        this.details = details;
        this.processing = !details.isCraftable();
        this.hint = hint;
        this.world = world;
    }

    /** Returns null for anything that is not a decodable encoded pattern. */
    public static PatternContext of(ItemStack pattern, RouteHint hint, World world) {
        ICraftingPatternDetails details = decode(pattern, world);
        return details == null ? null : new PatternContext(pattern, details, hint, world);
    }

    public static ICraftingPatternDetails decode(ItemStack pattern, World world) {
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem)) return null;
        try {
            return ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public ItemStack primaryOutput() {
        try {
            for (var output : details.getCondensedAEOutputs()) {
                if (output instanceof appeng.api.storage.data.IAEItemStack) {
                    ItemStack stack = ((appeng.api.storage.data.IAEItemStack) output).getItemStack();
                    if (stack != null) return PatternStacks.copy(stack, 1);
                }
            }
        } catch (RuntimeException ignored) { /* Display only. */ }
        return pattern.copy();
    }
}
