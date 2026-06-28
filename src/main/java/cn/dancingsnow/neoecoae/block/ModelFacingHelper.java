package cn.dancingsnow.neoecoae.block;

import net.minecraft.util.MathHelper;

public final class ModelFacingHelper {

    private ModelFacingHelper() {}

    public static int getFacingMetaFromYaw(float yaw) {
        int quadrant = MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) & 3;
        switch (quadrant) {
            case 0:
                return 0; // north
            case 1:
                return 1; // east
            case 2:
                return 2; // south
            case 3:
                return 3; // west
            default:
                return 0;
        }
    }
}
