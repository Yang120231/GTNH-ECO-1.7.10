package cn.dancingsnow.neoecoae.platform.render;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Generated item layers use native multipass sprites. Cuboid models continue through the preserved model chain. */
@SideOnly(Side.CLIENT)
public final class ItemModelLayers {

    private ItemModelLayers() {}

    public static IIcon[] icons(String id, IIconRegister register) {
        SortedMap<String, String> layers = new TreeMap<>();
        load(new ResourceLocation("neoecoae", "models/item/" + id + ".json"), layers, new HashSet<>());
        if (layers.isEmpty()) throw new IllegalStateException("No generated item layers for " + id);
        IIcon[] icons = new IIcon[layers.size()];
        int index = 0;
        for (String texture : layers.values()) icons[index++] = register.registerIcon(texture.replace(":item/", ":"));
        return icons;
    }

    private static void load(ResourceLocation location, Map<String, String> layers, Set<String> active) {
        if (!active.add(location.toString()) || active.size() > 16)
            throw new IllegalArgumentException("Cyclic item model parent: " + location);
        try (Reader input = new InputStreamReader(
            Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location)
                .getInputStream(),
            StandardCharsets.UTF_8)) {
            JsonObject model = new JsonParser().parse(input)
                .getAsJsonObject();
            if (model.has("parent")) {
                String parent = model.get("parent")
                    .getAsString();
                if (!parent.equals("minecraft:item/generated") && !parent.equals("item/generated")) {
                    ResourceLocation resource = new ResourceLocation(parent);
                    load(
                        new ResourceLocation(
                            resource.getResourceDomain(),
                            "models/" + resource.getResourcePath() + ".json"),
                        layers,
                        active);
                }
            }
            if (model.has("textures")) for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("textures")
                .entrySet())
                if (entry.getKey()
                    .startsWith("layer"))
                    layers.put(
                        entry.getKey(),
                        entry.getValue()
                            .getAsString());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load imported item model " + location, e);
        }
        active.remove(location.toString());
    }
}
