package cn.dancingsnow.neoecoae.platform;

import net.minecraft.item.Item;

/** Concrete native item identities without starting FML's LaunchClassLoader or a world. */
public final class TestItems {

    public static final Item STONE = new Item(), DIRT = new Item(), COBBLESTONE = new Item(), IRON_INGOT = new Item(),
        GLASS_BOTTLE = new Item(), SAND = new Item(), GRAVEL = new Item(), GLASS = new Item(), DIAMOND = new Item();

    private TestItems() {}
}
