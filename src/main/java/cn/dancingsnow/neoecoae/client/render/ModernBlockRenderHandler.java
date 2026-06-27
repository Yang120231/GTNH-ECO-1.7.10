package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class ModernBlockRenderHandler implements ISimpleBlockRenderingHandler {

    private final int renderId;

    public ModernBlockRenderHandler(int renderId) {
        this.renderId = renderId;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        if (block instanceof BlockModernModel) {
            BlockModernModel modelBlock = (BlockModernModel) block;
            EcoModelRenderer
                .renderInventoryBlock(ModernBlockModels.get(modelBlock.getModelName()), modelBlock.getModelIcons());
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        if (!(block instanceof BlockModernModel)) {
            return false;
        }

        BlockModernModel modelBlock = (BlockModernModel) block;
        EcoModelRenderer.renderWorld(
            ModernBlockModels.get(modelBlock.getModelName()),
            ModelFacing.NORTH,
            modelBlock.getModelIcons(),
            world,
            x,
            y,
            z,
            block);
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return this.renderId;
    }
}
