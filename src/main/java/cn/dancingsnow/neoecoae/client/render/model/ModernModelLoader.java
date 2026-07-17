package cn.dancingsnow.neoecoae.client.render.model;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.NeoECOAE;

public final class ModernModelLoader {

    private ModernModelLoader() {}

    public static ModernModel loadBlockModel(String name) {
        return loadModel(new ResourceLocation(NeoECOAE.MODID, "models/block/" + name + ".json"), 0);
    }

    public static ModernModel loadModel(ResourceLocation location, int depth) {
        if (depth > 4) {
            throw new IllegalStateException("Model parent chain is too deep for " + location);
        }

        JsonObject json = readJson(location);
        ModernModel parentModel = loadParent(json, depth);
        ModernModel model = parentModel != null ? parentModel.copy() : new ModernModel();

        readTextures(json, model);
        readRenderLayer(json, model);
        readElements(json, model, location.toString());
        readChildren(json, model, depth);
        return model;
    }

    private static void readChildren(JsonObject json, ModernModel model, int depth) {
        if (!json.has("children")) {
            return;
        }

        model.getElements()
            .clear();
        JsonObject children = json.getAsJsonObject("children");
        for (Entry<String, JsonElement> entry : children.entrySet()) {
            ModernModel childModel = loadInlineModel(
                entry.getValue()
                    .getAsJsonObject(),
                depth + 1);
            model.appendResolvedElementsFrom(childModel);
        }
    }

    private static ModernModel loadInlineModel(JsonObject json, int depth) {
        if (depth > 4) {
            throw new IllegalStateException("Inline model parent chain is too deep");
        }

        ModernModel parentModel = loadParent(json, depth);
        ModernModel model = parentModel != null ? parentModel.copy() : new ModernModel();
        readTextures(json, model);
        readRenderLayer(json, model);
        readElements(json, model, "inline model");
        readChildren(json, model, depth);
        return model;
    }

    private static ModernModel loadParent(JsonObject json, int depth) {
        if (!json.has("parent")) {
            return null;
        }

        String parent = json.get("parent")
            .getAsString();
        if ("block/block".equals(parent) || "minecraft:block/block".equals(parent)) {
            return null;
        }
        if ("block/cube_all".equals(parent) || "minecraft:block/cube_all".equals(parent)) {
            return createCubeAllParent();
        }
        return loadModel(toModelLocation(parent), depth + 1);
    }

    private static ModernModel createCubeAllParent() {
        ModernModel model = new ModernModel();
        ModelElement element = new ModelElement(
            new double[] { 0.0D, 0.0D, 0.0D },
            new double[] { 16.0D, 16.0D, 16.0D });
        addCubeAllFace(element, ForgeDirection.DOWN, "down");
        addCubeAllFace(element, ForgeDirection.UP, "up");
        addCubeAllFace(element, ForgeDirection.NORTH, "north");
        addCubeAllFace(element, ForgeDirection.SOUTH, "south");
        addCubeAllFace(element, ForgeDirection.WEST, "west");
        addCubeAllFace(element, ForgeDirection.EAST, "east");
        model.getElements()
            .add(element);
        return model;
    }

    private static void addCubeAllFace(ModelElement element, ForgeDirection side, String cullFace) {
        element.addFace(new ModelFace(side, "#all", cullFace, 0.0D, 0.0D, 16.0D, 16.0D, 0));
    }

    private static ResourceLocation toModelLocation(String modelId) {
        String namespace = NeoECOAE.MODID;
        String path = modelId;
        int separator = modelId.indexOf(':');
        if (separator >= 0) {
            namespace = modelId.substring(0, separator);
            path = modelId.substring(separator + 1);
        }
        return new ResourceLocation(namespace, "models/" + path + ".json");
    }

