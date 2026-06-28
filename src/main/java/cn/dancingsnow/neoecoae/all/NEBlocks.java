package cn.dancingsnow.neoecoae.all;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCompressed;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.block.BlockComputationDrive;
import cn.dancingsnow.neoecoae.block.BlockDirectionalModernModel;
import cn.dancingsnow.neoecoae.block.BlockEcoDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.block.ItemBlockModelDrive;
import cn.dancingsnow.neoecoae.block.ItemBlockModernModel;
import cn.dancingsnow.neoecoae.block.NEBlock;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NEBlocks {

    private static final List<BlockModernModel> MODERN_MODEL_BLOCKS = new ArrayList<BlockModernModel>();

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
    public static final Block storageCasing = texturedMachineBlock(
        "storage_casing",
        NeoECOAE.MODID + ":storage/casing",
        5.0F,
        10.0F,
        2);
    public static final Block computationCasing = texturedMachineBlock(
        "computation_casing",
        NeoECOAE.MODID + ":compute/casing",
        5.0F,
        10.0F,
        2);
    public static final Block craftingCasing = texturedMachineBlock(
        "crafting_casing",
        NeoECOAE.MODID + ":crafting/casing",
        5.0F,
        10.0F,
        2);
    public static final Block storageVent = directionalModelBlock(
        "storage_vent",
        "storage_vent",
        new String[] { NeoECOAE.MODID + ":block/storage/casing", NeoECOAE.MODID + ":block/storage/casing_back",
            NeoECOAE.MODID + ":block/storage/vents_north", NeoECOAE.MODID + ":block/storage/casing_side" });
    public static final Block inputHatch = texturedMachineBlock(
        "input_hatch",
        NeoECOAE.MODID + ":crafting/hatch_input",
        5.0F,
        10.0F,
        2);
    public static final Block outputHatch = texturedMachineBlock(
        "output_hatch",
        NeoECOAE.MODID + ":crafting/hatch_output",
        5.0F,
        10.0F,
        2);
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

    public static List<BlockModernModel> getModernModelBlocks() {
        return Collections.unmodifiableList(MODERN_MODEL_BLOCKS);
    }

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
        register(storageCasing, "storage_casing");
        register(computationCasing, "computation_casing");
        register(craftingCasing, "crafting_casing");
        GameRegistry.registerBlock(storageVent, ItemBlockModernModel.class, "storage_vent");
        register(inputHatch, "input_hatch");
        register(outputHatch, "output_hatch");
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

    private static Block texturedMachineBlock(String id, String texture, float hardness, float resistance,
        int harvestLevel) {
        Block block = new NEBlock(Material.iron).setBlockName(id)
            .setBlockTextureName(texture)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(hardness)
            .setResistance(resistance)
            .setStepSound(Block.soundTypeMetal);
        block.setHarvestLevel("pickaxe", harvestLevel);
        return block;
    }

    private static Block modelBlock(String id, String modelName, String[] textures) {
        return registerModernModelBlock(new BlockModernModel(id, modelName, textures));
    }

    private static Block directionalModelBlock(String id, String modelName, String[] textures) {
        return registerModernModelBlock(new BlockDirectionalModernModel(id, modelName, textures));
    }

    private static Block registerModernModelBlock(BlockModernModel block) {
        MODERN_MODEL_BLOCKS.add(block);
        return block;
    }

    private static void register(Block block, String id) {
        GameRegistry.registerBlock(block, id);
    }
}
