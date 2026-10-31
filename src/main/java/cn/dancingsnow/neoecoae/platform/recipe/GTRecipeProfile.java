package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;

/** The 1.20.1 GTM progression overlays the locked standalone recipes only when native GT is installed. */
public final class GTRecipeProfile {

    public static final String CATALOG = "neoecoae/gt-1201/catalog.json";

    private GTRecipeProfile() {}

    static Map<String, JsonObject> load() {
        Map<String, JsonObject> recipes = new LinkedHashMap<>();
        if (!NativeGregTech.isAvailable()) return recipes;
        for (var entry : SourceRecipes.read(CATALOG)
            .getAsJsonArray("files")) {
            var file = entry.getAsJsonObject();
            if (!file.get("profile")
                .getAsString()
                .equals("gtm")) continue;
            String id = file.get("id")
                .getAsString();
            var source = SourceRecipes.read(
                file.get("resource")
                    .getAsString());
            if (file.get("kind")
                .getAsString()
                .equals("tag")) SourceTags.add(id, normalizeTag(id, source));
            else {
                JsonObject recipe = normalize(source);
                applyNativeProgression(id, recipe);
                recipes.put(id.substring("neoecoae:".length()), recipe);
            }
        }
        return recipes;
    }

    /** GTNH progression overrides live here so the imported source catalog stays byte-for-byte intact. */
    private static void applyNativeProgression(String id, JsonObject recipe) {
        if (!recipe.has("inputItems")) return;
        String tier;
        if (id.endsWith("_l4") || id.equals("neoecoae:eco_cell_component_16m")) tier = "iv";
        else if (id.endsWith("_l6") || id.equals("neoecoae:eco_cell_component_64m")) tier = "luv";
        else if (id.endsWith("_l9") || id.equals("neoecoae:eco_cell_component_256m")) tier = "zpm";
        else if (id.equals("neoecoae:eco_infinite_cell_component")
            || id.equals("neoecoae:eco_infinite_item_cell")) tier = "uhv";
        else return;
        var inputs = recipe.getAsJsonArray("inputItems");
        for (var entry : inputs) {
            var input = entry.getAsJsonObject();
            if (input.has("tag") && input.get("tag")
                .getAsString()
                .startsWith("gtceu:circuits/")) input.addProperty("tag", "gtceu:circuits/" + tier);
            if (!input.has("item")) continue;
            String item = input.get("item")
                .getAsString();
            for (String part : new String[] { "field_generator", "sensor", "emitter" }) {
                if (item.startsWith("gtceu:") && item.endsWith("_" + part))
                    input.addProperty("item", "gtceu:" + tier + "_" + part);
            }
            if (id.equals("neoecoae:eco_cell_component_16m") && item.equals("ae2:cell_component_256k"))
                input.addProperty("item", "ae2:cell_component_1024k");
        }
        String component = id.equals("neoecoae:eco_cell_component_64m") ? "4096k"
            : id.equals("neoecoae:eco_cell_component_256m") ? "16384k" : null;
        if (component != null) {
            var input = new JsonObject();
            input.addProperty("item", "ae2:cell_component_" + component);
            input.addProperty("count", 12);
            inputs.add(input);
        }
    }

    /** GTNH-specific progression belongs in the adapter, not the byte-locked source tag. */
    static JsonObject normalizeTag(String id, JsonObject source) {
        JsonObject result = normalize(source);
        if (!id.equals("neoecoae:compat/gt/silicon")) return result;
        var values = new com.google.gson.JsonArray();
        for (var entry : result.getAsJsonArray("values")) {
            var value = entry.getAsJsonObject();
            String item = value.get("id")
                .getAsString();
            if (item.equals("gtceu:silicon_dust")) continue;
            if (item.equals("gtceu:silicon_ingot")) value.addProperty("id", "gtceu:silicon_solar_grade_plate");
            values.add(value);
        }
        result.add("values", values);
        return result;
    }

    /** Keep the copied source bytes intact; adapt Forge 1.20 serializer field names at the platform boundary. */
    public static JsonObject normalize(JsonObject source) {
        JsonObject result = new JsonParser().parse(source.toString())
            .getAsJsonObject();
        normalizeTags(result);
        if (result.has("conditions")) {
            var conditions = result.remove("conditions");
            result.add("neoforge:conditions", conditions);
            for (var value : conditions.getAsJsonArray()) {
                var condition = value.getAsJsonObject();
                String type = condition.get("type")
                    .getAsString();
                condition.addProperty("type", type.replace("forge:", "neoforge:"));
                if (condition.has("modid") && condition.get("modid")
                    .getAsString()
                    .equals("gtceu")) condition.addProperty("modid", "gregtech");
            }
        }
        for (String key : new String[] { "result", "itemOutput" }) {
            if (!result.has(key)) continue;
            var output = result.getAsJsonObject(key);
            if (output.has("item")) output.add("id", output.remove("item"));
        }
        if (result.has("fluidOutput")) {
            var output = result.getAsJsonObject("fluidOutput");
            if (output.has("fluid")) output.add("id", output.remove("fluid"));
        }
        if (result.has("inputItems")) for (var value : result.getAsJsonArray("inputItems")) {
            var input = value.getAsJsonObject();
            if (!input.has("count")) input.addProperty("count", 1);
        }
        return result;
    }

    private static void normalizeTags(JsonElement value) {
        if (value.isJsonArray()) {
            for (var entry : value.getAsJsonArray()) normalizeTags(entry);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("tag")) object.addProperty(
                "tag",
                tag(
                    object.get("tag")
                        .getAsString()));
            if (object.has("values")) {
                var values = object.getAsJsonArray("values");
                var adapted = new com.google.gson.JsonArray();
                for (var item : values) {
                    if (item.isJsonPrimitive() && item.getAsString()
                        .startsWith("#forge:"))
                        adapted.add(
                            new com.google.gson.JsonPrimitive(
                                "#" + tag(
                                    item.getAsString()
                                        .substring(1))));
                    else adapted.add(item);
                }
                object.add("values", adapted);
            }
            for (var entry : object.entrySet()) normalizeTags(entry.getValue());
        }
    }

    private static String tag(String id) {
        return id.startsWith("forge:") ? "c:" + id.substring("forge:".length()) : id;
    }
}
