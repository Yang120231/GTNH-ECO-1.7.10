package cn.dancingsnow.neoecoae.platform.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The upstream one-by-two-pixel, full-bright drive indicator in the native TESR entry point. */
@SideOnly(Side.CLIENT)
public final class NativeDriveLedRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity raw, double x, double y, double z, float partialTick) {
        ECODriveBlockEntity drive = (ECODriveBlockEntity) raw;
        int color;
        if (ECOInfiniteStorageMember.isMember(drive.getCellStack())) color = 0xca6cff;
        else {
            if (!drive.isMounted() || !drive.isOnline() || drive.getCellState() == CellState.ABSENT) return;
            color = drive.getCellState()
                .getStateColor();
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(-ledRotation(drive.getBlockState()), 0, 1, 0);
        GL11.glTranslated(-0.5, 0, -0.5);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        float previousX = OpenGlHelper.lastBrightnessX, previousY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_I(color);
        addLedVertices(tessellator);
        tessellator.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousX, previousY);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    static int ledRotation(BlockState state) {
        // Match eco_drive.json: the north-facing cell is rotated around the block's Y axis.
        Direction facing = state.getValue(NativeMachineBlock.FACING);
        return facing == Direction.EAST ? 90 : facing == Direction.SOUTH ? 180 : facing == Direction.WEST ? 270 : 0;
    }

    static void addLedVertices(Tessellator tessellator) {
        // Cover the housing's black slot at x=3..4, y=11..13 pixels, just outside its front face.
        tessellator.addVertex(3 / 16.0, 11 / 16.0, -0.001);
        tessellator.addVertex(4 / 16.0, 11 / 16.0, -0.001);
        tessellator.addVertex(4 / 16.0, 13 / 16.0, -0.001);
        tessellator.addVertex(3 / 16.0, 13 / 16.0, -0.001);
    }
}
