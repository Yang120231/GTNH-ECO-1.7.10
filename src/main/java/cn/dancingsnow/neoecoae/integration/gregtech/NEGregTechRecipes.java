package cn.dancingsnow.neoecoae.integration.gregtech;

import static gregtech.api.util.GTRecipeBuilder.BUCKETS;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.all.NEAE2RecipeItems;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEStorageItems;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.recipe.Scanning;

public final class NEGregTechRecipes {

    private NEGregTechRecipes() {}

    public static void register() {
        registerBaseMaterialRecipes();
        registerAlloyMaterialRecipes();
        registerCryotheumMixerRecipes();
        registerHighVersionMaterialRecipes();
        registerStorageComponentRecipes();
        registerComputationCellRecipes();
        registerSystemBlockRecipes();
        registerInfiniteComponentRecipe();
    }

    private static void registerBaseMaterialRecipes() {
        macerator(stack(NEItems.aluminumIngot), stack(NEItems.aluminumDust), 3 * SECONDS, TierEU.RECIPE_HV);
        macerator(stack(NEItems.tungstenIngot), stack(NEItems.tungstenDust), 4 * SECONDS, TierEU.RECIPE_HV);
    }

    private static void registerAlloyMaterialRecipes() {
        ItemStack certusDust = NEAE2RecipeItems.certusQuartzDust();
        ItemStack fluixDust = NEAE2RecipeItems.fluixDust();

        mixer(
            new ItemStack[] { ore("dustIron", 1), ore("dustAluminium", 1), copy(certusDust, 2) },
            null,
            new ItemStack[] { stack(NEItems.aluminumAlloyDust) },
            null,
            10 * SECONDS,
            TierEU.RECIPE_LV);
        mixer(
            new ItemStack[] { ore("dustTungsten", 1), stack(NEItems.aluminumAlloyDust), copy(fluixDust, 2) },
            null,
            new ItemStack[] { stack(NEItems.blackTungstenAlloyDust) },
            null,
            15 * SECONDS,
            TierEU.RECIPE_MV);

        macerator(stack(NEItems.aluminumAlloyIngot), stack(NEItems.aluminumAlloyDust), 10 * SECONDS, TierEU.RECIPE_LV);
        macerator(
            stack(NEItems.blackTungstenAlloyIngot),
            stack(NEItems.blackTungstenAlloyDust),
            10 * SECONDS,
            TierEU.RECIPE_MV);
    }

    private static void registerCryotheumMixerRecipes() {
        ItemStack certusDust = NEAE2RecipeItems.certusQuartzDust();
        ItemStack skyDust = NEAE2RecipeItems.skyDust();
        if (certusDust != null && skyDust != null) {
            mixer(
                new ItemStack[] { stack(Blocks.ice), copy(certusDust), copy(skyDust), stack(Items.snowball),
                    stack(NEItems.energizedCrystalDust, 4) },
                null,
                new ItemStack[] { stack(NEItems.cryotheum) },
                null,
                10 * SECONDS,
                TierEU.RECIPE_MV);
        }

        if (skyDust != null) {
            mixer(
                new ItemStack[] { stack(NEItems.cryotheum), copy(skyDust, 8) },
                null,
                new ItemStack[] { stack(NEItems.cryotheumCrystal) },
                null,
                12 * SECONDS,
                TierEU.RECIPE_HV);
        }

        FluidStack water = water(1 * BUCKETS);
        FluidStack cryotheumSolution = new FluidStack(NEFluids.cryotheumSolution(), 1 * BUCKETS);
        mixer(
            new ItemStack[] { stack(NEItems.cryotheumCrystal, 4), stack(NEItems.energizedCrystal, 2),
                ore("dustRedstone", 2) },
            water,
            null,
            new FluidStack[] { cryotheumSolution },
            8 * SECONDS,
            TierEU.RECIPE_HV);
    }

