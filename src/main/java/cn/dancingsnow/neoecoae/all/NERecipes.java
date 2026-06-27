package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import cpw.mods.fml.common.registry.GameRegistry;

public final class NERecipes {

    private NERecipes() {}

    public static void register() {
        registerToolRecipes(
            "ingotAluminum",
            NEItems.aluminumAxe,
            NEItems.aluminumHoe,
            NEItems.aluminumPickaxe,
            NEItems.aluminumShovel,
            NEItems.aluminumSword);
        registerToolRecipes(
            "ingotTungsten",
            NEItems.tungstenAxe,
            NEItems.tungstenHoe,
            NEItems.tungstenPickaxe,
            NEItems.tungstenShovel,
            NEItems.tungstenSword);
        registerToolRecipes(
            "ingotAluminumAlloy",
            NEItems.aluminumAlloyAxe,
            NEItems.aluminumAlloyHoe,
            NEItems.aluminumAlloyPickaxe,
            NEItems.aluminumAlloyShovel,
            NEItems.aluminumAlloySword);
        registerToolRecipes(
            "ingotBlackTungstenAlloy",
            NEItems.blackTungstenAlloyAxe,
            NEItems.blackTungstenAlloyHoe,
            NEItems.blackTungstenAlloyPickaxe,
            NEItems.blackTungstenAlloyShovel,
            NEItems.blackTungstenAlloySword);

        storageBlock("rawAluminum", NEBlocks.rawAluminumBlock, NEItems.rawAluminumOre);
        storageBlock("rawTungsten", NEBlocks.rawTungstenBlock, NEItems.rawTungstenOre);
        storageBlock("ingotAluminum", NEBlocks.aluminumBlock, NEItems.aluminumIngot);
        storageBlock("ingotTungsten", NEBlocks.tungstenBlock, NEItems.tungstenIngot);
        storageBlock("ingotAluminumAlloy", NEBlocks.aluminumAlloyBlock, NEItems.aluminumAlloyIngot);
        storageBlock("ingotBlackTungstenAlloy", NEBlocks.blackTungstenAlloyBlock, NEItems.blackTungstenAlloyIngot);

        shaped(
            NEBlocks.aluminumAlloyCasing,
            "ABA",
            "BCB",
            "ABA",
            'A',
            "ingotAluminumAlloy",
            'B',
            "blockGlass",
            'C',
            "ingotAluminum");
        shaped(
            NEBlocks.blackTungstenAlloyCasing,
            "ABA",
            "BCB",
            "ABA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            "blockGlass",
            'C',
            "ingotTungsten");

        shapeless(NEItems.aluminumAlloyDust, "dustIron", "dustAluminum", "dustCertusQuartz", "dustCertusQuartz");
        shapeless(NEItems.blackTungstenAlloyDust, "dustTungsten", "dustAluminumAlloy", "dustFluix", "dustFluix");

        smelt(NEBlocks.aluminumOre, NEItems.rawAluminumOre, 0.7F);
        smelt(NEBlocks.tungstenOre, NEItems.rawTungstenOre, 1.0F);
        smelt(NEItems.rawAluminumOre, NEItems.aluminumIngot, 0.7F);
        smelt(NEItems.rawTungstenOre, NEItems.tungstenIngot, 1.0F);
        smelt(NEItems.aluminumDust, NEItems.aluminumIngot, 0.7F);
        smelt(NEItems.tungstenDust, NEItems.tungstenIngot, 1.0F);
        smelt(NEItems.aluminumAlloyDust, NEItems.aluminumAlloyIngot, 0.9F);
        smelt(NEItems.blackTungstenAlloyDust, NEItems.blackTungstenAlloyIngot, 1.0F);
    }

    private static void registerToolRecipes(Object material, Item axe, Item hoe, Item pickaxe, Item shovel,
        Item sword) {
        shaped(axe, "AA", "AS", " S", 'A', material, 'S', "stickWood");
        shaped(hoe, "AA", " S", " S", 'A', material, 'S', "stickWood");
        shaped(pickaxe, "AAA", " S ", " S ", 'A', material, 'S', "stickWood");
        shaped(shovel, "A", "S", "S", 'A', material, 'S', "stickWood");
        shaped(sword, "A", "A", "S", 'A', material, 'S', "stickWood");
    }

    private static void storageBlock(Object material, Block block, Item item) {
        shaped(block, "AAA", "AAA", "AAA", 'A', material);
        shapeless(new ItemStack(item, 9), block);
    }

    private static void shaped(Item output, Object... recipe) {
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(output), recipe));
    }

    private static void shaped(Block output, Object... recipe) {
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(output), recipe));
    }

    private static void shapeless(Item output, Object... recipe) {
        shapeless(new ItemStack(output), recipe);
    }

    private static void shapeless(ItemStack output, Object... recipe) {
        GameRegistry.addRecipe(new ShapelessOreRecipe(output, recipe));
    }

    private static void smelt(Item input, Item output, float xp) {
        GameRegistry.addSmelting(input, new ItemStack(output), xp);
    }

    private static void smelt(Block input, Item output, float xp) {
        GameRegistry.addSmelting(input, new ItemStack(output), xp);
    }
}
