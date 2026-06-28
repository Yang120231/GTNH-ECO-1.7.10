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

import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;

public final class EcoModelRenderer {

    private static final int FULL_BRIGHTNESS = 15728880;
    private static final double LIGHT_SAMPLE_EPSILON = 0.01D;

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

            int brightness = quad.isFullBright() ? FULL_BRIGHTNESS
                : getWorldBrightness(world, x, y, z, block, quad, facing);
            float shade = quad.isFullBright() || !quad.isShade() ? 1.0F : getWorldShade(quad.getNormal());
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

        int neighborX = x + cullDirection.offsetX;
        int neighborY = y + cullDirection.offsetY;
        int neighborZ = z + cullDirection.offsetZ;
        if (ECOFormationVisibility.isHidden(world, neighborX, neighborY, neighborZ)) {
            return false;
        }

        return !world.getBlock(x, y, z)
            .shouldSideBeRendered(world, neighborX, neighborY, neighborZ, cullDirection.ordinal());
    }

    private static float getWorldShade(ForgeDirection normal) {
        if (normal == ForgeDirection.DOWN) {
            return 0.5F;
        }
        if (normal == ForgeDirection.UP) {
            return 1.0F;
        }
        if (normal == ForgeDirection.NORTH || normal == ForgeDirection.SOUTH) {
            return 0.8F;
        }
        return 0.6F;
    }

    private static int getWorldBrightness(IBlockAccess world, int x, int y, int z, Block block, BakedQuad quad,
        ModelFacing facing) {
        ForgeDirection normal = quad.getNormal();
        if (normal == ForgeDirection.UNKNOWN) {
            return block.getMixedBrightnessForBlock(world, x, y, z);
        }

        if (!quad.isBoundaryFace()) {
            return getInternalBrightness(world, x, y, z, block, facing.getDirection());
        }

        int sampleX = x + floor(sampleCoordinate(quad, 0) + normal.offsetX * LIGHT_SAMPLE_EPSILON);
        int sampleY = y + floor(sampleCoordinate(quad, 1) + normal.offsetY * LIGHT_SAMPLE_EPSILON);
        int sampleZ = z + floor(sampleCoordinate(quad, 2) + normal.offsetZ * LIGHT_SAMPLE_EPSILON);
        return getVisualBrightness(world, x, y, z, sampleX, sampleY, sampleZ, block, normal);
    }

    private static int getInternalBrightness(IBlockAccess world, int x, int y, int z, Block block,
        ForgeDirection openingDirection) {
        int openingBrightness = getVisualBrightness(world, x, y, z, x, y, z, block, openingDirection);
        if (openingBrightness != 0) {
            return openingBrightness;
        }

        int upward = block.getMixedBrightnessForBlock(world, x, y + 1, z);
        if (upward != 0) {
            return upward;
        }
        return block.getMixedBrightnessForBlock(world, x, y, z);
    }

    private static int getVisualBrightness(IBlockAccess world, int x, int y, int z, int sampleX, int sampleY,
        int sampleZ, Block block, ForgeDirection normal) {
        if (isVisualSelfOrHidden(world, sampleX, sampleY, sampleZ)) {
            int offsetX = sampleX + normal.offsetX;
            int offsetY = sampleY + normal.offsetY;
            int offsetZ = sampleZ + normal.offsetZ;
            if (!isVisualSelfOrHidden(world, offsetX, offsetY, offsetZ)) {
                return block.getMixedBrightnessForBlock(world, offsetX, offsetY, offsetZ);
            }
        }

        int brightness = block.getMixedBrightnessForBlock(world, sampleX, sampleY, sampleZ);
        if (brightness != 0) {
            return brightness;
        }

        int upward = block.getMixedBrightnessForBlock(world, x, y + 1, z);
        if (upward != 0) {
            return upward;
        }
        return block.getMixedBrightnessForBlock(world, x, y, z);
    }

    private static boolean isVisualSelfOrHidden(IBlockAccess world, int x, int y, int z) {
        if (ECOFormationVisibility.isHidden(world, x, y, z)) {
            return true;
        }

        Block sampleBlock = world.getBlock(x, y, z);
        return sampleBlock instanceof BlockModernModel || sampleBlock instanceof BlockModelDrive;
    }

    private static double sampleCoordinate(BakedQuad quad, int axis) {
        double[][] vertices = quad.getVertices();
        double total = 0.0D;
        for (double[] vertex : vertices) {
            total += vertex[axis];
        }
        return total / vertices.length;
    }

    private static int floor(double value) {
        int floor = (int) value;
        return value < floor ? floor - 1 : floor;
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
            float shade = quad.isFullBright() || !quad.isShade() ? 1.0F : getInventoryShade(normal);
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
