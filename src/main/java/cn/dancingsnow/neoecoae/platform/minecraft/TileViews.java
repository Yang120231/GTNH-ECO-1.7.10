package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraft.tileentity.TileEntity;

public final class TileViews {

    private TileViews() {}

    public static BlockPos position(TileEntity tile) {
        return new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord);
    }

    public static WorldView level(TileEntity tile) {
        return WorldView.of(tile.getWorldObj());
    }

    public static BlockState state(TileEntity tile) {
        if (tile instanceof NativeStateHolder) return ((NativeStateHolder) tile).nativeBlockState();
        WorldView view = level(tile);
        return view == null || !view.hasChunkAt(position(tile)) ? null : view.getBlockState(position(tile));
    }
}