    private static void registerHighVersionMaterialRecipes() {
        ItemStack chargedCertus = NEAE2RecipeItems.certusQuartzCrystalCharged();
        ItemStack fluixCrystal = NEAE2RecipeItems.fluixCrystal();
        ItemStack certusDust = NEAE2RecipeItems.certusQuartzDust();
        ItemStack fluixDust = NEAE2RecipeItems.fluixDust();

        mixer(
            new ItemStack[] { copy(chargedCertus, 8) },
            water(250),
            new ItemStack[] { stack(NEItems.energizedCrystal, 8) },
            null,
            25 * SECONDS,
            TierEU.RECIPE_HV);
        macerator(stack(NEItems.energizedCrystal), stack(NEItems.energizedCrystalDust), 10 * SECONDS, TierEU.RECIPE_HV);
        mixer(
            new ItemStack[] { stack(NEItems.energizedCrystalDust, 8), copy(fluixCrystal, 8) },
            water(250),
            new ItemStack[] { stack(NEItems.energizedFluixCrystal, 8) },
            null,
            25 * SECONDS,
            TierEU.RECIPE_HV);
        macerator(
            stack(NEItems.energizedFluixCrystal),
            stack(NEItems.energizedFluixCrystalDust),
            10 * SECONDS,
            TierEU.RECIPE_HV);
        mixer(
            new ItemStack[] { copy(certusDust, 4), copy(fluixDust, 4), stack(NEItems.energizedCrystalDust, 4),
                stack(NEItems.aluminumAlloyIngot, 4) },
            FluidRegistry.getFluidStack("lava", 2 * BUCKETS),
            new ItemStack[] { stack(NEItems.crystalIngot, 4) },
            null,
            40 * SECONDS,
            TierEU.RECIPE_EV);
        mixer(
            new ItemStack[] { stack(NEItems.energizedFluixCrystalDust, 4), ore("dustAluminium", 4),
                ore("dustSiliconSolarGrade", 4), stack(NEItems.blackTungstenAlloyIngot, 4) },
            FluidRegistry.getFluidStack("lava", 2 * BUCKETS),
            new ItemStack[] { stack(NEItems.energizedSuperconductiveIngot, 4) },
            null,
            40 * SECONDS,
            TierEU.RECIPE_EV);
    }

    private static void registerStorageComponentRecipes() {
        ItemStack component256k = NEAE2RecipeItems.cellComponent256k();
        assembler(
            new ItemStack[] { copy(component256k), stack(NEItems.energizedSuperconductiveIngot, 8),
                stack(NEItems.superconductingProcessor), stack(NEItems.crystalIngot) },
            Materials.SolderingAlloy.getMolten(1 * INGOTS),
            stack(NEStorageItems.ecoCellComponent16M),
            20 * SECONDS,
            TierEU.RECIPE_IV,
            16);
        assembler(
            new ItemStack[] { stack(NEStorageItems.ecoCellComponent16M, 4),
                stack(NEItems.energizedSuperconductiveIngot, 24), stack(NEItems.superconductingProcessor, 4),
                stack(NEItems.crystalMatrix, 2) },
            Materials.SolderingAlloy.getMolten(4 * INGOTS),
            stack(NEStorageItems.ecoCellComponent64M),
            30 * SECONDS,
            TierEU.RECIPE_LuV,
            17);
        assembler(
            new ItemStack[] { stack(NEStorageItems.ecoCellComponent64M, 4),
                stack(NEItems.energizedSuperconductiveIngot, 32), stack(NEItems.superconductingProcessor, 8),
                stack(NEItems.crystalMatrix, 4) },
            Materials.SolderingAlloy.getMolten(8 * INGOTS),
            stack(NEStorageItems.ecoCellComponent256M),
            45 * SECONDS,
            TierEU.RECIPE_ZPM,
            18);
    }

    private static void registerComputationCellRecipes() {
        assembler(
            new ItemStack[] { stack(NEStorageItems.ecoCellComponent16M, 4),
                stack(NEItems.energizedSuperconductiveIngot, 64), stack(NEItems.superconductingProcessor, 4),
                stack(NEItems.crystalMatrix) },
            Materials.SolderingAlloy.getMolten(4 * INGOTS),
            stack(NEStorageItems.ecoComputationCellL4),
            25 * SECONDS,
            TierEU.RECIPE_IV,
            19);
        assembler(
            new ItemStack[] { stack(NEStorageItems.ecoCellComponent64M, 4),
                stack(NEItems.energizedSuperconductiveIngot, 64), stack(NEItems.superconductingProcessor, 8),
                stack(NEItems.crystalMatrix, 2) },
            Materials.SolderingAlloy.getMolten(8 * INGOTS),
            stack(NEStorageItems.ecoComputationCellL6),
            40 * SECONDS,
            TierEU.RECIPE_LuV,
            20);
        assembler(
            new ItemStack[] { stack(NEStorageItems.ecoCellComponent256M, 4),
                stack(NEItems.energizedSuperconductiveIngot, 64), stack(NEItems.superconductingProcessor, 16),
                stack(NEItems.crystalMatrix, 4) },
            Materials.SolderingAlloy.getMolten(12 * INGOTS),
            stack(NEStorageItems.ecoComputationCellL9),
            60 * SECONDS,
            TierEU.RECIPE_ZPM,
            21);
    }

