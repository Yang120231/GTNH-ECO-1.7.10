package cn.dancingsnow.neoecoae.multiblock.network;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IOrientationStrategy;
import cn.dancingsnow.neoecoae.platform.ae2.grid.OrientationStrategies;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public final class NENetworkSwitchUtil {

    private static final IOrientationStrategy HORIZONTAL = OrientationStrategies.horizontalFacing();

    private NENetworkSwitchUtil() {}

    public static Direction rightOfController(BlockState state) {
        return HORIZONTAL.getSide(state, RelativeSide.RIGHT);
    }

    public static BlockPos switchPosition(BlockPos controllerPos, BlockState state, boolean mirrored) {
        Direction side = mirrored ? HORIZONTAL.getSide(state, RelativeSide.LEFT) : rightOfController(state);
        return controllerPos.relative(side);
    }

    public static boolean isSwitchPosition(BlockPos switchPos, BlockPos controllerPos, BlockState state) {
        return switchPos.equals(switchPosition(controllerPos, state, false))
            || switchPos.equals(switchPosition(controllerPos, state, true));
    }

    public static boolean canUseNetworkSwitch(StorageTierDescriptor tier) {
        return tier.supportsFeature(ECOFeature.NETWORK_SWITCH);
    }

    public static void setFormed(cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView level, BlockPos pos,
        boolean formed) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(NativeMachineBlock.FORMED) && state.getValue(NativeMachineBlock.FORMED) != formed)
            level.setBlock(pos, state.setValue(NativeMachineBlock.FORMED, formed), 2);
    }

    public static void clearFormed(cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView level,
        BlockPos controllerPos, BlockState state) {
        setFormed(level, switchPosition(controllerPos, state, false), false);
        setFormed(level, switchPosition(controllerPos, state, true), false);
    }

    public static void syncFormed(cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView level,
        BlockPos controllerPos, BlockState state, boolean mirrored) {
        setFormed(level, switchPosition(controllerPos, state, false), !mirrored);
        setFormed(level, switchPosition(controllerPos, state, true), mirrored);
    }
}
