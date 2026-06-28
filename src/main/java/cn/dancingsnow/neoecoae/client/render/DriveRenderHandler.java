package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class DriveRenderHandler implements ISimpleBlockRenderingHandler {

    private final int renderId;

    public DriveRenderHandler(int renderId) {
        this.renderId = renderId;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        if (block instanceof BlockModelDrive) {
            BlockModelDrive drive = (BlockModelDrive) block;
            EcoModelRenderer
                .renderInventoryBlock(DriveModels.get(drive, DriveVisualState.EMPTY), drive.getModelIcons());
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        if (!(block instanceof BlockModelDrive)) {
            return false;
        }
        if (ECOFormationVisibility.isHidden(world, x, y, z)) {
            return true;
        }

        BlockModelDrive drive = (BlockModelDrive) block;
        boolean formed = ECOFormationVisibility.shouldRenderFormedMember(world, x, y, z);
        EcoModelRenderer.renderWorld(
            DriveModels
                .get(drive, formed && drive.useFullModelWhenFormed() ? DriveVisualState.FULL : DriveVisualState.EMPTY),
            ModelFacing.fromMeta(world.getBlockMetadata(x, y, z)),
            formed ? drive.getFormedModelIcons() : drive.getModelIcons(),
            world,
            x,
            y,
            z,
            block,
            renderer);
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
