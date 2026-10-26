package cn.dancingsnow.neoecoae.platform.registry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.registry.GameRegistry;

/** Complete source identities, including blocks whose machine behavior is still being ported. */
public final class CoreBlocks {

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, net.minecraft.item.EnumRarity> RARITIES = new LinkedHashMap<>();
    private static int pending;

    private CoreBlocks() {}

    public static void register() {
        if (!BLOCKS.isEmpty()) throw new IllegalStateException("Core blocks registered twice");
        var stream = CoreBlocks.class.getResourceAsStream("/neoecoae/core-registration.json");
        if (stream == null) throw new IllegalStateException("Missing complete source block catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var catalog = new JsonParser().parse(reader)
                .getAsJsonObject();
            for (var value : catalog.getAsJsonArray("blocks")) {
                var definition = value.getAsJsonObject();
                String id = definition.get("id")
                    .getAsString();
                Block block = GameRegistry.findBlock("neoecoae", id);
                if (definition.get("registration")
                    .getAsString()
                    .equals("pending_behavior")) {
                    if (block != null) throw new IllegalStateException("Duplicate pending block: " + id);
                    block = new NativeRegisteredBlock(definition);
                    GameRegistry.registerBlock(block, NativeMachineItemBlock.class, id);
                    pending++;
                } else if (block == null) throw new IllegalStateException("Missing existing core block: " + id);
                if (BLOCKS.put(id, block) != null) throw new IllegalStateException("Duplicate source block: " + id);
                RARITIES.put(
                    id,
                    net.minecraft.item.EnumRarity.valueOf(
                        definition.get("rarity")
                            .getAsString()
                            .toLowerCase(java.util.Locale.ROOT)));
                Item item = GameRegistry.findItem("neoecoae", id);
                if (item == null || item != Item.getItemFromBlock(block))
                    throw new IllegalStateException("Missing block item: " + id);
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read complete source block catalog", failure);
        }
        NeoECOAE.LOG.info("NEOECOAE_CORE_BLOCKS_REGISTERED total={} pending_behavior={}", BLOCKS.size(), pending);
    }

    public static net.minecraft.item.EnumRarity rarity(String id) {
        return RARITIES.getOrDefault(id, net.minecraft.item.EnumRarity.common);
    }

    public static Map<String, Block> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    public static int pendingBehaviorCount() {
        return pending;
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static void checkCreativeItems() {
        for (var entry : BLOCKS.entrySet()) {
            var stacks = new java.util.ArrayList<ItemStack>();
            Item item = Item.getItemFromBlock(entry.getValue());
            if (entry.getValue()
                .getCreativeTabToDisplayOn() != NativeContent.TAB)
                throw new IllegalStateException("Core block missing creative tab: " + entry.getKey());
            item.getSubItems(item, NativeContent.TAB, stacks);
            if (stacks.stream()
                .noneMatch(stack -> stack != null && stack.getItem() == item))
                throw new IllegalStateException("Core block missing from creative listing: " + entry.getKey());
        }
    }
}
