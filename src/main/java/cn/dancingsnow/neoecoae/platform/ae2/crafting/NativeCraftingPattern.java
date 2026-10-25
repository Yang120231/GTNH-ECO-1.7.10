package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.Collection;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.crafting.CraftingInput;

/** The pinned PatternHelper's actual recipe execution and native container-item contract. */
public final class NativeCraftingPattern extends NativePatternDetails implements IMolecularAssemblerSupportedPattern {

    private final net.minecraft.item.crafting.IRecipe recipe;

    NativeCraftingPattern(ICraftingPatternDetails pattern, World world, Collection<AEKey> available) {
        super(pattern, world, available);
        recipe = NativePatternAccess.recipe((appeng.helpers.PatternHelper) pattern);
    }

    @Override
    public ItemStack assemble(CraftingInput input, World world) {
        var inventory = input.nativeInventory();
        return recipe.matches(inventory, world) ? ItemStacks.copy(recipe.getCraftingResult(inventory)) : null;
    }

    @Override
    public List<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = new java.util.ArrayList<ItemStack>();
        for (var stack : input.items()) {
            ItemStack result = ItemStacks.isEmpty(stack) || !stack.getItem()
                .hasContainerItem(stack) ? null
                    : stack.getItem()
                        .getContainerItem(ItemStacks.copyWithCount(stack, 1));
            if (!ItemStacks.isEmpty(result) && result.isItemStackDamageable()
                && result.getItemDamage() > result.getMaxDamage()) result = null;
            remaining.add(ItemStacks.copy(result));
        }
        return remaining;
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey key, World world) {
        return key != null && nativeDetails().isValidItemForSlot(slot, key.toNative(1), world);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        var sparse = getSparseInputs();
        return slot >= 0 && slot < sparse.length && sparse[slot] != null;
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor accessor) {
        var sparse = getSparseInputs();
        var condensed = getInputs();
        if (table.length != condensed.length) throw new IllegalArgumentException("Native crafting input dimensions");
        for (int slot = 0; slot < sparse.length; slot++) if (sparse[slot] != null) {
            int index = -1;
            for (int candidate = 0; candidate < condensed.length; candidate++)
                if (condensed[candidate].getPossibleInputs()[0].what()
                    .equals(sparse[slot].what())) {
                        index = candidate;
                        break;
                    }
            if (index < 0) throw new IllegalStateException("Sparse source input missing from native compressed inputs");
            boolean filled = false;
            for (var entry : table[index]) if (entry.getKey() instanceof AEItemKey && entry.getLongValue() > 0) {
                var key = (AEItemKey) entry.getKey();
                accessor.set(slot, key.toStack(1));
                table[index].remove(key, 1);
                filled = true;
                break;
            }
            if (!filled) throw new IllegalArgumentException("Missing materialized native crafting slot " + slot);
        }
    }
}
