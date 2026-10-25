package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;

/** Registry-name facade for the unchanged high-version algorithm fixtures. */
public final class Items {

    public static final Item STONE = Item.getItemFromBlock(Blocks.stone), DIRT = Item.getItemFromBlock(Blocks.dirt),
        COBBLESTONE = Item.getItemFromBlock(Blocks.cobblestone), IRON_INGOT = net.minecraft.init.Items.iron_ingot;

    private Items() {}
}
