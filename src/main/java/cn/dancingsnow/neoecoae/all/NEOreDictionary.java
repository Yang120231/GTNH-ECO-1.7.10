package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public final class NEOreDictionary {

    public static final String NEO_TUNGSTEN_ORE = "neoecoaeTungstenOre";
    public static final String NEO_TUNGSTEN_RAW = "neoecoaeTungstenRaw";
    public static final String NEO_TUNGSTEN_INGOT = "neoecoaeTungstenIngot";
    public static final String NEO_TUNGSTEN_DUST = "neoecoaeTungstenDust";
    public static final String NEO_TUNGSTEN_RAW_BLOCK = "neoecoaeTungstenRawBlock";
    public static final String NEO_TUNGSTEN_BLOCK = "neoecoaeTungstenBlock";
    public static final String NEO_SILICON = "neoecoaeSilicon";

    private NEOreDictionary() {}

    public static void register() {
        ore("oreAluminum", NEBlocks.aluminumOre);
        ore(NEO_TUNGSTEN_ORE, NEBlocks.tungstenOre);

        ore("rawAluminum", NEItems.rawAluminumOre);
        ore("rawAluminium", NEItems.rawAluminumOre);
        ore(NEO_TUNGSTEN_RAW, NEItems.rawTungstenOre);

        ore("ingotAluminum", NEItems.aluminumIngot);
        ore("ingotAluminium", NEItems.aluminumIngot);
        ore(NEO_TUNGSTEN_INGOT, NEItems.tungstenIngot);
        ore("ingotAluminumAlloy", NEItems.aluminumAlloyIngot);
        ore("ingotAluminiumAlloy", NEItems.aluminumAlloyIngot);
        ore("ingotBlackTungstenAlloy", NEItems.blackTungstenAlloyIngot);
        ore("ingotCrystal", NEItems.crystalIngot);
        ore("ingotEnergizedSuperconductive", NEItems.energizedSuperconductiveIngot);

        ore("dustAluminum", NEItems.aluminumDust);
        ore("dustAluminium", NEItems.aluminumDust);
        ore(NEO_TUNGSTEN_DUST, NEItems.tungstenDust);
        ore("dustAluminumAlloy", NEItems.aluminumAlloyDust);
        ore("dustAluminiumAlloy", NEItems.aluminumAlloyDust);
        ore("dustBlackTungstenAlloy", NEItems.blackTungstenAlloyDust);
        ore("dustEnergizedCrystal", NEItems.energizedCrystalDust);
        ore("dustEnergizedFluixCrystal", NEItems.energizedFluixCrystalDust);
        ore("gemEnergizedCrystal", NEItems.energizedCrystal);
        ore("gemEnergizedFluixCrystal", NEItems.energizedFluixCrystal);
        ore("dustCryotheum", NEItems.cryotheum);
        ore("gemCryotheum", NEItems.cryotheumCrystal);

        ore("blockRawAluminum", NEBlocks.rawAluminumBlock);
        ore("blockRawAluminium", NEBlocks.rawAluminumBlock);
        ore(NEO_TUNGSTEN_RAW_BLOCK, NEBlocks.rawTungstenBlock);
        ore("blockAluminum", NEBlocks.aluminumBlock);
        ore("blockAluminium", NEBlocks.aluminumBlock);
        ore(NEO_TUNGSTEN_BLOCK, NEBlocks.tungstenBlock);
        ore("blockAluminumAlloy", NEBlocks.aluminumAlloyBlock);
        ore("blockAluminiumAlloy", NEBlocks.aluminumAlloyBlock);
        ore("blockBlackTungstenAlloy", NEBlocks.blackTungstenAlloyBlock);
        ore("blockEnergizedCrystal", NEBlocks.energizedCrystalBlock);
        ore("blockEnergizedFluixCrystal", NEBlocks.energizedFluixCrystalBlock);
        ore("blockEnergizedSuperconductive", NEBlocks.energizedSuperconductiveBlock);
    }

    public static void registerSilicon() {
        ore(NEO_SILICON, NEAE2RecipeItems.silicon());
        for (ItemStack silicon : OreDictionary.getOres("itemSilicon")) {
            if (!isPlate(silicon)) {
                ore(NEO_SILICON, silicon);
            }
        }
    }

    private static void ore(String name, Item item) {
        OreDictionary.registerOre(name, item);
    }

    private static void ore(String name, ItemStack stack) {
        OreDictionary.registerOre(name, stack);
    }

    private static void ore(String name, Block block) {
        OreDictionary.registerOre(name, block);
    }

    private static boolean isPlate(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return true;
        }
        String itemName = stack.getItem()
            .getUnlocalizedName()
            .toLowerCase();
        if (itemName.contains("plate")) {
            return true;
        }
        for (int id : OreDictionary.getOreIDs(stack)) {
            if (OreDictionary.getOreName(id)
                .toLowerCase()
                .contains("plate")) {
                return true;
            }
        }
        return false;
    }
}
