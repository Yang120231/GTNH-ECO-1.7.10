package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraftforge.common.util.ForgeDirection;

/** Six-axis orientation, without Forge's UNKNOWN pseudo-side. */
public enum Direction {

    DOWN(0, -1, 0, Axis.Y),
    UP(0, 1, 0, Axis.Y),
    NORTH(0, 0, -1, Axis.Z),
    SOUTH(0, 0, 1, Axis.Z),
    WEST(-1, 0, 0, Axis.X),
    EAST(1, 0, 0, Axis.X);

    private final int x, y, z;
    private final Axis axis;

    Direction(int x, int y, int z, Axis axis) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.axis = axis;
    }

    public int getStepX() {
        return x;
    }

    public int getStepY() {
        return y;
    }

    public int getStepZ() {
        return z;
    }

    public Axis getAxis() {
        return axis;
    }

    public Direction getOpposite() {
        return values()[ordinal() ^ 1];
    }

    public ForgeDirection nativeSide() {
        return ForgeDirection.getOrientation(ordinal());
    }

    public static Direction fromNative(ForgeDirection side) {
        if (side == null || side == ForgeDirection.UNKNOWN)
            throw new IllegalArgumentException("A physical side is required");
        return values()[side.ordinal()];
    }

    public Direction getClockWise() {
        switch (this) {
            case NORTH:
                return EAST;
            case EAST:
                return SOUTH;
            case SOUTH:
                return WEST;
            case WEST:
                return NORTH;
            default:
                throw new IllegalStateException("Vertical direction has no horizontal rotation");
        }
    }

    public Direction getCounterClockWise() {
        return getClockWise().getOpposite();
    }

    public enum Axis {

        X,
        Y,
        Z;

        public boolean isHorizontal() {
            return this != Y;
        }

        public int choose(int x, int y, int z) {
            return this == X ? x : this == Y ? y : z;
        }
    }

    public static final class Plane {

        public static final java.util.List<Direction> HORIZONTAL = java.util.Arrays.asList(NORTH, EAST, SOUTH, WEST);

        private Plane() {}
    }
}
