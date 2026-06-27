package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.oredict.OreDictionary;

public final class NEOreDictionary {

    private NEOreDictionary() {}

    public static void register() {
        ore("oreAluminum", NEBlocks.aluminumOre);
        ore("oreTungsten", NEBlocks.tungstenOre);

        ore("rawAluminum", NEItems.rawAluminumOre);
        ore("rawAluminium", NEItems.rawAluminumOre);
        ore("rawTungsten", NEItems.rawTungstenOre);

        ore("ingotAluminum", NEItems.aluminumIngot);
        ore("ingotAluminium", NEItems.aluminumIngot);
        ore("ingotTungsten", NEItems.tungstenIngot);
        ore("ingotAluminumAlloy", NEItems.aluminumAlloyIngot);
        ore("ingotAluminiumAlloy", NEItems.aluminumAlloyIngot);
        ore("ingotBlackTungstenAlloy", NEItems.blackTungstenAlloyIngot);
        ore("ingotSuperconductive", NEItems.blackTungstenAlloyIngot);

        ore("dustAluminum", NEItems.aluminumDust);
        ore("dustAluminium", NEItems.aluminumDust);
        ore("dustTungsten", NEItems.tungstenDust);
        ore("dustAluminumAlloy", NEItems.aluminumAlloyDust);
        ore("dustAluminiumAlloy", NEItems.aluminumAlloyDust);
        ore("dustBlackTungstenAlloy", NEItems.blackTungstenAlloyDust);

        ore("blockRawAluminum", NEBlocks.rawAluminumBlock);
        ore("blockRawAluminium", NEBlocks.rawAluminumBlock);
        ore("blockRawTungsten", NEBlocks.rawTungstenBlock);
        ore("blockAluminum", NEBlocks.aluminumBlock);
        ore("blockAluminium", NEBlocks.aluminumBlock);
        ore("blockTungsten", NEBlocks.tungstenBlock);
        ore("blockAluminumAlloy", NEBlocks.aluminumAlloyBlock);
        ore("blockAluminiumAlloy", NEBlocks.aluminumAlloyBlock);
        ore("blockBlackTungstenAlloy", NEBlocks.blackTungstenAlloyBlock);
    }

    private static void ore(String name, Item item) {
        OreDictionary.registerOre(name, item);
    }

    private static void ore(String name, Block block) {
        OreDictionary.registerOre(name, block);
    }
}
