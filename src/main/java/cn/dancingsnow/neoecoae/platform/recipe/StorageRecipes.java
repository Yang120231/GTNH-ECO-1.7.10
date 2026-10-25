package cn.dancingsnow.neoecoae.platform.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cpw.mods.fml.common.registry.GameRegistry;

/** Interprets the imported assembly and disassembly data rather than duplicating its ingredient tables. */
public final class StorageRecipes {

    private static final Map<Item, List<ItemStack>> DISASSEMBLY = new IdentityHashMap<>();

    private StorageRecipes() {}

    public static void register() {
        for (NativeStorageCellItem cell : NativeContent.cells()) {
            String id = NativeContent.id(cell);
            JsonObject assembly = read(id);
            if (!"minecraft:crafting_shapeless".equals(
                assembly.get("type")
                    .getAsString()))
                throw new IllegalStateException("Unexpected assembly recipe for " + id);
            JsonObject disassembly = read("disassembly/" + id);
            if (!"ae2:storage_cell_disassembly".equals(
                disassembly.get("type")
                    .getAsString())
                || !disassembly.get("cell")
                    .getAsString()
                    .equals("neoecoae:" + id))
                throw new IllegalStateException("Unexpected disassembly identity for " + id);
            List<ItemStack> returns = new ArrayList<>();
            for (JsonElement value : disassembly.getAsJsonArray("cell_disassembly_items")) {
                JsonObject object = value.getAsJsonObject();
                returns.add(
                    stack(
                        object.get("id")
                            .getAsString(),
                        object.get("count")
                            .getAsInt()));
            }
            if (returns.isEmpty()) throw new IllegalStateException("Empty disassembly recipe for " + id);
            DISASSEMBLY.put(cell, Collections.unmodifiableList(returns));
        }
    }

    public static List<ItemStack> disassemble(Item item) {
        List<ItemStack> values = DISASSEMBLY.get(item);
        if (values == null) return Collections.emptyList();
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : values) copies.add(stack.copy());
        return copies;
    }

    public static boolean hasDisassembly(String id) {
        return id.startsWith("neoecoae:") && DISASSEMBLY.containsKey(NativeContent.item(id.substring(9)));
    }

    private static ItemStack stack(String id, int count) {
        int separator = id.indexOf(':');
        if (separator < 1) throw new IllegalArgumentException("Invalid item id: " + id);
        Item item = GameRegistry.findItem(id.substring(0, separator), id.substring(separator + 1));
        if (item == null || count <= 0) throw new IllegalStateException("Unregistered recipe item: " + id);
        return new ItemStack(item, count);
    }

    private static JsonObject read(String name) {
        String resource = "/data/neoecoae/recipe/" + name + ".json";
        InputStream stream = StorageRecipes.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing imported recipe " + resource);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read recipe " + resource, e);
        }
    }
}
