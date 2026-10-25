package cn.dancingsnow.neoecoae.platform.ae2.grid;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;

@FunctionalInterface
public interface IOrientationStrategy {

    Direction getSide(BlockState state, RelativeSide side);
}
