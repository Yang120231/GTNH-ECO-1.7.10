package cn.dancingsnow.neoecoae.client.render.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
        readElements(json, model);
        readDisplay(json, model);
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
        return loadModel(toModelLocation(parent), depth + 1);
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
            InputStream stream = resource.getInputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                return new JsonParser().parse(reader)
                    .getAsJsonObject();
            } finally {
                stream.close();
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

    private static void readElements(JsonObject json, ModernModel model) {
        if (!json.has("elements")) {
            return;
        }

        model.getElements()
            .clear();
        JsonArray elements = json.getAsJsonArray("elements");
        for (JsonElement elementValue : elements) {
            JsonObject elementJson = elementValue.getAsJsonObject();
            if (hasUnsupportedRotation(elementJson)) {
                NeoECOAE.LOG.warn("Skipping non-zero model element rotation in lightweight renderer");
                continue;
            }

            ModelElement element = new ModelElement(
                readVector(elementJson.getAsJsonArray("from")),
                readVector(elementJson.getAsJsonArray("to")));
            readFaces(elementJson.getAsJsonObject("faces"), element);
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

    private static void readFaces(JsonObject faces, ModelElement element) {
        for (Entry<String, JsonElement> entry : faces.entrySet()) {
            ForgeDirection side = toDirection(entry.getKey());
            if (side == ForgeDirection.UNKNOWN) {
                continue;
            }

            JsonObject faceJson = entry.getValue()
                .getAsJsonObject();
            JsonArray uv = faceJson.getAsJsonArray("uv");
            String cullFace = faceJson.has("cullface") ? faceJson.get("cullface")
                .getAsString() : null;
            int rotation = faceJson.has("rotation") ? faceJson.get("rotation")
                .getAsInt() : 0;
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
                    rotation));
        }
    }

    private static void readDisplay(JsonObject json, ModernModel model) {
        if (!json.has("display")) {
            return;
        }

        JsonObject display = json.getAsJsonObject("display");
        if (!display.has("gui")) {
            return;
        }

        JsonObject gui = display.getAsJsonObject("gui");
        double[] rotation = gui.has("rotation") ? readVector(gui.getAsJsonArray("rotation"))
            : ModelDisplayTransform.GUI_DEFAULT.getRotation();
        double[] translation = gui.has("translation") ? readVector(gui.getAsJsonArray("translation"))
            : ModelDisplayTransform.GUI_DEFAULT.getTranslation();
        double[] scale = gui.has("scale") ? readVector(gui.getAsJsonArray("scale"))
            : ModelDisplayTransform.GUI_DEFAULT.getScale();
        model.setGuiTransform(new ModelDisplayTransform(rotation, translation, scale));
    }

    private static double[] readVector(JsonArray array) {
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
}
