package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.recipe.SourceFluids;
import cpw.mods.fml.common.registry.GameRegistry;

/** Missing vanilla material backports are platform additions, separate from the 70 locked core blocks. */
public final class RecipeMaterials {

    public static final Block BLUE_ICE = new Block(Material.packedIce) {};

    private RecipeMaterials() {}

    public static void register() {
        BLUE_ICE.setBlockName("neoecoae.platform_blue_ice")
            .setBlockTextureName("neoecoae:platform_blue_ice")
            .setHardness(2.8F)
            .setStepSound(Block.soundTypeGlass);
        BLUE_ICE.slipperiness = 0.989F;
        GameRegistry.registerBlock(BLUE_ICE, "platform_blue_ice");
        GameRegistry.addRecipe(new ItemStack(BLUE_ICE), "III", "III", "III", 'I', net.minecraft.init.Blocks.packed_ice);
        SourceFluids.initialize();
    }
}
