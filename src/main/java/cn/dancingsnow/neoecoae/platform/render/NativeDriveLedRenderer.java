package cn.dancingsnow.neoecoae.platform.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
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
        // The block's facing points toward its back; the cell LED is rendered on
        // the opposite, player-facing side (matching AE2's CellLedRenderer).
        Direction facing = drive.getBlockState()
            .getValue(NativeMachineBlock.FACING)
            .getOpposite();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        int angle = facing == Direction.EAST ? 90
            : facing == Direction.SOUTH ? 180 : facing == Direction.WEST ? 270 : 0;
        GL11.glRotatef(-angle, 0, 1, 0);
        GL11.glTranslated(-0.5, 0, -0.5);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        float previousX = OpenGlHelper.lastBrightnessX, previousY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_I(color);
        tessellator.addVertex(3 / 16.0, 3 / 16.0, -0.001);
        tessellator.addVertex(4 / 16.0, 3 / 16.0, -0.001);
        tessellator.addVertex(4 / 16.0, 5 / 16.0, -0.001);
        tessellator.addVertex(3 / 16.0, 5 / 16.0, -0.001);
        tessellator.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousX, previousY);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