    private static void registerSystemBlockRecipes() {
        ItemStack drive = NEAE2RecipeItems.drive();
        registerStorageSystems(drive);
        registerCraftingSystems();
        registerComputationSystems();
    }

    private static void registerStorageSystems(ItemStack drive) {
        assembler(
            new ItemStack[] { stack(NEBlocks.storageCasing, 4), copy(drive, 4),
                stack(NEItems.energizedSuperconductiveIngot, 16), stack(NEItems.superconductingProcessor, 16) },
            Materials.SolderingAlloy.getMolten(4 * INGOTS),
            stack(NEBlocks.storageSystemL4),
            20 * SECONDS,
            TierEU.RECIPE_IV,
            4);
        assembler(
            new ItemStack[] { stack(NEBlocks.storageSystemL4), copy(drive, 8),
                stack(NEItems.energizedSuperconductiveIngot, 32), stack(NEItems.superconductingProcessor, 32) },
            Materials.SolderingAlloy.getMolten(8 * INGOTS),
            stack(NEBlocks.storageSystemL6),
            40 * SECONDS,
            TierEU.RECIPE_LuV,
            6);
        assembler(
            new ItemStack[] { stack(NEBlocks.storageSystemL6), copy(drive, 16),
                stack(NEItems.energizedSuperconductiveIngot, 64), stack(NEItems.superconductingProcessor, 64) },
            Materials.SolderingAlloy.getMolten(16 * INGOTS),
            stack(NEBlocks.storageSystemL9),
            80 * SECONDS,
            TierEU.RECIPE_ZPM,
            9);
    }

    private static void registerCraftingSystems() {
        assembler(
            new ItemStack[] { stack(NEBlocks.craftingCasing, 4), stack(NEBlocks.craftingParallelCoreL4, 2),
                stack(NEItems.energizedSuperconductiveIngot, 16), stack(NEItems.superconductingProcessor, 16) },
            Materials.SolderingAlloy.getMolten(4 * INGOTS),
            stack(NEBlocks.craftingSystemL4),
            20 * SECONDS,
            TierEU.RECIPE_IV,
            14);
        assembler(
            new ItemStack[] { stack(NEBlocks.craftingSystemL4), stack(NEBlocks.craftingParallelCoreL6, 2),
                stack(NEItems.energizedSuperconductiveIngot, 32), stack(NEItems.superconductingProcessor, 32) },
            Materials.SolderingAlloy.getMolten(8 * INGOTS),
            stack(NEBlocks.craftingSystemL6),
            40 * SECONDS,
            TierEU.RECIPE_LuV,
            16);
        assembler(
            new ItemStack[] { stack(NEBlocks.craftingSystemL6), stack(NEBlocks.craftingParallelCoreL9, 2),
                stack(NEItems.energizedSuperconductiveIngot, 64), stack(NEItems.superconductingProcessor, 64) },
            Materials.SolderingAlloy.getMolten(16 * INGOTS),
            stack(NEBlocks.craftingSystemL9),
            80 * SECONDS,
            TierEU.RECIPE_ZPM,
            19);
    }

    private static void registerComputationSystems() {
        assembler(
            new ItemStack[] { stack(NEBlocks.computationCasing, 4), stack(NEBlocks.computationParallelCoreL4, 2),
                stack(NEItems.energizedSuperconductiveIngot, 16), stack(NEItems.superconductingProcessor, 16) },
            Materials.SolderingAlloy.getMolten(4 * INGOTS),
            stack(NEBlocks.computationSystemL4),
            20 * SECONDS,
            TierEU.RECIPE_IV,
            24);
        assembler(
            new ItemStack[] { stack(NEBlocks.computationSystemL4), stack(NEBlocks.computationParallelCoreL6, 2),
                stack(NEItems.energizedSuperconductiveIngot, 32), stack(NEItems.superconductingProcessor, 32) },
            Materials.SolderingAlloy.getMolten(8 * INGOTS),
            stack(NEBlocks.computationSystemL6),
            40 * SECONDS,
            TierEU.RECIPE_LuV,
            26);
        assembler(
            new ItemStack[] { stack(NEBlocks.computationSystemL6), stack(NEBlocks.computationParallelCoreL9, 2),
                stack(NEItems.energizedSuperconductiveIngot, 64), stack(NEItems.superconductingProcessor, 64) },
            Materials.SolderingAlloy.getMolten(16 * INGOTS),
            stack(NEBlocks.computationSystemL9),
            80 * SECONDS,
            TierEU.RECIPE_ZPM,
            29);
    }

