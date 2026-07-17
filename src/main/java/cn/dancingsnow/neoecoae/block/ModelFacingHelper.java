package cn.dancingsnow.neoecoae.block;

import net.minecraft.util.MathHelper;

public final class ModelFacingHelper {

    private ModelFacingHelper() {}

    public static int getFacingMetaFromYaw(float yaw) {
        int quadrant = MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) & 3;
        switch (quadrant) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            default:
                return 0;
        }
    }
}
