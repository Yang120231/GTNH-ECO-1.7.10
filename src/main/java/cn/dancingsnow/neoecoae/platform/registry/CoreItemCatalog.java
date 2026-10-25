package cn.dancingsnow.neoecoae.platform.registry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Factories for the actual item kinds declared by the locked NEItems, with fail-fast parity checking. */
public final class CoreItemCatalog {

    private static final JsonObject DATA = load();
    private static final Map<Item, net.minecraft.item.EnumRarity> RARITY = new java.util.IdentityHashMap<>();

    private CoreItemCatalog() {}

    public static net.minecraft.item.EnumRarity rarity(Item item) {
        return RARITY.getOrDefault(item, net.minecraft.item.EnumRarity.common);
    }

    private static JsonObject load() {
        var stream = CoreItemCatalog.class.getResourceAsStream("/neoecoae/core-content.json");
        if (stream == null) throw new IllegalStateException("Missing source item catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read source item catalog", error);
        }
    }

    static void registerSimpleItems() {
        Map<String, Item.ToolMaterial> tiers = new LinkedHashMap<>();
        for (var entry : DATA.getAsJsonObject("tool_tiers")
            .entrySet()) {
            var definition = entry.getValue()
                .getAsJsonObject();
            tiers.put(
                entry.getKey(),
                EnumHelper.addToolMaterial(
                    "NEOECOAE_" + entry.getKey(),
                    definition.get("harvest_level")
                        .getAsInt(),
                    definition.get("uses")
                        .getAsInt(),
                    definition.get("speed")
                        .getAsFloat(),
                    definition.get("attack")
                        .getAsFloat(),
                    definition.get("enchantability")
                        .getAsInt()));
        }
        for (var value : DATA.getAsJsonArray("items")) {
            var entry = value.getAsJsonObject();
            if (!entry.get("enabled")
                .getAsBoolean()) continue;
            String id = entry.get("id")
                .getAsString(),
                kind = entry.get("kind")
                    .getAsString();
            switch (kind) {
                case "material":
                case "workstation_module":
                case "smithing_template":
                case "infinite_component":
                    NativeContent.register(id, new NativeMaterialItem(id));
                    break;
                case "tool":
                    NativeContent.register(id, tool(entry, tiers));
                    break;
                case "ECOComputationCellItem":
                case "ECOStorageCellItem":
                case "ECOInfiniteResourceCellItem":
                    break; // Dedicated factories below install their real inventories and contracts.
                default:
                    throw new IllegalStateException("Unsupported source item factory: " + kind);
            }
        }
        for (var entry : tiers.entrySet()) entry.getValue()
            .setRepairItem(
                new ItemStack(
                    java.util.Objects.requireNonNull(
                        NativeContent.item(
                            entry.getKey()
                                .toLowerCase(java.util.Locale.ROOT) + "_ingot"))));
    }

    private static Item tool(JsonObject entry, Map<String, Item.ToolMaterial> tiers) {
        Item.ToolMaterial material = java.util.Objects.requireNonNull(
            tiers.get(
                entry.get("tier")
                    .getAsString()));
        Item item;
        double attack = material.getDamageVsEntity() + entry.get("attack_base")
            .getAsDouble();
        switch (entry.get("tool")
            .getAsString()) {
            case "axe":
                item = new ItemAxe(material) {

                    @Override
                    public com.google.common.collect.Multimap getItemAttributeModifiers() {
                        return NativeToolAttributes.damage(super.getItemAttributeModifiers(), attack);
                    }
                };
                break;
            case "hoe":
                item = new ItemHoe(material) {

                    @Override
                    public com.google.common.collect.Multimap getItemAttributeModifiers() {
                        return NativeToolAttributes.damage(super.getItemAttributeModifiers(), attack);
                    }
                };
                break;
            case "pickaxe":
                item = new ItemPickaxe(material) {

                    @Override
                    public com.google.common.collect.Multimap getItemAttributeModifiers() {
                        return NativeToolAttributes.damage(super.getItemAttributeModifiers(), attack);
                    }
                };
                break;
            case "shovel":
                item = new ItemSpade(material) {

                    @Override
                    public com.google.common.collect.Multimap getItemAttributeModifiers() {
                        return NativeToolAttributes.damage(super.getItemAttributeModifiers(), attack);
                    }
                };
                break;
            case "sword":
                item = new ItemSword(material) {

                    @Override
                    public com.google.common.collect.Multimap getItemAttributeModifiers() {
                        return NativeToolAttributes.damage(super.getItemAttributeModifiers(), attack);
                    }
                };
                break;
            default:
                throw new IllegalStateException("Unknown source tool: " + entry);
        }
        return item.setTextureName(
            "neoecoae:" + entry.get("id")
                .getAsString());
    }

    public static void checkParity() {
        java.util.Set<String> expected = new java.util.LinkedHashSet<>();
        for (var value : DATA.getAsJsonArray("items")) {
            var entry = value.getAsJsonObject();
            if (!entry.get("enabled")
                .getAsBoolean()) continue;
            String id = entry.get("id")
                .getAsString();
            expected.add(id);
            Item item = NativeContent.item(id);
            if (item == null || cpw.mods.fml.common.registry.GameRegistry.findItem("neoecoae", id) != item)
                throw new IllegalStateException("Missing native source item registration: " + id);
            item.setMaxStackSize(
                entry.get("stack_limit")
                    .getAsInt());
            RARITY.put(
                item,
                net.minecraft.item.EnumRarity.valueOf(
                    entry.get("rarity")
                        .getAsString()
                        .toLowerCase(java.util.Locale.ROOT)));
            if (!entry.get("creative")
                .getAsBoolean()) item.setCreativeTab(null);
        }
        if (!expected.equals(
            NativeContent.items()
                .keySet()))
            throw new IllegalStateException("Core item catalog differs from native registrations");
        cn.dancingsnow.neoecoae.NeoECOAE.LOG.info(
            "NEOECOAE_CORE_ITEMS_REGISTERED count={} source={}",
            expected.size(),
            DATA.get("source_commit")
                .getAsString());
    }
}