    private static void registerInfiniteComponentRecipe() {
        ItemStack output = stack(NEStorageItems.ecoInfiniteCellComponent);
        ItemStack research = stack(NEStorageItems.ecoCellComponent256M);
        if (output == null || research == null) {
            return;
        }

        GTRecipeBuilder.builder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(2 * MINUTES, TierEU.RECIPE_ZPM))
            .itemInputs(
                stack(NEStorageItems.ecoCellComponent256M, 24),
                stack(NEStorageItems.ecoComputationCellL9, 6),
                stack(NEBlocks.storageSystemL9, 3),
                stack(NEBlocks.computationSystemL9, 3),
                stack(NEItems.crystalMatrix, 12),
                copy(NEAE2RecipeItems.singularity(), 16),
                stack(NEBlocks.energizedFluixCrystalBlock, 16),
                stack(NEItems.energizedSuperconductiveIngot, 64),
                ItemList.Field_Generator_UV.get(3),
                ItemList.Emitter_UV.get(3),
                ItemList.Sensor_UV.get(3),
                new Object[] { OrePrefixes.circuit.get(Materials.UV), 3 })
            .fluidInputs(
                Materials.SolderingAlloy.getMolten(24 * INGOTS),
                Materials.Naquadria.getMolten(12 * INGOTS),
                Materials.Lubricant.getFluid(6 * BUCKETS))
            .itemOutputs(output)
            .duration(4 * MINUTES)
            .eut((int) TierEU.RECIPE_UV)
            .addTo(AssemblyLine);
    }

    private static void mixer(ItemStack[] itemInputs, FluidStack fluidInput, ItemStack[] itemOutputs,
        FluidStack[] fluidOutputs, int duration, long eut) {
        if (!complete(itemInputs) || !complete(itemOutputs) || !complete(fluidOutputs)) {
            return;
        }
        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .duration(duration)
            .eut((int) eut);
        if (itemInputs != null) {
            builder.itemInputsUnsafe(itemInputs);
        }
        if (fluidInput != null) {
            builder.fluidInputs(fluidInput);
        }
        if (itemOutputs != null) {
            builder.itemOutputs(itemOutputs);
        }
        if (fluidOutputs != null) {
            builder.fluidOutputs(fluidOutputs);
        }
        builder.addTo(RecipeMaps.mixerRecipes);
    }

    private static void macerator(ItemStack input, ItemStack output, int duration, long eut) {
        if (input == null || output == null) {
            return;
        }
        GTRecipeBuilder.builder()
            .itemInputs(input)
            .itemOutputs(output)
            .duration(duration)
            .eut((int) eut)
            .addTo(RecipeMaps.maceratorRecipes);
    }

    private static void assembler(ItemStack[] itemInputs, FluidStack fluidInput, ItemStack output, int duration,
        long eut, int circuit) {
        if (!complete(itemInputs) || output == null) {
            return;
        }
        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .itemInputsUnsafe(itemInputs)
            .circuit(circuit)
            .itemOutputs(output)
            .duration(duration)
            .eut((int) eut);
        if (fluidInput != null) {
            builder.fluidInputs(fluidInput);
        }
        builder.addTo(RecipeMaps.assemblerRecipes);
    }

    private static ItemStack stack(Item item) {
        return stack(item, 1);
    }

    private static ItemStack stack(Item item, int amount) {
        return item == null ? null : new ItemStack(item, amount);
    }

    private static ItemStack stack(Block block) {
        return stack(block, 1);
    }

    private static ItemStack stack(Block block, int amount) {
        return block == null ? null : new ItemStack(block, amount);
    }

    private static ItemStack copy(ItemStack stack) {
        return copy(stack, stack == null ? 0 : stack.stackSize);
    }

    private static ItemStack copy(ItemStack stack, int amount) {
        if (stack == null) {
            return null;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = amount;
        return copy;
    }

    private static ItemStack ore(String oreName, int amount) {
        return GTOreDictUnificator.get(oreName, null, amount);
    }

    private static FluidStack water(int amount) {
        FluidStack water = FluidRegistry.getFluidStack("water", amount);
        return water != null ? water : new FluidStack(FluidRegistry.WATER, amount);
    }

    private static boolean complete(Object[] entries) {
        if (entries == null) {
            return true;
        }
        for (Object entry : entries) {
            if (entry == null) {
                return false;
            }
        }
        return true;
    }
}
