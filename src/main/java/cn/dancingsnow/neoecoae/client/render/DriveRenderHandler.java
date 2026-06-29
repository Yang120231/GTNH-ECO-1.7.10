package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cn.dancingsnow.neoecoae.tile.TileECODrive;
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
        TileECODrive ecoDriveTile = getEcoDriveTile(world, x, y, z);
        boolean occupied = drive.useFullModelWhenOccupied() && ecoDriveTile != null && ecoDriveTile.hasCell();
        boolean lit = ecoDriveTile != null && ecoDriveTile.isOnlineForRender();
        ModelFacing facing = ModelFacing.fromMeta(world.getBlockMetadata(x, y, z));
        EcoModelRenderer.renderWorld(
            DriveModels
                .get(
                    drive,
                    formed && drive.useFullModelWhenFormed() || occupied ? DriveVisualState.FULL
                        : DriveVisualState.EMPTY),
            facing,
            lit ? drive.getFormedModelIcons() : drive.getModelIcons(),
            world,
            x,
            y,
            z,
            block,
            renderer);
        renderInsertedEcoCell(world, x, y, z, block, renderer, drive, occupied, facing, ecoDriveTile);
        return true;
    }

    private static void renderInsertedEcoCell(IBlockAccess world, int x, int y, int z, Block block,
        RenderBlocks renderer, BlockModelDrive drive, boolean occupied, ModelFacing facing, TileECODrive ecoDriveTile) {
        if (!occupied || ecoDriveTile == null) {
            return;
        }
        EcoModelRenderer.renderWorld(
            ECOStorageCellRenderModels.getDriveCell(ecoDriveTile.getCellTierForRender()),
            facing,
            drive.getModelIcons(),
            world,
            x,
            y,
            z,
            block,
            renderer);
    }

    private static TileECODrive getEcoDriveTile(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECODrive) {
            return (TileECODrive) tile;
        }
        if (Minecraft.getMinecraft().theWorld == null) {
            return null;
        }
        tile = Minecraft.getMinecraft().theWorld.getTileEntity(x, y, z);
        return tile instanceof TileECODrive ? (TileECODrive) tile : null;
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
