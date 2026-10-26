package cn.dancingsnow.neoecoae.platform.recipe;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import appeng.api.AEApi;
import appeng.api.features.InscriberProcessType;
import appeng.core.features.registries.entries.InscriberRecipe;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

/** Native installation of original source recipe JSON. Deferred entries always carry a reason. */
public final class SourceRecipes {

    private static final Map<String, String> STATUS = new LinkedHashMap<>();
    private static final Map<String, IRecipe> CRAFTING = new LinkedHashMap<>();

    private SourceRecipes() {}

    static final class Unavailable extends RuntimeException {

        Unavailable(String reason) {
            super(reason);
        }
    }

    public static JsonObject read(String resource) {
        var stream = SourceRecipes.class.getResourceAsStream("/" + resource);
        if (stream == null) throw new IllegalStateException("Missing source recipe resource: " + resource);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read source recipe: " + resource, failure);
        }
    }

    public static void register() {
        if (!STATUS.isEmpty()) throw new IllegalStateException("Source recipes installed twice");
        SourceItems.initialize();
        var catalog = read("neoecoae/recipe-catalog.json");
        SourceTags.initialize(catalog);
        for (var entry : catalog.getAsJsonArray("recipes")) {
            String path = entry.getAsJsonObject()
                .get("path")
                .getAsString();
            String id = path.substring("data/neoecoae/recipe/".length(), path.length() - 5);
            var definition = read(path);
            try {
                if (definition.has("neoforge:conditions"))
                    for (var condition : definition.getAsJsonArray("neoforge:conditions"))
                        if (!condition(condition.getAsJsonObject())) throw new Unavailable("optional source condition");
                if (definition.toString()
                    .contains("integrated_working_station_module_")) {
                    STATUS.put(id, "excluded:user_large_workstation_cards");
                    continue;
                }
                install(id, definition);
            } catch (Unavailable unavailable) {
                STATUS.put(id, "pending:" + unavailable.getMessage());
            }
        }
        WorldTransformRecipes.bind();
        var counts = new LinkedHashMap<String, Integer>();
        for (String status : STATUS.values()) counts.merge(status.split(":", 2)[0], 1, Integer::sum);
        NeoECOAE.LOG.info("NEOECOAE_SOURCE_RECIPES_REGISTERED totals={} native_crafting={}", counts, CRAFTING.size());
        if (Boolean.getBoolean("neoecoae.recipeAcceptance")) NativeRecipeAcceptance.checkRegistered();
        for (var entry : STATUS.entrySet()) if (entry.getValue()
            .startsWith("pending:"))
            NeoECOAE.LOG.debug("Source recipe deferred {} {}", entry.getKey(), entry.getValue());
    }

    private static boolean condition(JsonObject condition) {
        String type = condition.get("type")
            .getAsString();
        if (type.equals("neoforge:mod_loaded")) return Loader.isModLoaded(
            condition.get("modid")
                .getAsString());
        if (type.equals("neoforge:not")) return !condition(condition.getAsJsonObject("value"));
        throw new IllegalArgumentException("Unsupported source recipe condition: " + condition);
    }

    static ItemStack output(JsonObject definition) {
        return output(definition, true);
    }

    static ItemStack output(JsonObject definition, boolean nested) {
        var result = nested ? definition.getAsJsonObject("result") : definition;
        if (result == null) throw new IllegalArgumentException("Missing source result: " + definition);
        int count = result.has("count") ? result.get("count")
            .getAsInt() : 1;
        String id = result.get("id")
            .getAsString();
        ItemStack stack = SourceItems.stack(id, count);
        if (stack == null) throw new Unavailable("unregistered result " + id);
        if (result.has("components"))
            throw new IllegalArgumentException("Unmapped source recipe result components: " + result);
        return stack;
    }

    private static void install(String id, JsonObject definition) {
        String type = definition.get("type")
            .getAsString();
        if (type.equals("ae2:storage_cell_disassembly")) {
            STATUS.put(
                id,
                StorageRecipes.hasDisassembly(
                    definition.get("cell")
                        .getAsString()) ? "installed:disassembly" : "pending:unregistered cell");
            return;
        }
        IRecipe recipe;
        switch (type) {
            case "minecraft:crafting_shaped": {
                ItemStack result = output(definition);
                var arguments = new ArrayList<Object>();
                var rows = new ArrayList<String>();
                var keys = new LinkedHashMap<Character, SourceIngredient>();
                for (var row : definition.getAsJsonArray("pattern")) {
                    rows.add(row.getAsString());
                    arguments.add(row.getAsString());
                }
                for (var entry : definition.getAsJsonObject("key")
                    .entrySet()) {
                    if (entry.getKey()
                        .length() != 1)
                        throw new IllegalArgumentException("Invalid source pattern key: " + entry.getKey());
                    var ingredient = SourceIngredient.of(entry.getValue());
                    keys.put(
                        entry.getKey()
                            .charAt(0),
                        ingredient);
                    arguments.add(
                        entry.getKey()
                            .charAt(0));
                    arguments.add(
                        ingredient.examples()
                            .get(0));
                }
                var shaped = new ShapedOreRecipe(result, arguments.toArray());
                int width = rows.get(0)
                    .length();
                for (int y = 0; y < rows.size(); y++) for (int x = 0; x < width; x++) {
                    var ingredient = keys.get(
                        rows.get(y)
                            .charAt(x));
                    shaped.getInput()[x + y * width] = ingredient == null ? null : ingredient.examples();
                }
                recipe = shaped;
                break;
            }
            case "minecraft:crafting_shapeless": {
                ItemStack result = output(definition);
                var ingredients = new ArrayList<SourceIngredient>();
                for (var ingredient : definition.getAsJsonArray("ingredients"))
                    ingredients.add(SourceIngredient.of(ingredient));
                recipe = new SourceShapelessRecipe(result, ingredients, false);
                break;
            }
            case "minecraft:smithing_transform": {
                ItemStack result = output(definition);
                var ingredients = new ArrayList<SourceIngredient>();
                for (String part : new String[] { "template", "base", "addition" })
                    ingredients.add(SourceIngredient.of(definition.get(part)));
                recipe = new SourceShapelessRecipe(result, ingredients, true);
                break;
            }
            case "minecraft:smelting": {
                ItemStack result = output(definition);
                for (var ingredient : SourceIngredient.of(definition.get("ingredient"))
                    .examples())
                    GameRegistry.addSmelting(
                        ingredient,
                        result.copy(),
                        definition.get("experience")
                            .getAsFloat());
                STATUS.put(id, "installed:smelting");
                return;
            }
            case "minecraft:blasting": {
                BlastingRecipes.install(
                    cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse("neoecoae:" + id),
                    definition);
                STATUS.put(id, "installed:blasting_directory");
                return;
            }
            case "ae2:transform": {
                WorldTransformRecipes.install(
                    cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse("neoecoae:" + id),
                    definition);
                STATUS.put(id, "installed:world_transform");
                return;
            }
            case "neoecoae:integrated_working_station": {
                WorkstationRecipes.install(
                    cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse("neoecoae:" + id),
                    definition);
                STATUS.put(id, "installed:workstation_directory");
                return;
            }
            case "neoecoae:cooling": {
                WorkstationRecipes.installCooling(
                    cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse("neoecoae:" + id),
                    definition);
                STATUS.put(id, "installed:cooling_directory");
                return;
            }
            case "ae2:inscriber": {
                installInscriber(definition);
                STATUS.put(id, "installed:inscriber");
                return;
            }
            default:
                throw new Unavailable("recipe adapter " + type);
        }
        GameRegistry.addRecipe(recipe);
        CRAFTING.put(id, recipe);
        STATUS.put(id, "installed:" + type);
    }

    private static void installInscriber(JsonObject definition) {
        ItemStack result = output(definition);
        JsonObject ingredients = definition.getAsJsonObject("ingredients");
        List<ItemStack> middle = SourceIngredient.of(ingredients.get("middle"))
            .examples();
        List<ItemStack> top = ingredients.has("top") ? SourceIngredient.of(ingredients.get("top"))
            .examples() : Collections.singletonList(null);
        List<ItemStack> bottom = ingredients.has("bottom") ? SourceIngredient.of(ingredients.get("bottom"))
            .examples() : Collections.singletonList(null);
        String mode = definition.get("mode")
            .getAsString();
        if (!mode.equals("press") && !mode.equals("inscribe"))
            throw new IllegalArgumentException("Unknown inscriber mode: " + mode);
        for (ItemStack t : top) for (ItemStack b : bottom) AEApi.instance()
            .registries()
            .inscriber()
            .addRecipe(
                new InscriberRecipe(
                    middle,
                    result.copy(),
                    t,
                    b,
                    mode.equals("press") ? InscriberProcessType.Press : InscriberProcessType.Inscribe));
    }

    public static Map<String, String> status() {
        return Collections.unmodifiableMap(STATUS);
    }

    public static Map<String, IRecipe> crafting() {
        return Collections.unmodifiableMap(CRAFTING);
    }
}
