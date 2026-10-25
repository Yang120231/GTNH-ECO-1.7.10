package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

public final class StorageTags {

    private StorageTags() {}

    public static boolean isInfiniteComponent(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return false;
        if (stack.getItem() == NativeContent.item("eco_infinite_cell_component")) return true;
        for (int id : OreDictionary.getOreIDs(stack))
            if ("neoecoaeInfiniteCellComponents".equals(OreDictionary.getOreName(id))) return true;
        return false;
    }
}
