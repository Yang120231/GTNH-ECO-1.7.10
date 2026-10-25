package cn.dancingsnow.neoecoae.platform.ae2.storage;

import net.minecraft.item.ItemStack;

import appeng.items.contents.CellUpgrades;

public final class UpgradeInventories {

    private UpgradeInventories() {}

    public static IUpgradeInventory forItem(ItemStack stack, int slots) {
        return new IUpgradeInventory(new CellUpgrades(stack, slots));
    }
}
