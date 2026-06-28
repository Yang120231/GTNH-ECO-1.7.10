package cn.dancingsnow.neoecoae.client.render.model;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;

final class ModelLightSampler {

    private static final double LIGHT_SAMPLE_EPSILON = 0.01D;

    private ModelLightSampler() {}

    static int getWorldBrightness(IBlockAccess world, int x, int y, int z, Block block, BakedQuad quad,
        ModelFacing facing) {
        ForgeDirection normal = quad.getNormal();
        if (normal == ForgeDirection.UNKNOWN) {
            return block.getMixedBrightnessForBlock(world, x, y, z);
        }

        if (!quad.isBoundaryFace()) {
            return getInternalBrightness(world, x, y, z, block, facing.getDirection());
        }

        int sampleX = x + floor(quad.getSampleX() + normal.offsetX * LIGHT_SAMPLE_EPSILON);
        int sampleY = y + floor(quad.getSampleY() + normal.offsetY * LIGHT_SAMPLE_EPSILON);
        int sampleZ = z + floor(quad.getSampleZ() + normal.offsetZ * LIGHT_SAMPLE_EPSILON);
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

    private static int floor(double value) {
        int floor = (int) value;
        return value < floor ? floor - 1 : floor;
    }
}
