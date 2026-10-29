package cn.dancingsnow.neoecoae.platform.gt;

import static gregtech.api.util.GTRecipeConstants.CLEANROOM;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;
import static gregtech.api.util.GTRecipeConstants.UniversalArcFurnace;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.recipe.GTRecipeProfile;
import cn.dancingsnow.neoecoae.platform.recipe.SourceFluids;
import cn.dancingsnow.neoecoae.platform.recipe.SourceIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceItems;
import cn.dancingsnow.neoecoae.platform.recipe.SourceRecipes;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;

/** Original GTM bootstrap/ore recipes installed in real native GT recipe maps, with native EU and requirements. */
final class GregTechMachineRecipes {

    private static boolean registered;

    private GregTechMachineRecipes() {}

    static void register() {
        if (registered) throw new IllegalStateException("GT machine recipes installed twice");
        int definitions = 0;
        int variants = 0;
        for (var entry : SourceRecipes.read(GTRecipeProfile.CATALOG)
            .getAsJsonArray("files")) {
            var file = entry.getAsJsonObject();
            if (!file.get("profile")
                .getAsString()
                .equals("gt_legacy")) continue;
            String id = file.get("id")
                .getAsString();
            variants += install(
                id,
                GTRecipeProfile.normalize(
                    SourceRecipes.read(
                        file.get("resource")
                            .getAsString())));
            definitions++;
        }
        if (definitions != 19)
            throw new IllegalStateException("Incomplete authorized 1.20.1 GT machine source catalog");
        variants += registerEnergizedCrystalSeed();
        registered = true;
        NeoECOAE.LOG.info(
            "NEOECOAE_NATIVE_GT_RECIPES_REGISTERED definitions={} variants={} source=024b73da",
            definitions,
            variants);
    }

    private static int install(String id, JsonObject source) {
        RecipeMap<?> map = map(
            source.get("type")
                .getAsString());
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (var element : source.getAsJsonObject("inputs")
            .getAsJsonArray("item")) {
            var content = element.getAsJsonObject()
                .getAsJsonObject("content");
            int count = content.get("count")
                .getAsInt();
            List<ItemStack> alternatives = new ArrayList<>();
            for (var stack : SourceIngredient.of(content.get("ingredient"))
                .examples()) {
                ItemStack canonical = GTOreDictUnificator.get(true, stack);
                if (canonical == null) throw new IllegalStateException("Invalid GT ingredient " + id);
                canonical.stackSize = count;
                boolean duplicate = false;
                for (ItemStack previous : alternatives)
                    if (ItemStack.areItemStacksEqual(previous, canonical)) duplicate = true;
                if (!duplicate) alternatives.add(canonical);
            }
            inputs.add(alternatives);
        }
        // GTNH's alloy smelter requires two inputs and rejects a lone metal dust. Its casting mold is not consumed.
        if (source.get("type")
            .getAsString()
            .equals("gtceu:alloy_smelter") && inputs.size() == 1)
            inputs.add(java.util.Collections.singletonList(ItemList.Shape_Mold_Ingot.get(0)));
        List<ItemStack> outputs = new ArrayList<>();
        for (var element : source.getAsJsonObject("outputs")
            .getAsJsonArray("item")) {
            var content = element.getAsJsonObject()
                .getAsJsonObject("content");
            String output = content.getAsJsonObject("ingredient")
                .get("item")
                .getAsString();
            ItemStack stack = SourceItems.stack(
                output,
                content.get("count")
                    .getAsInt());
            if (stack == null) throw new IllegalStateException("Missing native GT recipe output " + output);
            outputs.add(stack);
        }
        GTRecipeBuilder builder = GTValues.RA.stdBuilder()
            .itemOutputs(outputs.toArray(new ItemStack[0]))
            .fluidInputs(fluids(source.getAsJsonObject("inputs")))
            .duration(
                source.get("duration")
                    .getAsInt())
            .eut(
                source.getAsJsonObject("tickInputs")
                    .getAsJsonArray("eu")
                    .get(0)
                    .getAsJsonObject()
                    .get("content")
                    .getAsInt())
            .setNEIDesc("NeoECOAE");
        if (source.has("data") && source.getAsJsonObject("data")
            .has("ebf_temp"))
            builder.metadata(
                COIL_HEAT,
                source.getAsJsonObject("data")
                    .get("ebf_temp")
                    .getAsInt());
        if (source.has("recipeConditions")) for (var condition : source.getAsJsonArray("recipeConditions")) {
            if (!condition.getAsJsonObject()
                .get("type")
                .getAsString()
                .equals("cleanroom"))
                throw new IllegalStateException("Unsupported GT process requirement " + condition);
            builder.metadata(CLEANROOM, true);
        }
        return installVariants(id, map, builder, inputs, new ItemStack[inputs.size()], 0);
    }

