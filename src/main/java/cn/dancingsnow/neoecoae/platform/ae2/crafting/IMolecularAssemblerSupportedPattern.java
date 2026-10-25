package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.crafting.CraftingInput;

/** Used modern assembler API, mapped to the pinned native pattern and recipe contracts. */
public interface IMolecularAssemblerSupportedPattern extends IPatternDetails {

    ItemStack assemble(CraftingInput input, World level);

    default java.util.List<ItemStack> getRemainingItems(CraftingInput input) {
        return new java.util.ArrayList<>(java.util.Collections.nCopies(input.size(), null));
    }

    boolean isItemValid(int slot, AEItemKey key, World level);

    boolean isSlotEnabled(int slot);

    void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor);

    @Override
    default boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    @FunctionalInterface
    interface CraftingGridAccessor {

        void set(int slot, ItemStack stack);
    }
}
