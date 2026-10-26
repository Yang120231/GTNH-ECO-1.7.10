package cn.dancingsnow.neoecoae.blocks.entity.computation;

import java.util.EnumSet;
import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.grid.BlockOrientation;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;

public class ECOComputationNetworkSwitchBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster, ECOComputationNetworkSwitchBlockEntity> {

    public ECOComputationNetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator::new);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : EnumSet.noneOf(Direction.class);
    }
}
