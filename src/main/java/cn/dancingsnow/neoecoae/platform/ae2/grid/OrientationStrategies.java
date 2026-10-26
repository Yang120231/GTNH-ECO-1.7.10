package cn.dancingsnow.neoecoae.platform.ae2.grid;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;

public final class OrientationStrategies {

    private static final IOrientationStrategy HORIZONTAL = (state, side) -> {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // AE2's north-facing model space defines LEFT as WEST and RIGHT as EAST.
        switch (side) {
            case FRONT:
                return facing;
            case BACK:
                return facing.getOpposite();
            case TOP:
                return Direction.UP;
            case BOTTOM:
                return Direction.DOWN;
            case LEFT:
                return facing.getCounterClockWise();
            case RIGHT:
                return facing.getClockWise();
            default:
                throw new AssertionError(side);
        }
    };

    private OrientationStrategies() {}

    public static IOrientationStrategy horizontalFacing() {
        return HORIZONTAL;
    }
}
