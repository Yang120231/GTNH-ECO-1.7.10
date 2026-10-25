package cn.dancingsnow.neoecoae.platform.registry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;

import com.google.gson.JsonParser;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Real ordinary block factories from the locked NEBlocks. Every machine continues to require its actual tile
 * implementation.
 */
public final class BasicBlocks {

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    private BasicBlocks() {}

    public static void register() {
        var stream = BasicBlocks.class.getResourceAsStream("/neoecoae/core-blocks.json");
        if (stream == null) throw new IllegalStateException("Missing source block catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var data = new JsonParser().parse(reader)
                .getAsJsonObject();
            for (var value : data.getAsJsonArray("blocks")) {
                var properties = new NativeBlockProperties(value.getAsJsonObject());
                Block block = properties.frame ? new cn.dancingsnow.neoecoae.blocks.CasingBlock(properties)
                    : new NativeSimpleModelBlock(properties);
                if (BLOCKS.put(properties.id, block) != null)
                    throw new IllegalArgumentException("Duplicate basic block " + properties.id);
                GameRegistry.registerBlock(block, NativeMachineItemBlock.class, properties.id);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read source block catalog", error);
        }
        cn.dancingsnow.neoecoae.NeoECOAE.LOG.info("NEOECOAE_BASIC_BLOCKS_REGISTERED count={}", BLOCKS.size());
    }

    public static Map<String, Block> blocks() {
        return java.util.Collections.unmodifiableMap(BLOCKS);
    }
}
