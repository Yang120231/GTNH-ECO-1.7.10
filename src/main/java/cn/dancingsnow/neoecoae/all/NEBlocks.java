package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCompressed;
import net.minecraft.block.material.MapColor;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.block.BlockComputationDrive;
import cn.dancingsnow.neoecoae.block.BlockDirectionalModernModel;
import cn.dancingsnow.neoecoae.block.BlockEcoDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.block.ItemBlockModelDrive;
import cn.dancingsnow.neoecoae.block.ItemBlockModernModel;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NEBlocks {

    public static final Block aluminumOre = ore("aluminum_ore", NEItems.rawAluminumOre, 3.0F, 5.0F, 2);
    public static final Block tungstenOre = ore("tungsten_ore", NEItems.rawTungstenOre, 4.0F, 8.0F, 3);

    public static final Block rawAluminumBlock = storageBlock("raw_aluminum_block", MapColor.ironColor, 5.0F, 10.0F, 2);
    public static final Block rawTungstenBlock = storageBlock("raw_tungsten_block", MapColor.ironColor, 6.0F, 12.0F, 3);
    public static final Block aluminumBlock = storageBlock("aluminum_block", MapColor.ironColor, 5.0F, 10.0F, 2);
    public static final Block tungstenBlock = storageBlock("tungsten_block", MapColor.ironColor, 6.0F, 12.0F, 3);
    public static final Block aluminumAlloyBlock = storageBlock(
        "aluminum_alloy_block",
        MapColor.ironColor,
        5.0F,
        10.0F,
        2);
    public static final Block blackTungstenAlloyBlock = storageBlock(
        "black_tungsten_alloy_block",
        MapColor.ironColor,
        6.0F,
        12.0F,
        3);
    public static final Block energizedCrystalBlock = storageBlock(
        "energized_crystal_block",
        MapColor.diamondColor,
        5.0F,
        10.0F,
        2);
    public static final Block energizedSuperconductiveBlock = storageBlock(
        "energized_superconductive_block",
        MapColor.diamondColor,
        5.0F,
        10.0F,
        2);
    public static final Block energizedFluixCrystalBlock = storageBlock(
        "energized_fluix_crystal_block",
        MapColor.diamondColor,
        5.0F,
        10.0F,
        2);
    public static final Block aluminumAlloyCasing = modelBlock(
        "aluminum_alloy_casing",
        "aluminum_alloy_casing",
        new String[] { NeoECOAE.MODID + ":block/aluminum_alloy_casing" });
    public static final Block blackTungstenAlloyCasing = modelBlock(
        "black_tungsten_alloy_casing",
        "black_tungsten_alloy_casing",
        new String[] { NeoECOAE.MODID + ":block/black_tungsten_alloy_casing" });
    public static final Block storageCasing = modelBlock(
        "storage_casing",
        "storage_casing",
        new String[] { NeoECOAE.MODID + ":block/storage/casing" });
    public static final Block computationCasing = modelBlock(
        "computation_casing",
        "computation_casing",
        new String[] { NeoECOAE.MODID + ":block/compute/casing" });
    public static final Block craftingCasing = modelBlock(
        "crafting_casing",
        "crafting_casing",
        new String[] { NeoECOAE.MODID + ":block/crafting/casing" });
    public static final Block storageVent = directionalModelBlock(
        "storage_vent",
        "storage_vent",
        new String[] { NeoECOAE.MODID + ":block/storage/casing", NeoECOAE.MODID + ":block/storage/casing_back",
            NeoECOAE.MODID + ":block/storage/vents_north", NeoECOAE.MODID + ":block/storage/casing_side" });
    public static final Block inputHatch = modelBlock(
        "input_hatch",
        "input_hatch",
        new String[] { NeoECOAE.MODID + ":block/crafting/hatch_input" });
    public static final Block outputHatch = modelBlock(
        "output_hatch",
        "output_hatch",
        new String[] { NeoECOAE.MODID + ":block/crafting/hatch_output" });
    public static final Block craftingVent = directionalModelBlock(
        "crafting_vent",
        "crafting_vent",
        new String[] { NeoECOAE.MODID + ":block/crafting/casing", NeoECOAE.MODID + ":block/crafting/casing_back",
            NeoECOAE.MODID + ":block/crafting/casing_side", NeoECOAE.MODID + ":block/crafting/vent_north" });
    public static final Block craftingPatternBus = directionalModelBlock(
        "crafting_pattern_bus",
        "crafting_pattern_bus",
        new String[] { NeoECOAE.MODID + ":block/crafting/casing", NeoECOAE.MODID + ":block/crafting/casing_side",
            NeoECOAE.MODID + ":block/crafting/pattern_bus", NeoECOAE.MODID + ":block/crafting/casing_back" });
    public static final Block ecoDrive = new BlockEcoDrive();
    public static final Block computationDrive = new BlockComputationDrive();

    private NEBlocks() {}

    public static void register() {
        register(aluminumOre, "aluminum_ore");
        register(tungstenOre, "tungsten_ore");
        register(rawAluminumBlock, "raw_aluminum_block");
        register(rawTungstenBlock, "raw_tungsten_block");
        register(aluminumBlock, "aluminum_block");
        register(tungstenBlock, "tungsten_block");
        register(aluminumAlloyBlock, "aluminum_alloy_block");
        register(blackTungstenAlloyBlock, "black_tungsten_alloy_block");
        register(energizedCrystalBlock, "energized_crystal_block");
        register(energizedSuperconductiveBlock, "energized_superconductive_block");
        register(energizedFluixCrystalBlock, "energized_fluix_crystal_block");
        GameRegistry.registerBlock(aluminumAlloyCasing, ItemBlockModernModel.class, "aluminum_alloy_casing");
        GameRegistry.registerBlock(blackTungstenAlloyCasing, ItemBlockModernModel.class, "black_tungsten_alloy_casing");
        GameRegistry.registerBlock(storageCasing, ItemBlockModernModel.class, "storage_casing");
        GameRegistry.registerBlock(computationCasing, ItemBlockModernModel.class, "computation_casing");
        GameRegistry.registerBlock(craftingCasing, ItemBlockModernModel.class, "crafting_casing");
        GameRegistry.registerBlock(storageVent, ItemBlockModernModel.class, "storage_vent");
        GameRegistry.registerBlock(inputHatch, ItemBlockModernModel.class, "input_hatch");
        GameRegistry.registerBlock(outputHatch, ItemBlockModernModel.class, "output_hatch");
        GameRegistry.registerBlock(craftingVent, ItemBlockModernModel.class, "crafting_vent");
        GameRegistry.registerBlock(craftingPatternBus, ItemBlockModernModel.class, "crafting_pattern_bus");
        GameRegistry.registerBlock(ecoDrive, ItemBlockModelDrive.class, "eco_drive");
        GameRegistry.registerBlock(computationDrive, ItemBlockModelDrive.class, "computation_drive");
    }

    private static Block ore(String id, net.minecraft.item.Item droppedItem, float hardness, float resistance,
        int harvestLevel) {
        Block block = new NEBlockOre(droppedItem).setBlockName(id)
            .setBlockTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(hardness)
            .setResistance(resistance)
            .setStepSound(Block.soundTypePiston);
        block.setHarvestLevel("pickaxe", harvestLevel);
        return block;
    }

    private static Block storageBlock(String id, MapColor color, float hardness, float resistance, int harvestLevel) {
        Block block = new BlockCompressed(color).setBlockName(id)
            .setBlockTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(hardness)
            .setResistance(resistance)
            .setStepSound(Block.soundTypeMetal);
        block.setHarvestLevel("pickaxe", harvestLevel);
        return block;
    }

    private static Block modelBlock(String id, String modelName, String[] textures) {
        return new BlockModernModel(id, modelName, textures);
    }

    private static Block directionalModelBlock(String id, String modelName, String[] textures) {
        return new BlockDirectionalModernModel(id, modelName, textures);
    }

    private static void register(Block block, String id) {
        GameRegistry.registerBlock(block, id);
    }
}
