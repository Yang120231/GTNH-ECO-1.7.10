package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.google.gson.JsonObject;

/** The used vanilla property copies have matching native materials, hardness and resistance. */
public final class NativeBlockProperties {

    public final String id, oreDrop;
    public final Block base;
    public final int harvestLevel;
    public final boolean frame, noOcclusion;
    public final float hardness, resistance;

    public NativeBlockProperties(JsonObject definition) {
        id = definition.get("id")
            .getAsString();
        String prototype = definition.get("base")
            .getAsString();
        switch (prototype) {
            case "IRON_ORE":
                base = Blocks.iron_ore;
                break;
            case "IRON_BLOCK":
            case "RAW_IRON_BLOCK":
                base = Blocks.iron_block;
                break;
            case "QUARTZ_BLOCK":
                base = Blocks.quartz_block;
                break;
            default:
                throw new IllegalArgumentException("Unmapped source block properties: " + prototype);
        }
        hardness = definition.has("hardness") ? definition.get("hardness")
            .getAsFloat() : base.getBlockHardness(null, 0, 0, 0);
        resistance = definition.has("resistance") ? definition.get("resistance")
            .getAsFloat() : base.getExplosionResistance(null);
        harvestLevel = definition.get("harvest_level")
            .getAsInt();
        frame = definition.get("factory")
            .getAsString()
            .equals("frame");
        noOcclusion = definition.get("no_occlusion")
            .getAsBoolean();
        oreDrop = definition.has("ore_drop") ? definition.get("ore_drop")
            .getAsString() : null;
    }
}
