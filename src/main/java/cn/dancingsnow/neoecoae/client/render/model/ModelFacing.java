package cn.dancingsnow.neoecoae.client.render.model;

import net.minecraftforge.common.util.ForgeDirection;

public enum ModelFacing {

    NORTH(0, ForgeDirection.NORTH),
    EAST(1, ForgeDirection.EAST),
    SOUTH(2, ForgeDirection.SOUTH),
    WEST(3, ForgeDirection.WEST);

    private final int meta;
    private final ForgeDirection direction;

    ModelFacing(int meta, ForgeDirection direction) {
        this.meta = meta;
        this.direction = direction;
    }

    public int getMeta() {
        return this.meta;
    }

    public ForgeDirection getDirection() {
        return this.direction;
    }

    public static ModelFacing fromMeta(int meta) {
        switch (meta & 3) {
            case 1:
                return EAST;
            case 2:
                return SOUTH;
            case 3:
                return WEST;
            case 0:
            default:
                return NORTH;
        }
    }
}
