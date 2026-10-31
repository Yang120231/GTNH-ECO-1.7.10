package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.JsonObject;

import appeng.api.AEApi;

/** Modern item tags translated to source members and GTNH material ore names. */
public final class SourceTags {

    private static final Map<String, JsonObject> TAGS = new LinkedHashMap<>();
    private static final Map<String, List<ItemStack>> NATIVE = new LinkedHashMap<>();

    private SourceTags() {}

    static void add(String id, JsonObject definition) {
        TAGS.put(id, definition);
    }

    public static void initialize(JsonObject catalog) {
        for (var entry : catalog.getAsJsonArray("tags")) {
            String path = entry.getAsJsonObject()
                .get("path")
                .getAsString();
            int start = path.indexOf("/tags/item/");
            String id = path.substring(5, start) + ":" + path.substring(start + 11, path.length() - 5);
            TAGS.put(id, SourceRecipes.read(path));
        }
        var m = AEApi.instance()
            .definitions()
            .materials();
        nativeItem("c:ingots/iron", new ItemStack(net.minecraft.init.Items.iron_ingot));
        nativeItem("c:storage_blocks/iron", new ItemStack(net.minecraft.init.Blocks.iron_block));
        nativeItem("c:dusts/redstone", new ItemStack(net.minecraft.init.Items.redstone));
        nativeItem(
            "c:dusts/iron",
            m.ironDust()
                .maybeStack(1)
                .orNull());
        nativeItem(
            "c:dusts/ender_pearl",
            m.enderDust()
                .maybeStack(1)
                .orNull());
        nativeItem(
            "c:dusts/sky_stone",
            m.skyDust()
                .maybeStack(1)
                .orNull());
        nativeItem(
            "c:dusts/certus_quartz",
            m.certusQuartzDust()
                .maybeStack(1)
                .orNull());
        nativeItem(
            "c:dusts/fluix",
            m.fluixDust()
                .maybeStack(1)
                .orNull());
        nativeItem(
            "c:gems/fluix",
            m.fluixCrystal()
                .maybeStack(1)
                .orNull());
        nativeItem(
            "c:silicon",
            m.silicon()
                .maybeStack(1)
                .orNull());
        for (var entry : TAGS.entrySet()) {
            String ore = oreName(entry.getKey());
            if (ore == null) continue;
            for (var stack : resolve(entry.getKey(), new HashSet<>(), false))
                OreDictionary.registerOre(ore, stack.copy());
        }
    }

    private static void nativeItem(String tag, ItemStack stack) {
        if (stack != null) NATIVE.computeIfAbsent(tag, key -> new ArrayList<>())
            .add(stack);
    }

    public static List<ItemStack> items(String tag) {
        return resolve(tag, new HashSet<>(), true);
    }

    private static List<ItemStack> resolve(String tag, Set<String> visiting, boolean ore) {
        if (!visiting.add(tag)) throw new IllegalStateException("Recursive source tag: " + tag);
        var result = new ArrayList<ItemStack>();
        if (NATIVE.containsKey(tag)) result.addAll(NATIVE.get(tag));
        if (tag.equals("ae2:smart_dense_cable")) java.util.Collections.addAll(
            result,
            AEApi.instance()
                .definitions()
                .parts()
                .cableDense()
                .allStacks(1));
        if (tag.equals("ae2:covered_dense_cable")) java.util.Collections.addAll(
            result,
            AEApi.instance()
                .definitions()
                .parts()
                .cableDenseCovered()
                .allStacks(1));
        JsonObject definition = TAGS.get(tag);
        if (definition != null) for (var value : definition.getAsJsonArray("values")) {
            String id = value.isJsonPrimitive() ? value.getAsString()
                : value.getAsJsonObject()
                    .get("id")
                    .getAsString();
            if (id.startsWith("#")) result.addAll(resolve(id.substring(1), visiting, ore));
            else {
                ItemStack stack = SourceItems.stack(id, 1);
                if (stack != null) result.add(stack);
            }
        }
        String oreId = oreName(tag);
        if (ore && oreId != null) result.addAll(OreDictionary.getOres(oreId));
        if (ore && "c:ingots/aluminum".equals(tag)) result.addAll(OreDictionary.getOres("ingotAluminium"));
        if (ore && "c:dusts/aluminum".equals(tag)) result.addAll(OreDictionary.getOres("dustAluminium"));
        visiting.remove(tag);
        return result;
    }

    public static String oreName(String tag) {
        if (tag.startsWith("gtceu:circuits/")) {
            String tier = tag.substring("gtceu:circuits/".length());
            switch (tier) {
                case "hv":
                    return "circuitAdvanced";
                case "ev":
                    return "circuitData";
                case "iv":
                    return "circuitElite";
                case "luv":
                    return "circuitMaster";
                case "zpm":
                    return "circuitUltimate";
                case "uhv":
                    return "circuitInfinite";
                default:
                    throw new IllegalArgumentException("Unmapped GT circuit tier: " + tag);
            }
        }
        if (!tag.startsWith("c:")) return null;
        String[] path = tag.substring(2)
            .split("/", 2);
        if (path.length == 1) return "c:silicon".equals(tag) ? "itemSilicon" : null;
        String prefix;
        switch (path[0]) {
            case "ingots":
                prefix = "ingot";
                break;
            case "dusts":
                prefix = "dust";
                break;
            case "gems":
                prefix = "gem";
                break;
            case "ores":
                prefix = "ore";
                break;
            case "storage_blocks":
                prefix = "block";
                break;
            case "raw_materials":
                prefix = "rawOre";
                break;
            default:
                return null;
        }
        StringBuilder name = new StringBuilder(prefix);
        for (String word : path[1].split("_")) name.append(Character.toUpperCase(word.charAt(0)))
            .append(word.substring(1));
        return name.toString();
    }
}
