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

        registerMachineBlockRecipes();

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

    private static void registerMachineBlockRecipes() {
        shaped(
            NEBlocks.storageCasing,
            "AAA",
            "ABA",
            "AAA",
            'A',
            "ingotAluminumAlloy",
            'B',
            NEBlocks.aluminumAlloyCasing);
        shaped(
            NEBlocks.computationCasing,
            "AAA",
            "ABA",
            "AAA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            NEBlocks.blackTungstenAlloyCasing);
        shaped(
            NEBlocks.craftingCasing,
            "AAA",
            "ABA",
            "AAA",
            'A',
            "ingotAluminumAlloy",
            'B',
            NEBlocks.blackTungstenAlloyCasing);

        shaped(NEBlocks.storageVent, " A ", "ABA", " A ", 'A', "ingotAluminumAlloy", 'B', NEBlocks.storageCasing);
        shaped(NEBlocks.craftingVent, " A ", "ABA", " A ", 'A', "ingotAluminumAlloy", 'B', NEBlocks.craftingCasing);
        shaped(NEBlocks.inputHatch, " A ", "ABA", " A ", 'A', "ingotAluminumAlloy", 'B', "blockGlass");
        shaped(NEBlocks.outputHatch, " A ", "ABA", " A ", 'A', "ingotTungsten", 'B', "blockGlass");

        shaped(
            NEBlocks.storageInterface,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotAluminumAlloy",
            'B',
            "dustCertusQuartz",
            'C',
            NEBlocks.storageCasing,
            'D',
            "blockGlass");
        shaped(
            NEBlocks.craftingInterface,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotAluminumAlloy",
            'B',
            "dustCertusQuartz",
            'C',
            NEBlocks.craftingCasing,
            'D',
            "blockGlass");
        shaped(
            NEBlocks.computationInterface,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            "dustFluix",
            'C',
            NEBlocks.computationCasing,
            'D',
            "blockGlass");

        shaped(
            NEBlocks.ecoDrive,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotAluminumAlloy",
            'B',
            "dustCertusQuartz",
            'C',
            NEBlocks.storageCasing,
            'D',
            NEBlocks.storageInterface);
        shaped(
            NEBlocks.computationDrive,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            "dustFluix",
            'C',
            NEBlocks.computationCasing,
            'D',
            NEBlocks.computationInterface);

        shaped(
            NEBlocks.craftingPatternBus,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotAluminumAlloy",
            'B',
            "dustCertusQuartz",
            'C',
            NEBlocks.craftingCasing,
            'D',
            NEBlocks.craftingInterface);
        shaped(
            NEBlocks.craftingWorker,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            "dustFluix",
            'C',
            NEBlocks.craftingCasing,
            'D',
            NEBlocks.craftingPatternBus);
        shaped(
            NEBlocks.computationTransmitter,
            "ABA",
            "CDC",
            "ABA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            "dustFluix",
            'C',
            NEBlocks.computationCasing,
            'D',
            NEBlocks.computationInterface);

        tieredMachine(
            NEBlocks.storageSystemL4,
            NEBlocks.storageSystemL6,
            NEBlocks.storageSystemL9,
            NEBlocks.storageCasing,
            NEBlocks.ecoDrive,
            "ingotAluminumAlloy");
        tieredMachine(
            NEBlocks.craftingSystemL4,
            NEBlocks.craftingSystemL6,
            NEBlocks.craftingSystemL9,
            NEBlocks.craftingCasing,
            NEBlocks.craftingWorker,
            "ingotAluminumAlloy");
        tieredMachine(
            NEBlocks.computationSystemL4,
            NEBlocks.computationSystemL6,
            NEBlocks.computationSystemL9,
            NEBlocks.computationCasing,
            NEBlocks.computationDrive,
            "ingotBlackTungstenAlloy");
        tieredMachine(
            NEBlocks.computationCoolingControllerL4,
            NEBlocks.computationCoolingControllerL6,
            NEBlocks.computationCoolingControllerL9,
            NEBlocks.computationCasing,
            "blockGlass",
            "ingotBlackTungstenAlloy");
    }

    private static void tieredMachine(Block tier4, Block tier6, Block tier9, Block casing, Object core,
        Object material) {
        shaped(tier4, "ABA", "CDC", "ABA", 'A', material, 'B', "dustCertusQuartz", 'C', casing, 'D', core);
        shaped(tier6, "ABA", "CDC", "ABA", 'A', material, 'B', "dustFluix", 'C', tier4, 'D', casing);
        shaped(tier9, "ABA", "CDC", "ABA", 'A', "ingotBlackTungstenAlloy", 'B', "dustFluix", 'C', tier6, 'D', casing);
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
