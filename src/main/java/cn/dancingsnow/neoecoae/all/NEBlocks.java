package cn.dancingsnow.neoecoae.all;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCompressed;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.block.BlockComputationCoolingController;
import cn.dancingsnow.neoecoae.block.BlockComputationDrive;
import cn.dancingsnow.neoecoae.block.BlockComputationTransmitter;
import cn.dancingsnow.neoecoae.block.BlockCraftingHatch;
import cn.dancingsnow.neoecoae.block.BlockCraftingPatternBus;
import cn.dancingsnow.neoecoae.block.BlockCraftingWorker;
import cn.dancingsnow.neoecoae.block.BlockDirectionalModernModel;
import cn.dancingsnow.neoecoae.block.BlockECOController;
import cn.dancingsnow.neoecoae.block.BlockECOInterface;
import cn.dancingsnow.neoecoae.block.BlockEcoDrive;
import cn.dancingsnow.neoecoae.block.BlockFormedDirectionalModernModel;
import cn.dancingsnow.neoecoae.block.BlockFormedTexturedMachine;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.block.BlockTieredModernModel;
import cn.dancingsnow.neoecoae.block.ItemBlockModelDrive;
import cn.dancingsnow.neoecoae.block.ItemBlockModernModel;
import cn.dancingsnow.neoecoae.block.ItemBlockTooltip;
import cn.dancingsnow.neoecoae.block.NEBlock;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
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
    public static final Block craftingCasing = formedTexturedMachineBlock(
        "crafting_casing",
        NeoECOAE.MODID + ":crafting/casing",
        NeoECOAE.MODID + ":crafting/casing_formed",
        5.0F,
        10.0F,
        2);
    public static final Block storageVent = directionalModelBlock(
        "storage_vent",
        "storage_vent",
        new String[] { NeoECOAE.MODID + ":block/storage/casing", NeoECOAE.MODID + ":block/storage/casing_back",
            NeoECOAE.MODID + ":block/storage/vents_north", NeoECOAE.MODID + ":block/storage/casing_side" });
    public static final Block storageInterface = interfaceBlock(
        "storage_interface",
        "storage_interface",
        new String[] { NeoECOAE.MODID + ":block/storage/me_bus", NeoECOAE.MODID + ":block/storage/me_bus_light" },
        ECOControllerSubsystem.STORAGE);
    public static final Block inputHatch = craftingHatch(
        "input_hatch",
        NeoECOAE.MODID + ":crafting/hatch_input",
        NeoECOAE.MODID + ":crafting/hatch_input_formed",
        true);
    public static final Block outputHatch = craftingHatch(
        "output_hatch",
        NeoECOAE.MODID + ":crafting/hatch_output",
        NeoECOAE.MODID + ":crafting/hatch_output_formed",
        false);
    public static final Block craftingVent = registerModernModelBlock(
        new BlockFormedDirectionalModernModel(
            "crafting_vent",
            "crafting_vent",
            "crafting_vent_formed",
            new String[] { NeoECOAE.MODID + ":block/crafting/casing", NeoECOAE.MODID + ":block/crafting/casing_back",
                NeoECOAE.MODID + ":block/crafting/casing_side", NeoECOAE.MODID + ":block/crafting/vent_north",
                NeoECOAE.MODID + ":block/crafting/casing_formed",
                NeoECOAE.MODID + ":block/crafting/vent_north_formed" }));
    public static final Block craftingPatternBus = registerModernModelBlock(
        new BlockCraftingPatternBus(
            "crafting_pattern_bus",
            "crafting_pattern_bus",
            "crafting_pattern_bus_formed",
            new String[] { NeoECOAE.MODID + ":block/crafting/casing", NeoECOAE.MODID + ":block/crafting/casing_side",
                NeoECOAE.MODID + ":block/crafting/pattern_bus", NeoECOAE.MODID + ":block/crafting/casing_back",
                NeoECOAE.MODID + ":block/crafting/casing_formed", NeoECOAE.MODID + ":block/crafting/pattern_bus_formed",
                NeoECOAE.MODID + ":block/crafting/pattern_bus_formed_light" }));
    public static final Block craftingWorker = registerModernModelBlock(
        new BlockCraftingWorker(
            "crafting_worker",
            "crafting_worker",
            "crafting_worker_formed",
            new String[] { NeoECOAE.MODID + ":block/crafting/core/working_core_north",
                NeoECOAE.MODID + ":block/crafting/core/working_core_north_formed",
                NeoECOAE.MODID + ":block/crafting/core/working_core_light",
                NeoECOAE.MODID + ":block/crafting/core/working_core_light_on",
                NeoECOAE.MODID + ":block/crafting/casing", NeoECOAE.MODID + ":block/crafting/casing_formed",
                NeoECOAE.MODID + ":block/crafting/casing_back", NeoECOAE.MODID + ":block/crafting/core/core_side" }));
    public static final Block craftingInterface = interfaceBlock(
        "crafting_interface",
        "crafting_interface",
        new String[] { NeoECOAE.MODID + ":block/crafting/me_bus", NeoECOAE.MODID + ":block/crafting/me_bus_light" },
        ECOControllerSubsystem.CRAFTING);
    public static final Block ecoDrive = new BlockEcoDrive();
    public static final Block computationDrive = new BlockComputationDrive();
    public static final Block computationInterface = interfaceBlock(
        "computation_interface",
        "computation_interface",
        new String[] { NeoECOAE.MODID + ":block/compute/me_bus", NeoECOAE.MODID + ":block/compute/me_bus_light" },
        ECOControllerSubsystem.COMPUTATION);
    public static final Block computationTransmitter = registerModernModelBlock(
        new BlockComputationTransmitter(
            "computation_transmitter",
            "computation_transmitter",
            "computation_transmitter_formed",
            new String[] { NeoECOAE.MODID + ":block/compute/cable/plug_off",
                NeoECOAE.MODID + ":block/compute/casing_back", NeoECOAE.MODID + ":block/compute/casing",
                NeoECOAE.MODID + ":block/compute/transmitter/transmitter_north",
                NeoECOAE.MODID + ":block/compute/transmitter/transmitter_north_glass",
                NeoECOAE.MODID + ":block/compute/transmitter/transmitter_side_east",
                NeoECOAE.MODID + ":block/compute/transmitter/transmitter_side_west",
                NeoECOAE.MODID + ":block/compute/coolant", NeoECOAE.MODID + ":block/compute/cable/plug_a",
                NeoECOAE.MODID + ":block/compute/cable/plug_b", NeoECOAE.MODID + ":block/compute/cable/plug_c",
                NeoECOAE.MODID + ":block/compute/cable/cable_a", NeoECOAE.MODID + ":block/compute/cable/cable_b",
                NeoECOAE.MODID + ":block/compute/cable/cable_c" }));
    public static final Block computationCoolingControllerL4 = computationCoolingController("l4", "controller_east_a");
    public static final Block computationCoolingControllerL6 = computationCoolingController("l6", "controller_east_b");
    public static final Block computationCoolingControllerL9 = computationCoolingController("l9", "controller_east_c");
    public static final Block energyCellL4 = energyCell("l4", "a");
    public static final Block energyCellL6 = energyCell("l6", "b");
    public static final Block energyCellL9 = energyCell("l9", "c");
    public static final Block craftingParallelCoreL4 = craftingParallelCore("l4", "a");
    public static final Block craftingParallelCoreL6 = craftingParallelCore("l6", "b");
    public static final Block craftingParallelCoreL9 = craftingParallelCore("l9", "c");
    public static final Block computationParallelCoreL4 = computationParallelCore("l4", "a");
    public static final Block computationParallelCoreL6 = computationParallelCore("l6", "b");
    public static final Block computationParallelCoreL9 = computationParallelCore("l9", "c");
    public static final Block computationThreadingCoreL4 = computationThreadingCore("l4", "a");
    public static final Block computationThreadingCoreL6 = computationThreadingCore("l6", "b");
    public static final Block computationThreadingCoreL9 = computationThreadingCore("l9", "c");
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
        GameRegistry.registerBlock(storageCasing, ItemBlockTooltip.class, "storage_casing");
        GameRegistry.registerBlock(computationCasing, ItemBlockTooltip.class, "computation_casing");
        GameRegistry.registerBlock(craftingCasing, ItemBlockTooltip.class, "crafting_casing");
        GameRegistry.registerBlock(storageVent, ItemBlockModernModel.class, "storage_vent");
        GameRegistry.registerBlock(storageInterface, ItemBlockModernModel.class, "storage_interface");
        GameRegistry.registerBlock(inputHatch, ItemBlockTooltip.class, "input_hatch");
        GameRegistry.registerBlock(outputHatch, ItemBlockTooltip.class, "output_hatch");
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
        GameRegistry.registerBlock(energyCellL4, ItemBlockModernModel.class, "energy_cell_l4");
        GameRegistry.registerBlock(energyCellL6, ItemBlockModernModel.class, "energy_cell_l6");
        GameRegistry.registerBlock(energyCellL9, ItemBlockModernModel.class, "energy_cell_l9");
        GameRegistry.registerBlock(craftingParallelCoreL4, ItemBlockModernModel.class, "crafting_parallel_core_l4");
        GameRegistry.registerBlock(craftingParallelCoreL6, ItemBlockModernModel.class, "crafting_parallel_core_l6");
        GameRegistry.registerBlock(craftingParallelCoreL9, ItemBlockModernModel.class, "crafting_parallel_core_l9");
        GameRegistry
            .registerBlock(computationParallelCoreL4, ItemBlockModernModel.class, "computation_parallel_core_l4");
        GameRegistry
            .registerBlock(computationParallelCoreL6, ItemBlockModernModel.class, "computation_parallel_core_l6");
        GameRegistry
            .registerBlock(computationParallelCoreL9, ItemBlockModernModel.class, "computation_parallel_core_l9");
        GameRegistry
            .registerBlock(computationThreadingCoreL4, ItemBlockModernModel.class, "computation_threading_core_l4");
        GameRegistry
            .registerBlock(computationThreadingCoreL6, ItemBlockModernModel.class, "computation_threading_core_l6");
        GameRegistry
            .registerBlock(computationThreadingCoreL9, ItemBlockModernModel.class, "computation_threading_core_l9");
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

    private static Block formedTexturedMachineBlock(String id, String texture, String formedTexture, float hardness,
        float resistance, int harvestLevel) {
        Block block = new BlockFormedTexturedMachine(texture, formedTexture).setBlockName(id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(hardness)
            .setResistance(resistance)
            .setStepSound(Block.soundTypeMetal);
        block.setHarvestLevel("pickaxe", harvestLevel);
        return block;
    }

    private static Block craftingHatch(String id, String texture, String formedTexture, boolean input) {
        Block block = new BlockCraftingHatch(texture, formedTexture, input).setBlockName(id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(5.0F)
            .setResistance(10.0F)
            .setStepSound(Block.soundTypeMetal);
        block.setHarvestLevel("pickaxe", 2);
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

    private static Block interfaceBlock(String id, String modelName, String[] textures,
        ECOControllerSubsystem subsystem) {
        return registerModernModelBlock(new BlockECOInterface(id, modelName, textures, subsystem));
    }

    private static Block computationCoolingController(String tier, String eastTexture) {
        return registerModernModelBlock(
            new BlockComputationCoolingController(
                "computation_cooling_controller_" + tier,
                "computation_cooling_controller/controller_" + tier + "_off",
                "computation_cooling_controller/controller_" + tier + "_formed",
                "computation_cooling_controller/controller_" + tier + "_formed_mirrored",
                new String[] { NeoECOAE.MODID + ":block/compute/cooling_controller/" + eastTexture,
                    NeoECOAE.MODID + ":block/compute/cooling_controller/controller_north",
                    NeoECOAE.MODID + ":block/compute/casing_back", NeoECOAE.MODID + ":block/compute/casing",
                    NeoECOAE.MODID + ":block/compute/transmitter/transmitter_side_east",
                    NeoECOAE.MODID + ":block/compute/coolant",
                    NeoECOAE.MODID + ":block/compute/cooling_controller/screen_on",
                    NeoECOAE.MODID + ":block/compute/cooling_controller/" + coolingControllerFormedTexture(tier) },
                ModelFacing.WEST,
                controllerTier(tier)));
    }

    private static Block energyCell(String tier, String suffix) {
        return registerModernModelBlock(
            new BlockTieredModernModel(
                "energy_cell_" + tier,
                "storage_energy_cell/cell_" + tier + "_4",
                new String[] { NeoECOAE.MODID + ":block/storage/casing_back", NeoECOAE.MODID + ":block/storage/casing",
                    NeoECOAE.MODID + ":block/storage/energy_cell/cell_side_" + suffix,
                    NeoECOAE.MODID + ":block/storage/energy_cell/cell_north_layer_" + suffix,
                    NeoECOAE.MODID + ":block/storage/energy_cell/cell_north_4",
                    NeoECOAE.MODID + ":block/storage/energy_cell/cell_north_light_4" },
                controllerTier(tier)));
    }

    private static Block craftingParallelCore(String tier, String suffix) {
        return registerModernModelBlock(
            new BlockTieredModernModel(
                "crafting_parallel_core_" + tier,
                "crafting_core/parallel_core_" + tier,
                "crafting_core/parallel_core_" + tier + "_formed",
                new String[] { NeoECOAE.MODID + ":block/crafting/core/parallel_core_north",
                    NeoECOAE.MODID + ":block/crafting/casing", NeoECOAE.MODID + ":block/crafting/casing_back",
                    NeoECOAE.MODID + ":block/crafting/casing_formed",
                    NeoECOAE.MODID + ":block/crafting/core/core_side_" + suffix,
                    NeoECOAE.MODID + ":block/crafting/core/parallel_core_light_" + suffix,
                    NeoECOAE.MODID + ":block/crafting/core/parallel_core_light_" + suffix + "_on",
                    NeoECOAE.MODID + ":block/crafting/core/parallel_core_north_formed" },
                controllerTier(tier)));
    }

    private static Block computationParallelCore(String tier, String suffix) {
        return registerModernModelBlock(
            new BlockTieredModernModel(
                "computation_parallel_core_" + tier,
                "computation_core/parallel_core_" + tier,
                "computation_core/parallel_core_" + tier + "_formed",
                new String[] { NeoECOAE.MODID + ":block/compute/core/parallel_core_north",
                    NeoECOAE.MODID + ":block/compute/casing", NeoECOAE.MODID + ":block/compute/casing_back",
                    NeoECOAE.MODID + ":block/compute/core/core_side_" + suffix,
                    NeoECOAE.MODID + ":block/compute/core/parallel_core_light_" + suffix,
                    NeoECOAE.MODID + ":block/compute/core/parallel_core_light_" + suffix + "_on" },
                controllerTier(tier)));
    }

    private static Block computationThreadingCore(String tier, String suffix) {
        return registerModernModelBlock(
            new BlockTieredModernModel(
                "computation_threading_core_" + tier,
                "computation_core/threading_core_" + tier,
                "computation_core/threading_core_" + tier + "_formed",
                new String[] { NeoECOAE.MODID + ":block/compute/core/threading_core_north",
                    NeoECOAE.MODID + ":block/compute/casing", NeoECOAE.MODID + ":block/compute/casing_back",
                    NeoECOAE.MODID + ":block/compute/core/core_side_" + suffix,
                    NeoECOAE.MODID + ":block/compute/core/threading_core_light_" + suffix,
                    NeoECOAE.MODID + ":block/compute/core/threading_core_light_" + suffix + "_on",
                    NeoECOAE.MODID + ":block/compute/core/threading_core_light_" + suffix + "_working" },
                controllerTier(tier)));
    }

    private static Block computationSystem(String tier, String levelTexture) {
        return registerModernModelBlock(
            new BlockECOController(
                "computation_system_" + tier,
                "computation_controller/controller_" + tier + "_off",
                "computation_controller/controller_" + tier + "_formed",
                new String[] { NeoECOAE.MODID + ":block/compute/casing",
                    NeoECOAE.MODID + ":block/compute/controller/controller_north",
                    NeoECOAE.MODID + ":block/compute/controller/controller_side",
                    NeoECOAE.MODID + ":block/compute/casing_back",
                    NeoECOAE.MODID + ":block/compute/controller/controller_top",
                    NeoECOAE.MODID + ":block/compute/controller/screen_off",
                    NeoECOAE.MODID + ":block/compute/controller/" + levelTexture,
                    NeoECOAE.MODID + ":block/compute/controller_formed/" + storageFormedTexture(tier),
                    NeoECOAE.MODID + ":block/compute/controller/" + screenOnTexture(tier),
                    NeoECOAE.MODID + ":block/compute/coolant" },
                ECOControllerSubsystem.COMPUTATION,
                controllerTier(tier)));
    }

    private static Block storageSystem(String tier, String northTexture, String sideTexture) {
        return registerModernModelBlock(
            new BlockECOController(
                "storage_system_" + tier,
                "storage_controller/controller_" + tier + "_off",
                "storage_controller/controller_" + tier + "_formed",
                new String[] { NeoECOAE.MODID + ":block/storage/controller/" + sideTexture,
                    NeoECOAE.MODID + ":block/storage/controller/" + northTexture,
                    NeoECOAE.MODID + ":block/storage/casing_back", NeoECOAE.MODID + ":block/storage/casing",
                    NeoECOAE.MODID + ":block/storage/controller/screen_off",
                    NeoECOAE.MODID + ":block/storage/controller_formed/" + storageFormedTexture(tier) },
                ECOControllerSubsystem.STORAGE,
                controllerTier(tier)));
    }

    private static Block craftingSystem(String tier, String northTexture, String sideTexture) {
        return registerModernModelBlock(
            new BlockECOController(
                "crafting_system_" + tier,
                "crafting_controller/controller_" + tier + "_off",
                "crafting_controller/controller_" + tier + "_formed",
                new String[] { NeoECOAE.MODID + ":block/crafting/controller/" + sideTexture,
                    NeoECOAE.MODID + ":block/crafting/controller/" + northTexture,
                    NeoECOAE.MODID + ":block/crafting/casing_back", NeoECOAE.MODID + ":block/crafting/casing",
                    NeoECOAE.MODID + ":block/crafting/controller/screen_off",
                    NeoECOAE.MODID + ":block/crafting/controller_formed/" + storageFormedTexture(tier),
                    NeoECOAE.MODID + ":block/crafting/controller/" + screenOnTexture(tier) },
                ECOControllerSubsystem.CRAFTING,
                controllerTier(tier)));
    }

    private static String storageFormedTexture(String tier) {
        if ("l4".equals(tier)) {
            return "controller_formed_a";
        }
        if ("l6".equals(tier)) {
            return "controller_formed_b";
        }
        return "controller_formed_c";
    }

    private static String coolingControllerFormedTexture(String tier) {
        if ("l4".equals(tier)) {
            return "controller_a_formed";
        }
        if ("l6".equals(tier)) {
            return "controller_b_formed";
        }
        return "controller_c_formed";
    }

    private static String screenOnTexture(String tier) {
        if ("l4".equals(tier)) {
            return "screen_on_a";
        }
        if ("l6".equals(tier)) {
            return "screen_on_b";
        }
        return "screen_on_c";
    }

    private static ECOControllerTier controllerTier(String tier) {
        return ECOControllerTier.fromId(tier);
    }

    private static Block registerModernModelBlock(BlockModernModel block) {
        MODERN_MODEL_BLOCKS.add(block);
        return block;
    }

    private static void register(Block block, String id) {
        GameRegistry.registerBlock(block, id);
    }
}