    /**
     * 1.7.10-only seed: the source's first energized crystal comes from lightning striking budding certus, which GTNH
     * lacks. The arc furnace stands in for that lightning at MV; the source autoclave loop multiplies it afterwards.
     */
    private static int registerEnergizedCrystalSeed() {
        ItemStack input = SourceItems.stack("ae2:charged_certus_quartz_crystal", 4);
        ItemStack output = SourceItems.stack("neoecoae:energized_crystal", 1);
        if (input == null || output == null)
            throw new IllegalStateException("Missing energized crystal arc furnace seed material");
        int count = GTValues.RA.stdBuilder()
            .itemInputs(input)
            .itemOutputs(output)
            .duration(200)
            .eut(TierEU.RECIPE_MV)
            .setNEIDesc("NeoECOAE")
            .addTo(UniversalArcFurnace)
            .size();
        if (count == 0) throw new IllegalStateException("Native GT map rejected energized crystal seed");
        return count;
    }

    private static int installVariants(String id, RecipeMap<?> map, GTRecipeBuilder builder,
        List<List<ItemStack>> choices, ItemStack[] selected, int slot) {
        if (slot == choices.size()) {
            int count = builder.copy()
                .itemInputs(selected)
                .addTo(map)
                .size();
            if (count == 0) throw new IllegalStateException("Native GT map rejected source recipe " + id);
            return count;
        }
        int count = 0;
        for (ItemStack alternative : choices.get(slot)) {
            selected[slot] = alternative.copy();
            count += installVariants(id, map, builder, choices, selected, slot + 1);
        }
        return count;
    }

    private static FluidStack[] fluids(JsonObject inputs) {
        List<FluidStack> result = new ArrayList<>();
        if (inputs.has("fluid")) for (var element : inputs.getAsJsonArray("fluid")) {
            var content = element.getAsJsonObject()
                .getAsJsonObject("content");
            String id = content.getAsJsonObject("value")
                .get("fluid")
                .getAsString();
            var fluid = SourceFluids.fluid(id);
            if (fluid == null) throw new IllegalStateException("Missing native GT fluid " + id);
            result.add(
                new FluidStack(
                    fluid,
                    content.get("amount")
                        .getAsInt()));
        }
        return result.toArray(new FluidStack[0]);
    }

    private static RecipeMap<?> map(String type) {
        switch (type) {
            case "gtceu:mixer":
                return RecipeMaps.mixerRecipes;
            case "gtceu:alloy_smelter":
                return RecipeMaps.alloySmelterRecipes;
            case "gtceu:electric_blast_furnace":
                return RecipeMaps.blastFurnaceRecipes;
            case "gtceu:autoclave":
                return RecipeMaps.autoclaveRecipes;
            case "gtceu:circuit_assembler":
                return RecipeMaps.circuitAssemblerRecipes;
            case "gtceu:assembler":
                return RecipeMaps.assemblerRecipes;
            case "gtceu:macerator":
                return RecipeMaps.maceratorRecipes;
            default:
                throw new IllegalArgumentException("Unmapped native GT recipe map: " + type);
        }
    }
}
