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
            var definition = normalize(
                SourceRecipes.read(
                    file.get("resource")
                        .getAsString()));
            if (file.get("kind")
                .getAsString()
                .equals("tag")) SourceTags.add(id, definition);
            else recipes.put(id.substring("neoecoae:".length()), definition);
        }
        return recipes;
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
