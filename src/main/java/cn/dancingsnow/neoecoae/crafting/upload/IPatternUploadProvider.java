package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;
import gregtech.api.recipe.RecipeMap;

/** Optional adapter contract for modded ME-connected pattern hatches. */
public interface IPatternUploadProvider extends IGridHost {

    IInventory getPatternUploadInventory();

    ItemStack getPatternUploadIcon();

    String getPatternUploadName();

    TileEntity getTileEntity();

    RecipeMap<?> getPatternUploadRecipeMap();

    default boolean isPatternUploadActive() {
        return true;
    }
}
