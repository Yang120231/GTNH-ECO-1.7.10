package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import appeng.api.AEApi;
import appeng.api.features.InscriberProcessType;
import cn.dancingsnow.neoecoae.integration.gregtech.NEGregTechRecipes;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NERecipes {

    private static final int IWS_ENERGIZED_CRYSTAL_ENERGY = 62500;
    private static final int IWS_ECO_COMPONENT_16M_ENERGY = 16000;
    private static final int IWS_ECO_COMPONENT_64M_ENERGY = 48000;
    private static final int IWS_ECO_COMPONENT_256M_ENERGY = 144000;
    private static final int IWS_ECO_COMPUTATION_CELL_L4_ENERGY = 64000;
    private static final int IWS_ECO_COMPUTATION_CELL_L6_ENERGY = 256000;
    private static final int IWS_ECO_COMPUTATION_CELL_L9_ENERGY = 1024000;
    private static final int IWS_SYSTEM_L4_ENERGY = 16000;
    private static final int IWS_SYSTEM_L6_ENERGY = 160000;
    private static final int IWS_SYSTEM_L9_ENERGY = 640000;
    private static final int IWS_ADVANCED_INGOT_ENERGY = 200000;

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
        storageBlock4("gemEnergizedCrystal", NEBlocks.energizedCrystalBlock, NEItems.energizedCrystal);
        storageBlock4("gemEnergizedFluixCrystal", NEBlocks.energizedFluixCrystalBlock, NEItems.energizedFluixCrystal);
        storageBlock(
            "ingotEnergizedSuperconductive",
            NEBlocks.energizedSuperconductiveBlock,
            NEItems.energizedSuperconductiveIngot);

        shapedIfComplete(
            NEBlocks.aluminumAlloyCasing,
            "ABA",
            "BCB",
            "ABA",
            'A',
            "ingotAluminumAlloy",
            'B',
            NEAE2RecipeItems.quartzVibrantGlass(),
            'C',
            NEItems.crystalIngot);
        shapedIfComplete(
            NEBlocks.blackTungstenAlloyCasing,
            "ABA",
            "BCB",
            "ABA",
            'A',
            "ingotBlackTungstenAlloy",
            'B',
            NEAE2RecipeItems.quartzVibrantGlass(),
            'C',
            NEItems.crystalIngot);

        registerMaterialItemRecipes();
        registerMachineBlockRecipes();
        registerStorageItemRecipes();
        registerComputationItemRecipes();
        registerCoolingItemRecipes();

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

    public static void registerPostInit() {
        if (useGregTechRecipes()) {
            NEGregTechRecipes.register();
        }
    }

    private static void registerMaterialItemRecipes() {
        // IWS parity: energized crystal / fluix recipes use 62,500 AE in 1.21.1.
        shapedIfComplete(
            new ItemStack(NEItems.energizedCrystal, 8),
            "ABA",
            "BCB",
            "ABA",
            'A',
            NEAE2RecipeItems.certusQuartzCrystalCharged(),
            'B',
            "dustEnergizedCrystal",
            'C',
            Items.water_bucket);
        shapelessIfComplete(NEItems.energizedCrystalDust, NEItems.energizedCrystal);
        registerInscriber(
            NEItems.energizedCrystalDust,
            InscriberProcessType.Inscribe,
            NEItems.energizedCrystal,
            null,
            null);

        shapedIfComplete(
            new ItemStack(NEItems.energizedFluixCrystal, 8),
            "ABA",
            "BCB",
            "ABA",
            'A',
            "dustEnergizedCrystal",
            'B',
            NEAE2RecipeItems.fluixCrystal(),
            'C',
            Items.water_bucket);
        shapeless(new ItemStack(NEItems.energizedFluixCrystal, 4), NEBlocks.energizedFluixCrystalBlock);
        shapelessIfComplete(NEItems.energizedFluixCrystalDust, NEItems.energizedFluixCrystal);
        registerInscriber(
            NEItems.energizedFluixCrystalDust,
            InscriberProcessType.Inscribe,
            NEItems.energizedFluixCrystal,
            null,
            null);

        // IWS parity: crystal / superconductive ingots cost 200,000 AE per four outputs in 1.21.1.
        shapedIfComplete(
            new ItemStack(NEItems.crystalIngot, 4),
            "ABC",
            "DDD",
            "CBA",
            'A',
            NEAE2RecipeItems.certusQuartzDust(),
            'B',
            NEAE2RecipeItems.fluixDust(),
            'C',
            "dustEnergizedCrystal",
            'D',
            "ingotAluminumAlloy");
        shaped(NEItems.crystalMatrix, "A A", " A ", "A A", 'A', NEItems.crystalIngot);
        shapedIfComplete(
            new ItemStack(NEItems.energizedSuperconductiveIngot, 4),
            "ABC",
            "DDD",
            "CBA",
            'A',
            "dustEnergizedFluixCrystal",
            'B',
            "dustAluminum",
            'C',
            NEAE2RecipeItems.silicon(),
            'D',
            "ingotBlackTungstenAlloy");
        shapedIfComplete(
            NEItems.superconductingProcessorPress,
            "AAA",
            "BCD",
            "AAA",
            'A',
            NEItems.energizedSuperconductiveIngot,
            'B',
            NEAE2RecipeItems.engineeringProcessorPress(),
            'C',
            NEAE2RecipeItems.calculationProcessorPress(),
            'D',
            NEAE2RecipeItems.logicProcessorPress());
        shaped(
            NEItems.superconductingProcessorPrint,
            " A ",
            "ABA",
            " A ",
            'A',
            NEItems.energizedSuperconductiveIngot,
            'B',
            NEItems.superconductingProcessorPress);
        shapedIfComplete(
            NEItems.superconductingProcessor,
            " A ",
            "BCD",
            " A ",
            'A',
            "dustRedstone",
            'B',
            NEItems.superconductingProcessorPrint,
            'C',
            NEItems.crystalMatrix,
            'D',
            NEAE2RecipeItems.siliconPrint());
        registerInscriber(
            NEItems.superconductingProcessorPrint,
            InscriberProcessType.Inscribe,
            NEItems.energizedSuperconductiveIngot,
            NEItems.superconductingProcessorPress,
            null);
        registerInscriber(
            NEItems.superconductingProcessor,
            InscriberProcessType.Press,
            NEItems.crystalMatrix,
            NEItems.superconductingProcessorPrint,
            NEAE2RecipeItems.siliconPrint());
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

        shapedIfComplete(
            NEBlocks.storageInterface,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.storageCasing,
            'B',
            NEAE2RecipeItems.logicProcessor(),
            'C',
            NEAE2RecipeItems.singularity(),
            'D',
            NEAE2RecipeItems.interfaceBlock());
        shapedIfComplete(
            NEBlocks.craftingInterface,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.craftingCasing,
            'B',
            NEAE2RecipeItems.engineeringProcessor(),
            'C',
            NEAE2RecipeItems.singularity(),
            'D',
            NEAE2RecipeItems.interfaceBlock());
        shapedIfComplete(
            NEBlocks.computationInterface,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.computationCasing,
            'B',
            NEAE2RecipeItems.calculationProcessor(),
            'C',
            NEAE2RecipeItems.singularity(),
            'D',
            NEAE2RecipeItems.interfaceBlock());

        shapedIfComplete(
            NEBlocks.ecoDrive,
            "ADA",
            "BCB",
            "ADA",
            'A',
            NEBlocks.storageCasing,
            'B',
            NEAE2RecipeItems.smartDenseCable(),
            'C',
            NEAE2RecipeItems.drive(),
            'D',
            NEAE2RecipeItems.logicProcessor());
        shapedIfComplete(
            NEBlocks.computationDrive,
            "ABA",
            "CDC",
            "AEA",
            'A',
            NEBlocks.computationCasing,
            'B',
            NEAE2RecipeItems.craftingMonitor(),
            'C',
            NEAE2RecipeItems.craftingUnit(),
            'D',
            NEAE2RecipeItems.patternProvider(),
            'E',
            NEAE2RecipeItems.calculationProcessor());

        shapedIfComplete(
            NEBlocks.craftingPatternBus,
            "ABA",
            "BCB",
            "ADA",
            'A',
            NEBlocks.craftingCasing,
            'B',
            NEAE2RecipeItems.patternProvider(),
            'C',
            NEAE2RecipeItems.interfaceBlock(),
            'D',
            NEAE2RecipeItems.engineeringProcessor());
        shapedIfComplete(
            NEBlocks.craftingWorker,
            "ABA",
            "CDC",
            "AEA",
            'A',
            NEAE2RecipeItems.craftingStorage256k(),
            'B',
            NEAE2RecipeItems.interfaceBlock(),
            'C',
            NEAE2RecipeItems.controller(),
            'D',
            NEBlocks.craftingCasing,
            'E',
            NEBlocks.craftingVent);
        shapedIfComplete(
            NEBlocks.computationTransmitter,
            "ABA",
            "CDC",
            "AEA",
            'A',
            NEBlocks.computationCasing,
            'B',
            NEItems.energizedSuperconductiveIngot,
            'C',
            NEAE2RecipeItems.coveredDenseCable(),
            'D',
            NEAE2RecipeItems.interfaceBlock(),
            'E',
            NEAE2RecipeItems.calculationProcessor());

        registerStorageSystemRecipes();
        registerCraftingSystemRecipes();
        registerComputationSystemRecipes();
        registerComputationCoolingControllerRecipes();
        registerEnergyCellRecipes();
        registerCraftingCoreRecipes();
        registerComputationCoreRecipes();
    }

    private static void registerStorageSystemRecipes() {
        if (useGregTechRecipes()) {
            return;
        }
        // IWS parity: L4/L6/L9 systems cost 16,000 / 160,000 / 640,000 AE in 1.21.1.
        shapedIfComplete(
            NEBlocks.storageSystemL4,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.storageCasing,
            'B',
            NEAE2RecipeItems.drive(),
            'C',
            NEItems.energizedSuperconductiveIngot,
            'D',
            NEItems.superconductingProcessor);
        shapedIfComplete(
            NEBlocks.storageSystemL6,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.storageSystemL4,
            'B',
            NEAE2RecipeItems.drive(),
            'C',
            NEBlocks.energizedSuperconductiveBlock,
            'D',
            NEItems.superconductingProcessor);
        shapedIfComplete(
            NEBlocks.storageSystemL9,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.storageSystemL6,
            'B',
            NEAE2RecipeItems.drive(),
            'C',
            NEBlocks.energizedSuperconductiveBlock,
            'D',
            NEItems.superconductingProcessor);
    }

    private static void registerCraftingSystemRecipes() {
        if (useGregTechRecipes()) {
            return;
        }
        // IWS parity: L4/L6/L9 systems cost 16,000 / 160,000 / 640,000 AE in 1.21.1.
        shaped(
            NEBlocks.craftingSystemL4,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.craftingCasing,
            'B',
            NEBlocks.craftingParallelCoreL4,
            'C',
            NEItems.energizedSuperconductiveIngot,
            'D',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.craftingSystemL6,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.craftingSystemL4,
            'B',
            NEBlocks.craftingParallelCoreL6,
            'C',
            NEBlocks.energizedSuperconductiveBlock,
            'D',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.craftingSystemL9,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.craftingSystemL6,
            'B',
            NEBlocks.craftingParallelCoreL9,
            'C',
            NEBlocks.energizedSuperconductiveBlock,
            'D',
            NEItems.superconductingProcessor);
    }

    private static void registerComputationSystemRecipes() {
        if (useGregTechRecipes()) {
            return;
        }
        // IWS parity: L4/L6/L9 systems cost 16,000 / 160,000 / 640,000 AE in 1.21.1.
        shaped(
            NEBlocks.computationSystemL4,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.computationCasing,
            'B',
            NEBlocks.computationParallelCoreL4,
            'C',
            NEItems.energizedSuperconductiveIngot,
            'D',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.computationSystemL6,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.computationSystemL4,
            'B',
            NEBlocks.computationParallelCoreL6,
            'C',
            NEBlocks.energizedSuperconductiveBlock,
            'D',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.computationSystemL9,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.computationSystemL6,
            'B',
            NEBlocks.computationParallelCoreL9,
            'C',
            NEBlocks.energizedSuperconductiveBlock,
            'D',
            NEItems.superconductingProcessor);
    }

    private static void registerComputationCoolingControllerRecipes() {
        shaped(
            NEBlocks.computationCoolingControllerL4,
            "ABA",
            "BCB",
            "ABA",
            'A',
            Blocks.ice,
            'B',
            NEBlocks.computationCasing,
            'C',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.computationCoolingControllerL6,
            "ABA",
            "DCD",
            "ABA",
            'A',
            NEItems.cryotheumCrystal,
            'B',
            NEItems.crystalIngot,
            'C',
            NEBlocks.computationCoolingControllerL4,
            'D',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.computationCoolingControllerL9,
            "ABA",
            "DCD",
            "ABA",
            'A',
            NEItems.cryotheumCrystal,
            'B',
            NEItems.energizedSuperconductiveIngot,
            'C',
            NEBlocks.computationCoolingControllerL6,
            'D',
            NEItems.superconductingProcessor);
    }

    private static void registerStorageItemRecipes() {
        shapedIfComplete(
            NEStorageItems.ecoItemCellHousing,
            "ABA",
            "B B",
            "CCC",
            'A',
            NEItems.crystalMatrix,
            'B',
            "dustRedstone",
            'C',
            "ingotAluminum");
        if (useGregTechRecipes()) {
            registerStorageCellAssemblyRecipes();
            return;
        }
        // IWS parity: 16M / 64M / 256M components cost 16,000 / 48,000 / 144,000 AE in 1.21.1.
        shapedIfComplete(
            NEStorageItems.ecoCellComponent16M,
            "ABA",
            "CDC",
            "AEA",
            'A',
            NEBlocks.energizedSuperconductiveBlock,
            'B',
            NEAE2RecipeItems.cellComponent256k(),
            'C',
            NEItems.superconductingProcessor,
            'D',
            NEItems.crystalIngot,
            'E',
            NEAE2RecipeItems.cellComponent256k());
        shaped(
            NEStorageItems.ecoCellComponent64M,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.energizedSuperconductiveBlock,
            'B',
            NEStorageItems.ecoCellComponent16M,
            'C',
            NEItems.superconductingProcessor,
            'D',
            NEItems.crystalIngot);
        shapedIfComplete(
            NEStorageItems.ecoCellComponent256M,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.energizedSuperconductiveBlock,
            'B',
            NEStorageItems.ecoCellComponent64M,
            'C',
            NEItems.superconductingProcessor,
            'D',
            NEItems.crystalIngot);
        shapeless(
            NEStorageItems.ecoItemStorageCell16M,
            NEStorageItems.ecoItemCellHousing,
            NEStorageItems.ecoCellComponent16M);
        shapeless(
            NEStorageItems.ecoItemStorageCell64M,
            NEStorageItems.ecoItemCellHousing,
            NEStorageItems.ecoCellComponent64M);
        shapeless(
            NEStorageItems.ecoItemStorageCell256M,
            NEStorageItems.ecoItemCellHousing,
            NEStorageItems.ecoCellComponent256M);
        shaped(
            NEStorageItems.ecoInfiniteCellComponent,
            "ABA",
            "BCB",
            "ABA",
            'A',
            NEStorageItems.ecoCellComponent256M,
            'B',
            NEItems.energizedSuperconductiveIngot,
            'C',
            NEBlocks.energizedFluixCrystalBlock);
    }

    private static void registerStorageCellAssemblyRecipes() {
        shapeless(
            NEStorageItems.ecoItemStorageCell16M,
            NEStorageItems.ecoItemCellHousing,
            NEStorageItems.ecoCellComponent16M);
        shapeless(
            NEStorageItems.ecoItemStorageCell64M,
            NEStorageItems.ecoItemCellHousing,
            NEStorageItems.ecoCellComponent64M);
        shapeless(
            NEStorageItems.ecoItemStorageCell256M,
            NEStorageItems.ecoItemCellHousing,
            NEStorageItems.ecoCellComponent256M);
    }

    private static void registerComputationItemRecipes() {
        if (useGregTechRecipes()) {
            return;
        }
        // IWS parity: CE4 / CE6 / CE9 cost 64,000 / 256,000 / 1,024,000 AE in 1.21.1.
        shaped(
            NEStorageItems.ecoComputationCellL4,
            "ABA",
            "CDC",
            "AEA",
            'A',
            NEBlocks.energizedSuperconductiveBlock,
            'B',
            NEStorageItems.ecoCellComponent16M,
            'C',
            NEItems.superconductingProcessor,
            'D',
            NEItems.crystalMatrix,
            'E',
            NEStorageItems.ecoCellComponent16M);
        shaped(
            NEStorageItems.ecoComputationCellL6,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.energizedSuperconductiveBlock,
            'B',
            NEStorageItems.ecoCellComponent64M,
            'C',
            NEItems.superconductingProcessor,
            'D',
            NEItems.crystalMatrix);
        shapedIfComplete(
            NEStorageItems.ecoComputationCellL9,
            "ABA",
            "CDC",
            "ABA",
            'A',
            NEBlocks.energizedSuperconductiveBlock,
            'B',
            NEStorageItems.ecoCellComponent256M,
            'C',
            NEItems.superconductingProcessor,
            'D',
            NEItems.crystalMatrix);
    }

    private static void registerCoolingItemRecipes() {
        if (useGregTechRecipes()) {
            return;
        }
        shapelessIfComplete(
            NEItems.cryotheum,
            Blocks.ice,
            NEAE2RecipeItems.certusQuartzDust(),
            NEAE2RecipeItems.skyDust(),
            Items.snowball,
            NEItems.energizedCrystalDust,
            NEItems.energizedCrystalDust,
            NEItems.energizedCrystalDust,
            NEItems.energizedCrystalDust);
        shapedIfComplete(
            NEItems.cryotheumCrystal,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEAE2RecipeItems.skyDust(),
            'B',
            NEItems.cryotheum);
    }

    private static void registerEnergyCellRecipes() {
        shapedIfComplete(
            NEBlocks.energyCellL4,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEAE2RecipeItems.denseEnergyCell(),
            'B',
            NEBlocks.storageCasing);
        shaped(
            NEBlocks.energyCellL6,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEBlocks.energyCellL4,
            'B',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.energyCellL9,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEBlocks.energyCellL6,
            'B',
            NEItems.superconductingProcessor);
    }

    private static void registerCraftingCoreRecipes() {
        shapedIfComplete(
            NEBlocks.craftingParallelCoreL4,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEAE2RecipeItems.craftingAccelerator(),
            'B',
            NEBlocks.craftingCasing);
        shaped(
            NEBlocks.craftingParallelCoreL6,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEBlocks.craftingParallelCoreL4,
            'B',
            NEItems.superconductingProcessor);
        shaped(
            NEBlocks.craftingParallelCoreL9,
            "AAA",
            "ABA",
            "AAA",
            'A',
            NEBlocks.craftingParallelCoreL6,
            'B',
            NEItems.superconductingProcessor);
    }

    private static void registerComputationCoreRecipes() {
        shapedIfComplete(
            NEBlocks.computationParallelCoreL4,
            "ABA",
            "ACA",
            "ABA",
            'A',
            NEAE2RecipeItems.craftingAccelerator(),
            'B',
            NEItems.superconductingProcessor,
            'C',
            NEBlocks.computationCasing);
        shaped(
            NEBlocks.computationParallelCoreL6,
            "ABA",
            "ACA",
            "ABA",
            'A',
            NEBlocks.computationParallelCoreL4,
            'B',
            NEItems.superconductingProcessor,
            'C',
            NEBlocks.computationCasing);
        shaped(
            NEBlocks.computationParallelCoreL9,
            "ABA",
            "ACA",
            "ABA",
            'A',
            NEBlocks.computationParallelCoreL6,
            'B',
            NEItems.superconductingProcessor,
            'C',
            NEBlocks.computationCasing);
        shapedIfComplete(
            NEBlocks.computationThreadingCoreL4,
            "ABA",
            "ACA",
            "ABA",
            'A',
            NEAE2RecipeItems.craftingStorage256k(),
            'B',
            NEItems.superconductingProcessor,
            'C',
            NEBlocks.computationCasing);
        shaped(
            NEBlocks.computationThreadingCoreL6,
            "ABA",
            "ACA",
            "ABA",
            'A',
            NEBlocks.computationThreadingCoreL4,
            'B',
            NEItems.superconductingProcessor,
            'C',
            NEBlocks.computationCasing);
        shaped(
            NEBlocks.computationThreadingCoreL9,
            "ABA",
            "ACA",
            "ABA",
            'A',
            NEBlocks.computationThreadingCoreL6,
            'B',
            NEItems.superconductingProcessor,
            'C',
            NEBlocks.computationCasing);
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

    private static void storageBlock4(Object material, Block block, Item item) {
        shaped(block, "AA", "AA", 'A', material);
        shapeless(new ItemStack(item, 4), block);
    }

    private static void shaped(Item output, Object... recipe) {
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(output), recipe));
    }

    private static void shaped(Block output, Object... recipe) {
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(output), recipe));
    }

    private static void shaped(ItemStack output, Object... recipe) {
        GameRegistry.addRecipe(new ShapedOreRecipe(output, recipe));
    }

    private static void shapedIfComplete(Item output, Object... recipe) {
        if (isComplete(recipe)) {
            shaped(output, recipe);
        }
    }

    private static void shapedIfComplete(Block output, Object... recipe) {
        if (isComplete(recipe)) {
            shaped(output, recipe);
        }
    }

    private static void shapedIfComplete(ItemStack output, Object... recipe) {
        if (output != null && isComplete(recipe)) {
            shaped(output, recipe);
        }
    }

    private static void shapeless(Item output, Object... recipe) {
        shapeless(new ItemStack(output), recipe);
    }

    private static void shapeless(ItemStack output, Object... recipe) {
        GameRegistry.addRecipe(new ShapelessOreRecipe(output, recipe));
    }

    private static void shapelessIfComplete(Item output, Object... recipe) {
        if (isComplete(recipe)) {
            shapeless(output, recipe);
        }
    }

    private static void shapelessIfComplete(ItemStack output, Object... recipe) {
        if (output != null && isComplete(recipe)) {
            shapeless(output, recipe);
        }
    }

    private static void registerInscriber(Item output, InscriberProcessType processType, Item input, Object topOptional,
        Object bottomOptional) {
        registerInscriber(
            new ItemStack(output),
            processType,
            new ItemStack(input),
            stack(topOptional),
            stack(bottomOptional));
    }

    private static void registerInscriber(ItemStack output, InscriberProcessType processType, ItemStack input,
        ItemStack topOptional, ItemStack bottomOptional) {
        if (output == null || input == null || processType == null || topOptional == null && bottomOptional == null) {
            return;
        }
        appeng.api.features.IInscriberRecipeBuilder builder = AEApi.instance()
            .registries()
            .inscriber()
            .builder()
            .withInputs(java.util.Collections.singleton(input))
            .withOutput(output)
            .withProcessType(processType);
        if (topOptional != null) {
            builder.withTopOptional(topOptional);
        }
        if (bottomOptional != null) {
            builder.withBottomOptional(bottomOptional);
        }
        AEApi.instance()
            .registries()
            .inscriber()
            .addRecipe(builder.build());
    }

    private static ItemStack stack(Object entry) {
        if (entry instanceof ItemStack) {
            return ((ItemStack) entry).copy();
        }
        if (entry instanceof Item) {
            return new ItemStack((Item) entry);
        }
        if (entry instanceof Block) {
            return new ItemStack((Block) entry);
        }
        return null;
    }

    private static boolean isComplete(Object[] recipe) {
        for (Object entry : recipe) {
            if (entry == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean useGregTechRecipes() {
        return Loader.isModLoaded("gregtech");
    }

    private static void smelt(Item input, Item output, float xp) {
        GameRegistry.addSmelting(input, new ItemStack(output), xp);
    }

    private static void smelt(Block input, Item output, float xp) {
        GameRegistry.addSmelting(input, new ItemStack(output), xp);
    }
}
