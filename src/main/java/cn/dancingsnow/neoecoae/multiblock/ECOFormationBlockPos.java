package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public class ECOFormationBlockPos {

    private final int x;
    private final int y;
    private final int z;
    private final ECOControllerTier tier;

    public ECOFormationBlockPos(int x, int y, int z) {
        this(x, y, z, null);
    }

    public ECOFormationBlockPos(int x, int y, int z, ECOControllerTier tier) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }
}
