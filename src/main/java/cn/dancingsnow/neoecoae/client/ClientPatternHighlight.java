package cn.dancingsnow.neoecoae.client;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

/** Short-lived client marker for a pattern upload target. */
public final class ClientPatternHighlight {

    private static int dimension;
    private static int x;
    private static int y;
    private static int z;
    private static long expiresAt;

    private ClientPatternHighlight() {}

    public static synchronized void set(int dimension, int x, int y, int z) {
        ClientPatternHighlight.dimension = dimension;
        ClientPatternHighlight.x = x;
        ClientPatternHighlight.y = y;
        ClientPatternHighlight.z = z;
        expiresAt = System.currentTimeMillis() + 8_000L;
    }

    public static synchronized void render(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null
            || minecraft.renderViewEntity == null
            || System.currentTimeMillis() > expiresAt
            || minecraft.theWorld.provider.dimensionId != dimension) {
            return;
        }
        float partialTicks = event.partialTicks;
        double viewX = interpolate(
            minecraft.renderViewEntity.lastTickPosX,
            minecraft.renderViewEntity.posX,
            partialTicks);
        double viewY = interpolate(
            minecraft.renderViewEntity.lastTickPosY,
            minecraft.renderViewEntity.posY,
            partialTicks);
        double viewZ = interpolate(
            minecraft.renderViewEntity.lastTickPosZ,
            minecraft.renderViewEntity.posZ,
            partialTicks);
        // Match vanilla's small selection-box expansion to avoid z-fighting with the block model.
        Block block = minecraft.theWorld.getBlock(x, y, z);
        if (block == null || block.getMaterial() == Material.air) return;
        block.setBlockBoundsBasedOnState(minecraft.theWorld, x, y, z);
        AxisAlignedBB box = block.getSelectedBoundingBoxFromPool(minecraft.theWorld, x, y, z);
        if (box == null) return;
        box = box.expand(0.002D, 0.002D, 0.002D)
            .getOffsetBoundingBox(-viewX, -viewY, -viewZ);

        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 0.08F, 0.08F, 0.95F);
        GL11.glLineWidth(2.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        RenderGlobal.drawOutlinedBoundingBox(box, -1);
        GL11.glDepthMask(true);
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
        else GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (textureEnabled) GL11.glEnable(GL11.GL_TEXTURE_2D);
        else GL11.glDisable(GL11.GL_TEXTURE_2D);
        if (blendEnabled) GL11.glEnable(GL11.GL_BLEND);
        else GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }
}
