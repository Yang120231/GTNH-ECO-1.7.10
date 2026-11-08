package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;

/** Immutable view of the encoded pattern being routed. */
public final class PatternContext {

    public final ItemStack pattern;
    public final ICraftingPatternDetails details;
    public final boolean processing;
    /** Recipe-only view; the encoded pattern and its tunnel references are never rewritten. */
    public final IAEStack<?>[] recipeInputs;
    public final String inputError;
    /** Null unless an NEI hint naming a recipe map is still bound to exactly this pattern in this window. */
    public final RouteHint hint;
    public final World world;

    private PatternContext(ItemStack pattern, ICraftingPatternDetails details, RouteHint hint, World world,
        IGrid grid) {
        this.pattern = PatternStacks.copy(pattern, 1);
        this.details = details;
        this.processing = !details.isCraftable();
        this.hint = hint;
        this.world = world;
        IAEStack<?>[] inputs = details.getCondensedAEInputs();
        String error = null;
        if (processing && !details.isInputOnly() && NativePatternDetails.hasTunnelInputs(inputs)) {
            ICraftingGrid crafting = grid == null ? null : grid.getCache(ICraftingGrid.class);
            if (!(crafting instanceof CraftingGridCache)) {
                error = "gui.neoecoae.pattern_upload.reason.tunnel_missing";
            } else {
                var expanded = NativePatternDetails.expandInputs(details, (CraftingGridCache) crafting, true);
                if (expanded == null) error = "gui.neoecoae.pattern_upload.reason.tunnel_invalid";
                else {
                    inputs = expanded;
                    if (NativePatternDetails.hasTunnelInputs(inputs))
                        error = "gui.neoecoae.pattern_upload.reason.tunnel_missing";
                }
            }
        }
        recipeInputs = inputs;
        inputError = error;
    }

    /** Returns null for anything that is not a decodable encoded pattern. */
    public static PatternContext of(ItemStack pattern, RouteHint hint, World world, IGrid grid) {
        ICraftingPatternDetails details = decode(pattern, world);
        return details == null ? null : new PatternContext(pattern, details, hint, world, grid);
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
