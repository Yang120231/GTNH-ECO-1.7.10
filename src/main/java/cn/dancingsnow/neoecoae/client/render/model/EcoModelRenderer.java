package cn.dancingsnow.neoecoae.client.render.model;

import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

public final class EcoModelRenderer {

    private EcoModelRenderer() {}

    public static void renderWorld(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons,
        IBlockAccess world, int x, int y, int z, Block block) {
        renderWorld(model, facing, icons, world, x, y, z, block, null);
    }

    public static void renderWorld(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons,
        IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.addTranslation(x, y, z);
        for (BakedQuad quad : model.getQuads(facing)) {
            if (shouldCull(world, x, y, z, quad)) {
                continue;
            }
            IIcon icon = getWorldIcon(quad, icons, renderer);
            if (icon == null) {
                continue;
            }

            ForgeDirection normal = quad.getNormal();
            int brightness = block.getMixedBrightnessForBlock(world, x, y, z);
            float shade = getWorldShade(quad.getNormal());
            tessellator.setBrightness(brightness);
            tessellator.setColorOpaque_F(shade, shade, shade);
            submitQuad(tessellator, quad, icon);
        }
        tessellator.addTranslation(-x, -y, -z);
    }

    public static void renderInventoryBlock(BakedEcoModel model, Map<String, IIcon> icons) {
        renderInventoryBlock(model, ModelFacing.NORTH, icons);
    }

    public static void renderInventoryBlock(BakedEcoModel model, ModelFacing facing, Map<String, IIcon> icons) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glRotated(180.0D, 0.0D, 1.0D, 0.0D);
        GL11.glTranslated(-0.5D, -0.5D, -0.5D);
        renderInventoryQuads(model, facing, icons);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    private static IIcon getWorldIcon(BakedQuad quad, Map<String, IIcon> icons, RenderBlocks renderer) {
        if (renderer != null && renderer.hasOverrideBlockTexture()) {
            return renderer.overrideBlockTexture;
        }
        return icons.get(quad.getTexture());
    }

    private static boolean shouldCull(IBlockAccess world, int x, int y, int z, BakedQuad quad) {
        ForgeDirection cullDirection = quad.getCullDirection();
        if (cullDirection == ForgeDirection.UNKNOWN) {
            return false;
        }

        return !world.getBlock(x, y, z)
            .shouldSideBeRendered(
                world,
                x + cullDirection.offsetX,
                y + cullDirection.offsetY,
                z + cullDirection.offsetZ,
                cullDirection.ordinal());
    }

    private static float getWorldShade(ForgeDirection normal) {
        if (normal == ForgeDirection.DOWN) {
            return 0.5F;
        }
        if (normal == ForgeDirection.UP) {
            return 0.95F;
        }
        if (normal == ForgeDirection.NORTH || normal == ForgeDirection.SOUTH) {
            return 0.7F;
        }
        return 0.55F;
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
        Tessellator tessellator = Tessellator.instance;
        List<BakedQuad> quads = model.getQuads(facing);
        for (BakedQuad quad : quads) {
            IIcon icon = icons.get(quad.getTexture());
            if (icon == null) {
                continue;
            }

            tessellator.startDrawingQuads();
            ForgeDirection normal = quad.getNormal();
            float shade = getInventoryShade(normal);
            tessellator.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
            tessellator.setColorOpaque_F(shade, shade, shade);
            submitQuad(tessellator, quad, icon);
            tessellator.draw();
        }
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
