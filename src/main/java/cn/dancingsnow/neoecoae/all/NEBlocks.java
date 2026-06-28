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
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
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
    public static final Block storageInterface = directionalModelBlock(
        "storage_interface",
        "storage_interface",
        new String[] { NeoECOAE.MODID + ":block/storage/me_bus", NeoECOAE.MODID + ":block/storage/me_bus_light" });
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
    public static final Block craftingWorker = directionalModelBlock(
        "crafting_worker",
        "crafting_worker",
        new String[] { NeoECOAE.MODID + ":block/crafting/core/working_core_north",
            NeoECOAE.MODID + ":block/crafting/core/working_core_light", NeoECOAE.MODID + ":block/crafting/casing",
            NeoECOAE.MODID + ":block/crafting/casing_back", NeoECOAE.MODID + ":block/crafting/core/core_side" });
    public static final Block craftingInterface = directionalModelBlock(
        "crafting_interface",
        "crafting_interface",
        new String[] { NeoECOAE.MODID + ":block/crafting/me_bus", NeoECOAE.MODID + ":block/crafting/me_bus_light" });
    public static final Block ecoDrive = new BlockEcoDrive();
    public static final Block computationDrive = new BlockComputationDrive();
    public static final Block computationInterface = directionalModelBlock(
        "computation_interface",
        "computation_interface",
        new String[] { NeoECOAE.MODID + ":block/compute/me_bus", NeoECOAE.MODID + ":block/compute/me_bus_light" });
    public static final Block computationTransmitter = directionalModelBlock(
        "computation_transmitter",
        "computation_transmitter",
        new String[] { NeoECOAE.MODID + ":block/compute/cable/plug_off", NeoECOAE.MODID + ":block/compute/casing_back",
            NeoECOAE.MODID + ":block/compute/casing", NeoECOAE.MODID + ":block/compute/transmitter/transmitter_north",
            NeoECOAE.MODID + ":block/compute/transmitter/transmitter_north_glass",
            NeoECOAE.MODID + ":block/compute/transmitter/transmitter_side_east",
            NeoECOAE.MODID + ":block/compute/transmitter/transmitter_side_west" });
    public static final Block computationCoolingControllerL4 = computationCoolingController("l4", "controller_east_a");
    public static final Block computationCoolingControllerL6 = computationCoolingController("l6", "controller_east_b");
    public static final Block computationCoolingControllerL9 = computationCoolingController("l9", "controller_east_c");
    public static final Block computationSystemL4 = computationSystem("l4", "controller_side_layer_a");
    public static final Block computationSystemL6 = computationSystem("l6", "controller_side_layer_b");
    public static final Block computationSystemL9 = computationSystem("l9", "controller_side_layer_c");
    public static final Block storageSystemL4 = storageSystem("l4", "controller_north_a", "controller_side_a");
    public static final Block storageSystemL6 = storageSystem("l6", "controller_north_b", "controller_side_b");
    public static final Block storageSystemL9 = storageSystem("l9", "controller_north_c", "controller_side_c");
    public static final Block craftingSystemL4 = craftingSystem("l4", "controller_north_a", "controller_side_a");
    public static final Block craftingSystemL6 = craftingSystem("l6", "controller_north_b", "controller_side_b");
    public static final Block craftingSystemL9 = craftingSystem("l9", "controller_north_c", "controller_side_c");

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
        GameRegistry.registerBlock(storageInterface, ItemBlockModernModel.class, "storage_interface");
        register(inputHatch, "input_hatch");
        register(outputHatch, "output_hatch");
        GameRegistry.registerBlock(craftingVent, ItemBlockModernModel.class, "crafting_vent");
        GameRegistry.registerBlock(craftingPatternBus, ItemBlockModernModel.class, "crafting_pattern_bus");
        GameRegistry.registerBlock(craftingWorker, ItemBlockModernModel.class, "crafting_worker");
        GameRegistry.registerBlock(craftingInterface, ItemBlockModernModel.class, "crafting_interface");
        GameRegistry.registerBlock(ecoDrive, ItemBlockModelDrive.class, "eco_drive");
        GameRegistry.registerBlock(computationDrive, ItemBlockModelDrive.class, "computation_drive");
        GameRegistry.registerBlock(computationInterface, ItemBlockModernModel.class, "computation_interface");
        GameRegistry.registerBlock(computationTransmitter, ItemBlockModernModel.class, "computation_transmitter");
        GameRegistry.registerBlock(
            computationCoolingControllerL4,
            ItemBlockModernModel.class,
            "computation_cooling_controller_l4");
        GameRegistry.registerBlock(
            computationCoolingControllerL6,
            ItemBlockModernModel.class,
            "computation_cooling_controller_l6");
        GameRegistry.registerBlock(
            computationCoolingControllerL9,
            ItemBlockModernModel.class,
            "computation_cooling_controller_l9");
        GameRegistry.registerBlock(computationSystemL4, ItemBlockModernModel.class, "computation_system_l4");
        GameRegistry.registerBlock(computationSystemL6, ItemBlockModernModel.class, "computation_system_l6");
        GameRegistry.registerBlock(computationSystemL9, ItemBlockModernModel.class, "computation_system_l9");
        GameRegistry.registerBlock(storageSystemL4, ItemBlockModernModel.class, "storage_system_l4");
        GameRegistry.registerBlock(storageSystemL6, ItemBlockModernModel.class, "storage_system_l6");
        GameRegistry.registerBlock(storageSystemL9, ItemBlockModernModel.class, "storage_system_l9");
        GameRegistry.registerBlock(craftingSystemL4, ItemBlockModernModel.class, "crafting_system_l4");
        GameRegistry.registerBlock(craftingSystemL6, ItemBlockModernModel.class, "crafting_system_l6");
        GameRegistry.registerBlock(craftingSystemL9, ItemBlockModernModel.class, "crafting_system_l9");
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

    private static Block directionalModelBlock(String id, String modelName, String[] textures,
        ModelFacing inventoryFacing) {
        return registerModernModelBlock(new BlockDirectionalModernModel(id, modelName, textures, inventoryFacing));
    }

    private static Block computationCoolingController(String tier, String eastTexture) {
        return directionalModelBlock(
            "computation_cooling_controller_" + tier,
            "computation_cooling_controller/controller_" + tier + "_off",
            new String[] { NeoECOAE.MODID + ":block/compute/cooling_controller/" + eastTexture,
                NeoECOAE.MODID + ":block/compute/cooling_controller/controller_north",
                NeoECOAE.MODID + ":block/compute/casing_back", NeoECOAE.MODID + ":block/compute/casing",
                NeoECOAE.MODID + ":block/compute/transmitter/transmitter_side_east" },
            ModelFacing.WEST);
    }

    private static Block computationSystem(String tier, String levelTexture) {
        return directionalModelBlock(
            "computation_system_" + tier,
            "computation_controller/controller_" + tier + "_off",
            new String[] { NeoECOAE.MODID + ":block/compute/casing",
                NeoECOAE.MODID + ":block/compute/controller/controller_north",
                NeoECOAE.MODID + ":block/compute/controller/controller_side",
                NeoECOAE.MODID + ":block/compute/casing_back",
                NeoECOAE.MODID + ":block/compute/controller/controller_top",
                NeoECOAE.MODID + ":block/compute/controller/screen_off",
                NeoECOAE.MODID + ":block/compute/controller/" + levelTexture });
    }

    private static Block storageSystem(String tier, String northTexture, String sideTexture) {
        return directionalModelBlock(
            "storage_system_" + tier,
            "storage_controller/controller_" + tier + "_off",
            new String[] { NeoECOAE.MODID + ":block/storage/controller/" + sideTexture,
                NeoECOAE.MODID + ":block/storage/controller/" + northTexture,
                NeoECOAE.MODID + ":block/storage/casing_back", NeoECOAE.MODID + ":block/storage/casing",
                NeoECOAE.MODID + ":block/storage/controller/screen_off" });
    }

    private static Block craftingSystem(String tier, String northTexture, String sideTexture) {
        return directionalModelBlock(
            "crafting_system_" + tier,
            "crafting_controller/controller_" + tier + "_off",
            new String[] { NeoECOAE.MODID + ":block/crafting/controller/" + sideTexture,
                NeoECOAE.MODID + ":block/crafting/controller/" + northTexture,
                NeoECOAE.MODID + ":block/crafting/casing_back", NeoECOAE.MODID + ":block/crafting/casing",
                NeoECOAE.MODID + ":block/crafting/controller/screen_off" });
    }

    private static Block registerModernModelBlock(BlockModernModel block) {
        MODERN_MODEL_BLOCKS.add(block);
        return block;
    }

    private static void register(Block block, String id) {
        GameRegistry.registerBlock(block, id);
    }
}
