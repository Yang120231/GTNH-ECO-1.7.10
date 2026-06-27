package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.block.BlockEcoDrive;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class EcoDriveRenderHandler implements ISimpleBlockRenderingHandler {

    private final int renderId;

    public EcoDriveRenderHandler(int renderId) {
        this.renderId = renderId;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        if (block instanceof BlockEcoDrive) {
            EcoModelRenderer.renderInventoryBlock(
                EcoDriveModels.get(EcoDriveVisualState.EMPTY),
                ((BlockEcoDrive) block).getModelIcons());
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        if (!(block instanceof BlockEcoDrive)) {
            return false;
        }

        EcoModelRenderer.renderWorld(
            EcoDriveModels.get(EcoDriveVisualState.EMPTY),
            ModelFacing.fromMeta(world.getBlockMetadata(x, y, z)),
            ((BlockEcoDrive) block).getModelIcons(),
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
