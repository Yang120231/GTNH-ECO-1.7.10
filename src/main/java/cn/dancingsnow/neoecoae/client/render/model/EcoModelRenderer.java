package cn.dancingsnow.neoecoae.client.render.model;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.platform.render.ModelRenderBridge;

public final class EcoModelRenderer {

    private static final int FULL_BRIGHTNESS = 15728880;

    private EcoModelRenderer() {}

    public static void renderWorld(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons,
        IBlockAccess world, int x, int y, int z, Block block) {
        renderWorld(model, facing, icons, world, x, y, z, block, null);
    }

    public static void renderWorld(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons,
        IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
        Tessellator tessellator = Tessellator.instance;
        ModelRenderLayer renderLayer = ModelRenderLayer.fromRenderPass(MinecraftForgeClient.getRenderPass());
        List<BakedQuad> quads = model.getQuads(facing, renderLayer);
        if (quads.isEmpty()) return;
        int checkedSides = 0;
        int culledSides = 0;
        int internalBrightness = -1;
        tessellator.addTranslation(x, y, z);
        try {
            for (BakedQuad quad : quads) {
                if (quad.isCullable()) {
                    int side = 1 << quad.getCullDirection()
                        .ordinal();
                    if ((checkedSides & side) == 0) {
                        checkedSides |= side;
                        if (shouldCull(world, x, y, z, block, quad.getCullDirection())) culledSides |= side;
                    }
                    if ((culledSides & side) != 0) continue;
                }
                IIcon icon = getWorldIcon(quad, icons, renderer);
                if (icon == null) continue;

                int brightness;
                if (quad.isFullBright()) brightness = FULL_BRIGHTNESS;
                else if (!quad.isBoundaryFace()) {
                    if (internalBrightness < 0) internalBrightness = ModelLightSampler
                        .getInternalBrightness(world, x, y, z, block, facing.getDirection());
                    brightness = internalBrightness;
                } else brightness = ModelLightSampler.getWorldBrightness(world, x, y, z, block, quad, facing);
                float shade = quad.isFullBright() || !quad.isShade() ? 1.0F : quad.getWorldShade();
                tessellator.setBrightness(brightness);
                ForgeDirection normal = quad.getNormal();
                tessellator.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
                tessellator.setColorOpaque_F(shade, shade, shade);
                submitQuad(tessellator, quad, icon);
            }
        } finally {
            tessellator.addTranslation(-x, -y, -z);
        }
    }

    public static void renderInventoryBlock(BakedEcoModel model, Map<String, IIcon> icons) {
        renderInventoryBlock(model, ModelFacing.NORTH, icons);
    }

    public static void renderInventoryBlock(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons) {
        GL11.glPushMatrix();
        try {
            GL11.glRotated(180.0D, 0.0D, 1.0D, 0.0D);
            GL11.glTranslated(-0.5D, -0.5D, -0.5D);
            renderInventoryQuads(model, facing, icons);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void renderItemModel(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons) {
        renderInventoryQuads(model, facing, icons);
    }

    private static IIcon getWorldIcon(BakedQuad quad, Map<String, IIcon> icons, RenderBlocks renderer) {
        if (renderer != null && renderer.hasOverrideBlockTexture()) {
            return renderer.overrideBlockTexture;
        }
        return icons.get(quad.getTexture());
    }

    private static boolean shouldCull(IBlockAccess world, int x, int y, int z, Block block,
        ForgeDirection cullDirection) {
        int neighborX = x + cullDirection.offsetX;
        int neighborY = y + cullDirection.offsetY;
        int neighborZ = z + cullDirection.offsetZ;
        if (ModelRenderBridge.isHidden(world, neighborX, neighborY, neighborZ)) {
            return false;
        }

        return !block.shouldSideBeRendered(world, neighborX, neighborY, neighborZ, cullDirection.ordinal());
    }

    private static float getInventoryShade(ForgeDirection normal) {
        if (normal == ForgeDirection.DOWN) {
            return 0.72F;
        }
        if (normal == ForgeDirection.UP) {
            return 1.0F;
        }
        if (normal == ForgeDirection.NORTH || normal == ForgeDirection.SOUTH) {
            return 0.86F;
        }
        return 0.78F;
    }

    private static void renderInventoryQuads(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons) {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);

            // ItemRenderer disables depth writes for blocks with a translucent world pass.
            // Their solid model faces still need depth writes to hide the rear faces.
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            renderInventoryQuads(model.getQuads(facing, ModelRenderLayer.CUTOUT), icons);

            List<BakedQuad> translucent = model.getQuads(facing, ModelRenderLayer.TRANSLUCENT);
            if (!translucent.isEmpty()) {
                GL11.glDepthMask(false);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                renderInventoryQuads(sortBackToFront(translucent), icons);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static List<BakedQuad> sortBackToFront(List<BakedQuad> quads) {
        if (quads.size() < 2) return quads;
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        double depthX = matrix.get(2), depthY = matrix.get(6), depthZ = matrix.get(10);
        List<BakedQuad> sorted = new ArrayList<>(quads);
        // View-space Z increases toward the camera, including held-item rotations.
        sorted.sort(
            Comparator.comparingDouble(
                quad -> depthX * quad.getSampleX() + depthY * quad.getSampleY() + depthZ * quad.getSampleZ()));
        return sorted;
    }

    private static void renderInventoryQuads(List<BakedQuad> quads, Map<String, IIcon> icons) {
        if (quads.isEmpty()) return;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (BakedQuad quad : quads) {
            IIcon icon = icons.get(quad.getTexture());
            if (icon == null) {
                continue;
            }

            ForgeDirection normal = quad.getNormal();
            float shade = quad.isFullBright() || !quad.isShade() ? 1.0F : getInventoryShade(normal);
            tessellator.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
            tessellator.setColorOpaque_F(shade, shade, shade);
            submitQuad(tessellator, quad, icon);
        }
        tessellator.draw();
    }

    private static void submitQuad(Tessellator tessellator, BakedQuad quad, IIcon icon) {
        double[][] vertices = quad.getVertices();
        double[][] uv = quad.getUv();
        for (int i = 0; i < vertices.length; i++) {
            tessellator.addVertexWithUV(
                vertices[i][0],
                vertices[i][1],
                vertices[i][2],
                icon.getInterpolatedU(uv[i][0]),
                icon.getInterpolatedV(uv[i][1]));
        }
    }
}
