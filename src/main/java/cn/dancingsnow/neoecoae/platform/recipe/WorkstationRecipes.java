package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

/** Real, typed native machine recipes. JSON is compiled once; machine hosts query these objects. */
public final class WorkstationRecipes {

    private static final Map<ResourceLocation, IntegratedWorkingStationRecipe> RECIPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, IntegratedWorkingStationRecipe> DEFAULT_RECIPES = Collections
        .unmodifiableMap(RECIPES);
    private static volatile Map<ResourceLocation, IntegratedWorkingStationRecipe> activeRecipes = DEFAULT_RECIPES;
    private static final Map<ResourceLocation, CoolingRecipe> COOLING = new LinkedHashMap<>();

    private WorkstationRecipes() {}

    static void install(ResourceLocation id, JsonObject value) {
        // Apply the same storage rebalance after either the standalone or GT recipe profile has been selected.
        String outputId = value.has("itemOutput") ? value.getAsJsonObject("itemOutput")
            .get("id")
            .getAsString() : "";
        int costMultiplier = outputId.startsWith("neoecoae:eco_cell_component_")
            || outputId.startsWith("neoecoae:eco_computation_cell_")
            || outputId.equals("neoecoae:eco_infinite_cell_component")
            || outputId.equals("neoecoae:eco_infinite_item_cell") ? 2 : 1;
        List<SizedIngredient> inputs = new ArrayList<>();
        if (value.has("inputItems")) for (var element : value.getAsJsonArray("inputItems")) {
            var input = element.getAsJsonObject();
            inputs.add(
                new SizedIngredient(
                    SourceIngredient.of(input),
                    Math.multiplyExact(
                        input.get("count")
                            .getAsInt(),
                        costMultiplier)));
        }
        if (inputs.size() > 9) throw new IllegalArgumentException("Too many single workstation inputs: " + id);
        var fluid = fluidInput(value.getAsJsonObject("inputFluid"));
        if (value.has("inputFluid"))
            fluid = new SizedFluidIngredient(fluid.ingredient(), Math.multiplyExact(fluid.amount(), costMultiplier));
        ItemStack output = value.has("itemOutput") ? SourceRecipes.output(value.getAsJsonObject("itemOutput"), false)
            : null;
        FluidStack fluidOutput = SourceFluids.output(value.getAsJsonObject("fluidOutput"));
        if (output == null && fluidOutput == null)
            throw new IllegalArgumentException("Empty workstation output: " + id);
        var recipe = new IntegratedWorkingStationRecipe(
            inputs,
            fluid,
            output,
            fluidOutput,
            Math.multiplyExact(
                value.get("energy")
                    .getAsInt(),
                costMultiplier),
            value.has("processingTicks") ? value.get("processingTicks")
                .getAsInt() : 100);
        if (RECIPES.putIfAbsent(id, recipe) != null)
            throw new IllegalStateException("Duplicate workstation recipe " + id);
    }

    static void installCooling(ResourceLocation id, JsonObject value) {
        var recipe = new CoolingRecipe(
            fluidInput(value.getAsJsonObject("input")),
            SourceFluids.output(value.getAsJsonObject("output")),
            value.get("coolant")
                .getAsInt(),
            value.has("max_overclock") ? value.get("max_overclock")
                .getAsInt() : 0);
        if (COOLING.putIfAbsent(id, recipe) != null) throw new IllegalStateException("Duplicate cooling recipe " + id);
    }

    private static SizedFluidIngredient fluidInput(JsonObject value) {
        return new SizedFluidIngredient(
            SourceFluidIngredient.of(value),
            value == null ? 1
                : value.has("amount") ? value.get("amount")
                    .getAsInt() : 1);
    }

    public static Map<ResourceLocation, IntegratedWorkingStationRecipe> getAll() {
        return activeRecipes;
    }

    /** The large workstation inherits built-ins, independently of single-block script edits. */
    public static Map<ResourceLocation, IntegratedWorkingStationRecipe> getDefaults() {
        return DEFAULT_RECIPES;
    }

    public static synchronized Runnable addScriptRecipe(ResourceLocation id, IntegratedWorkingStationRecipe recipe) {
        if (activeRecipes.containsKey(id))
            throw new IllegalArgumentException("Duplicate integrated workstation recipe " + id);
        Map<ResourceLocation, IntegratedWorkingStationRecipe> next = new LinkedHashMap<>(activeRecipes);
        next.put(id, recipe);
        return replaceScriptRecipes(next);
    }

    public static synchronized Runnable removeScriptRecipes(
        BiPredicate<ResourceLocation, IntegratedWorkingStationRecipe> filter) {
        Map<ResourceLocation, IntegratedWorkingStationRecipe> next = new LinkedHashMap<>(activeRecipes);
        next.entrySet()
            .removeIf(entry -> filter.test(entry.getKey(), entry.getValue()));
        return replaceScriptRecipes(next);
    }

    private static Runnable replaceScriptRecipes(Map<ResourceLocation, IntegratedWorkingStationRecipe> next) {
        Map<ResourceLocation, IntegratedWorkingStationRecipe> before = activeRecipes;
        activeRecipes = Collections.unmodifiableMap(next);
        return () -> {
            synchronized (WorkstationRecipes.class) {
                activeRecipes = before;
            }
        };
    }

    public static IntegratedWorkingStationRecipe getById(ResourceLocation id) {
        return getAll().get(id);
    }

    public static Match find(List<ItemStack> items, FluidStack fluid) {
        var input = new IntegratedWorkingStationRecipe.Input(items, fluid);
        for (var entry : getAll().entrySet()) if (entry.getValue()
            .matches(input, null)) return new Match(entry.getKey(), entry.getValue());
        return null;
    }

    public static Map<ResourceLocation, CoolingRecipe> cooling() {
        return Collections.unmodifiableMap(COOLING);
    }

    public static CoolingRecipe findCooling(FluidStack input, FluidStack output) {
        for (var recipe : COOLING.values())
            if (recipe.matches(new CoolingRecipe.Input(input, output), null)) return recipe;
        return null;
    }

    @com.github.bsideup.jabel.Desugar
    public record Match(ResourceLocation id, IntegratedWorkingStationRecipe recipe) {}
}
