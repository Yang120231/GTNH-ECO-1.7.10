package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.registry.NativeCasingBlock;

public class ECOMachineCasingBlockEntity<C extends NECluster<C>>
    extends NEBlockEntity<C, ECOMachineCasingBlockEntity<C>> {

    public ECOMachineCasingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        NEClusterCalculator.Factory<C> factory) {
        super(type, pos, blockState, factory);
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (this.level == null || this.notLoaded() || this.isRemoved()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        BlockState newState = state;
        if (this.cluster != null) {
            if (newState.hasProperty(NativeCasingBlock.INVISIBLE)) {
                newState = newState.setValue(NativeCasingBlock.INVISIBLE, this.cluster.shouldCasingHide(this));
            }
            if (newState.hasProperty(NativeCasingBlock.CLASSIC_VISIBLE)) {
                newState = newState
                    .setValue(NativeCasingBlock.CLASSIC_VISIBLE, this.cluster.shouldCasingRenderInClassic(this));
            }
        } else {
            if (newState.hasProperty(NativeCasingBlock.INVISIBLE)) {
                newState = newState.setValue(NativeCasingBlock.INVISIBLE, false);
            }
            if (newState.hasProperty(NativeCasingBlock.CLASSIC_VISIBLE)) {
                newState = newState.setValue(NativeCasingBlock.CLASSIC_VISIBLE, false);
            }
        }
        if (newState != state) {
            level.setBlock(worldPosition, newState, 2);
        }
    }
}
