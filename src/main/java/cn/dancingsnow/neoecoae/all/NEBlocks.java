package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCompressed;
import net.minecraft.block.material.MapColor;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.block.BlockEcoDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.block.ItemBlockEcoDrive;
import cn.dancingsnow.neoecoae.block.ItemBlockModernModel;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NEBlocks {

    public static final Block aluminumOre = ore("aluminum_ore", NEItems.rawAluminumOre, 3.0F, 5.0F, 2);
    public static final Block tungstenOre = ore("tungsten_ore", NEItems.rawTungstenOre, 4.0F, 8.0F, 3);

    public static final Block rawAluminumBlock = storageBlock("raw_aluminum_block", MapColor.ironColor, 5.0F, 10.0F, 2);
    public static final Block rawTungstenBlock = storageBlock("raw_tungsten_block", MapColor.ironColor, 6.0F, 12.0F, 3);
    public static final Block aluminumBlock = storageBlock("aluminum_block", MapColor.ironColor, 5.0F, 10.0F, 2);
    public static final Block tungstenBlock = storageBlock("tungsten_block", MapColor.ironColor, 6.0F, 12.0F, 3);
    public static final Block aluminumAlloyBlock = storageBlock(
        "aluminum_alloy_block",
        MapColor.ironColor,
        5.0F,
        10.0F,
        2);
    public static final Block blackTungstenAlloyBlock = storageBlock(
        "black_tungsten_alloy_block",
        MapColor.ironColor,
        6.0F,
        12.0F,
        3);
    public static final Block aluminumAlloyCasing = modelBlock(
        "aluminum_alloy_casing",
        "aluminum_alloy_casing",
        new String[] { NeoECOAE.MODID + ":block/aluminum_alloy_casing" });
    public static final Block blackTungstenAlloyCasing = modelBlock(
        "black_tungsten_alloy_casing",
        "black_tungsten_alloy_casing",
        new String[] { NeoECOAE.MODID + ":block/black_tungsten_alloy_casing" });
    public static final Block ecoDrive = new BlockEcoDrive();

    private NEBlocks() {}

    public static void register() {
        register(aluminumOre, "aluminum_ore");
        register(tungstenOre, "tungsten_ore");
        register(rawAluminumBlock, "raw_aluminum_block");
        register(rawTungstenBlock, "raw_tungsten_block");
        register(aluminumBlock, "aluminum_block");
        register(tungstenBlock, "tungsten_block");
        register(aluminumAlloyBlock, "aluminum_alloy_block");
        register(blackTungstenAlloyBlock, "black_tungsten_alloy_block");
        GameRegistry.registerBlock(aluminumAlloyCasing, ItemBlockModernModel.class, "aluminum_alloy_casing");
        GameRegistry.registerBlock(blackTungstenAlloyCasing, ItemBlockModernModel.class, "black_tungsten_alloy_casing");
        GameRegistry.registerBlock(ecoDrive, ItemBlockEcoDrive.class, "eco_drive");
    }

    private static Block ore(String id, net.minecraft.item.Item droppedItem, float hardness, float resistance,
        int harvestLevel) {
        Block block = new NEBlockOre(droppedItem).setBlockName(id)
            .setBlockTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(hardness)
            .setResistance(resistance)
            .setStepSound(Block.soundTypePiston);
        block.setHarvestLevel("pickaxe", harvestLevel);
        return block;
    }

    private static Block storageBlock(String id, MapColor color, float hardness, float resistance, int harvestLevel) {
        Block block = new BlockCompressed(color).setBlockName(id)
            .setBlockTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE)
            .setHardness(hardness)
            .setResistance(resistance)
            .setStepSound(Block.soundTypeMetal);
        block.setHarvestLevel("pickaxe", harvestLevel);
        return block;
    }

    private static Block modelBlock(String id, String modelName, String[] textures) {
        return new BlockModernModel(id, modelName, textures);
    }

    private static void register(Block block, String id) {
        GameRegistry.registerBlock(block, id);
    }
}
