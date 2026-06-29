package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cn.dancingsnow.neoecoae.tile.TileECODrive;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class DriveRenderHandler implements ISimpleBlockRenderingHandler {

    private static final int FULL_BRIGHTNESS = 15728880;
    private static final double PIXEL = 1.0D / 16.0D;
    private static final double LED_EPSILON = 0.001D;
    private static final double LED_MIN_X = 3.0D * PIXEL;
    private static final double LED_MAX_X = 4.0D * PIXEL;
    private static final double LED_MIN_Y = 11.0D * PIXEL;
    private static final double LED_MAX_Y = 13.0D * PIXEL;
    private static final double LED_Z = -LED_EPSILON;
    private static final double LED_TEXTURE_U_MIN = 2.0D;
    private static final double LED_TEXTURE_U_MAX = 3.0D;
    private static final double LED_TEXTURE_V_MIN = 1.0D;
    private static final double LED_TEXTURE_V_MAX = 2.0D;

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
        renderCellStatusLed(x, y, z, occupied, facing, ecoDriveTile, drive);
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

    private static void renderCellStatusLed(int x, int y, int z, boolean occupied, ModelFacing facing,
        TileECODrive ecoDriveTile, BlockModelDrive drive) {
        if (!occupied || ecoDriveTile == null || !ecoDriveTile.isOnlineForRender()) {
            return;
        }
        int color = ecoDriveTile.getCellLedColorForRender();
        if ((color & 0x00FFFFFF) == 0) {
            return;
        }

        IIcon icon = drive.getModelIcons()
            .get("neoecoae:block/storage/drive/drive_north");
        if (icon == null) {
            return;
        }

        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        Tessellator tessellator = Tessellator.instance;
        tessellator.setBrightness(FULL_BRIGHTNESS);
        tessellator.setColorOpaque_F(red, green, blue);

        double[][] vertices = new double[][] {
            rotate(LED_MIN_X, LED_MAX_Y, LED_Z, facing),
            rotate(LED_MAX_X, LED_MAX_Y, LED_Z, facing),
            rotate(LED_MAX_X, LED_MIN_Y, LED_Z, facing),
            rotate(LED_MIN_X, LED_MIN_Y, LED_Z, facing) };
        double minU = icon.getInterpolatedU(LED_TEXTURE_U_MIN);
        double maxU = icon.getInterpolatedU(LED_TEXTURE_U_MAX);
        double minV = icon.getInterpolatedV(LED_TEXTURE_V_MIN);
        double maxV = icon.getInterpolatedV(LED_TEXTURE_V_MAX);
        tessellator.addVertexWithUV(x + vertices[0][0], y + vertices[0][1], z + vertices[0][2], minU, minV);
        tessellator.addVertexWithUV(x + vertices[1][0], y + vertices[1][1], z + vertices[1][2], maxU, minV);
        tessellator.addVertexWithUV(x + vertices[2][0], y + vertices[2][1], z + vertices[2][2], maxU, maxV);
        tessellator.addVertexWithUV(x + vertices[3][0], y + vertices[3][1], z + vertices[3][2], minU, maxV);
    }

    private static double[] rotate(double modelX, double modelY, double modelZ, ModelFacing facing) {
        double x = modelX - 0.5D;
        double z = modelZ - 0.5D;
        double rotatedX = x;
        double rotatedZ = z;

        switch (facing) {
            case EAST:
                rotatedX = -z;
                rotatedZ = x;
                break;
            case SOUTH:
                rotatedX = -x;
                rotatedZ = -z;
                break;
            case WEST:
                rotatedX = z;
                rotatedZ = -x;
                break;
            case NORTH:
            default:
                break;
        }

        return new double[] { rotatedX + 0.5D, modelY, rotatedZ + 0.5D };
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
