package cn.dancingsnow.neoecoae.platform;

import net.minecraft.item.Item;

/** Concrete native item identities without starting FML's LaunchClassLoader or a world. */
public final class TestItems {

    public static final Item STONE = new Item(), DIRT = new Item(), COBBLESTONE = new Item(), IRON_INGOT = new Item(),
        GLASS_BOTTLE = new Item(), SAND = new Item(), GRAVEL = new Item(), GLASS = new Item(), DIAMOND = new Item();
    public static final Item COAL = new Item(), IRON_PICKAXE = new Item().setMaxDamage(250)
        .setMaxStackSize(1);
    public static final Item RAW_IRON = new Item(), RAW_COPPER = new Item(), COPPER_INGOT = new Item(),
        GOLD_INGOT = new Item();
    public static final Item BRICK = new Item(), BUCKET = new Item(), CHARCOAL = new Item(), CLAY_BALL = new Item(),
        EMERALD = new Item(), END_STONE = new Item(), FLINT = new Item(), HONEY_BOTTLE = new Item(),
        IRON_BLOCK = new Item(), IRON_NUGGET = new Item(), NETHERITE_UPGRADE_SMITHING_TEMPLATE = new Item(),
        NETHERRACK = new Item(), OBSIDIAN = new Item(), PAPER = new Item(), SUGAR = new Item(),
        WATER_BUCKET = new Item();

    private TestItems() {}
}
