package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;

public class ECOComputationNetworkSwitchBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster, ECOComputationNetworkSwitchBlockEntity> {

    public ECOComputationNetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator::new);
    }
}