    private static JsonObject readJson(ResourceLocation location) {
        try {
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location);
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader)
                    .getAsJsonObject();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load model " + location, e);
        }
    }

    private static void readTextures(JsonObject json, ModernModel model) {
        if (!json.has("textures")) {
            return;
        }

        JsonObject textures = json.getAsJsonObject("textures");
        for (Entry<String, JsonElement> entry : textures.entrySet()) {
            model.getTextures()
                .put(
                    entry.getKey(),
                    entry.getValue()
                        .getAsString());
        }
    }

    private static void readRenderLayer(JsonObject json, ModernModel model) {
        if (!json.has("render_type")) {
            return;
        }
        model.setRenderLayer(
            ModelRenderLayer.fromJsonName(
                json.get("render_type")
                    .getAsString(),
                model.getRenderLayer()));
    }

    private static void readElements(JsonObject json, ModernModel model, String modelName) {
        if (!json.has("elements")) {
            return;
        }

        model.getElements()
            .clear();
        JsonArray elements = json.getAsJsonArray("elements");
        for (int i = 0; i < elements.size(); i++) {
            JsonElement elementValue = elements.get(i);
            JsonObject elementJson = elementValue.getAsJsonObject();
            if (hasUnsupportedRotation(elementJson)) {
                NeoECOAE.LOG.warn("Skipping non-zero model element rotation in lightweight renderer");
                continue;
            }

            require(elementJson, "from", modelName, "element " + i);
            require(elementJson, "to", modelName, "element " + i);
            require(elementJson, "faces", modelName, "element " + i);
            ModelElement element = new ModelElement(
                readVector(elementJson.getAsJsonArray("from")),
                readVector(elementJson.getAsJsonArray("to")),
                !elementJson.has("shade") || elementJson.get("shade")
                    .getAsBoolean(),
                model.getRenderLayer());
            readFaces(elementJson.getAsJsonObject("faces"), element, modelName, "element " + i);
            model.getElements()
                .add(element);
        }
    }

    private static boolean hasUnsupportedRotation(JsonObject elementJson) {
        if (!elementJson.has("rotation")) {
            return false;
        }

        JsonObject rotation = elementJson.getAsJsonObject("rotation");
        return rotation.has("angle") && rotation.get("angle")
            .getAsDouble() != 0.0D;
    }

    private static void readFaces(JsonObject faces, ModelElement element, String modelName, String elementName) {
        for (Entry<String, JsonElement> entry : faces.entrySet()) {
            ForgeDirection side = toDirection(entry.getKey());
            if (side == ForgeDirection.UNKNOWN) {
                continue;
            }

            JsonObject faceJson = entry.getValue()
                .getAsJsonObject();
            require(faceJson, "uv", modelName, elementName + " face " + entry.getKey());
            require(faceJson, "texture", modelName, elementName + " face " + entry.getKey());
            JsonArray uv = faceJson.getAsJsonArray("uv");
            if (uv.size() != 4) {
                throw new IllegalStateException(
                    "Model " + modelName
                        + " "
                        + elementName
                        + " face "
                        + entry.getKey()
                        + " must have exactly 4 uv values");
            }
            String cullFace = faceJson.has("cullface") ? faceJson.get("cullface")
                .getAsString() : null;
            int rotation = faceJson.has("rotation") ? faceJson.get("rotation")
                .getAsInt() : 0;
            boolean fullBright = isFullBright(faceJson);
            ModelRenderLayer renderLayer = faceJson.has("render_type") ? ModelRenderLayer.fromJsonName(
                faceJson.get("render_type")
                    .getAsString(),
                element.getRenderLayer()) : element.getRenderLayer();
            element.addFace(
                new ModelFace(
                    side,
                    faceJson.get("texture")
                        .getAsString(),
                    cullFace,
                    uv.get(0)
                        .getAsDouble(),
                    uv.get(1)
                        .getAsDouble(),
                    uv.get(2)
                        .getAsDouble(),
                    uv.get(3)
                        .getAsDouble(),
                    rotation,
                    fullBright,
                    renderLayer));
        }
    }

    private static boolean isFullBright(JsonObject faceJson) {
        JsonObject data = null;
        if (faceJson.has("neoforge_data")) {
            data = faceJson.getAsJsonObject("neoforge_data");
        } else if (faceJson.has("forge_data")) {
            data = faceJson.getAsJsonObject("forge_data");
        }
        if (data == null) {
            return false;
        }

        int blockLight = data.has("block_light") ? data.get("block_light")
            .getAsInt() : 0;
        int skyLight = data.has("sky_light") ? data.get("sky_light")
            .getAsInt() : 0;
        return blockLight >= 15 || skyLight >= 15;
    }

    private static double[] readVector(JsonArray array) {
        if (array.size() != 3) {
            throw new IllegalStateException("Model vector must have exactly 3 values");
        }
        return new double[] { array.get(0)
            .getAsDouble(),
            array.get(1)
                .getAsDouble(),
            array.get(2)
                .getAsDouble() };
    }

    private static ForgeDirection toDirection(String side) {
        if ("down".equals(side)) {
            return ForgeDirection.DOWN;
        }
        if ("up".equals(side)) {
            return ForgeDirection.UP;
        }
        if ("north".equals(side)) {
            return ForgeDirection.NORTH;
        }
        if ("south".equals(side)) {
            return ForgeDirection.SOUTH;
        }
        if ("west".equals(side)) {
            return ForgeDirection.WEST;
        }
        if ("east".equals(side)) {
            return ForgeDirection.EAST;
        }
        return ForgeDirection.UNKNOWN;
    }

    private static void require(JsonObject json, String member, String modelName, String owner) {
        if (!json.has(member)) {
            throw new IllegalStateException("Model " + modelName + " " + owner + " is missing '" + member + "'");
        }
    }
}
