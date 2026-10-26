package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public class ECOComputationCoolingControllerBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster, ECOComputationCoolingControllerBlockEntity> {

    private final StorageTierDescriptor tier;
    // Transient derived state rebuilt by the calculator; the BlockState property is render-only persistence.
    private boolean mirrored;

    public ECOComputationCoolingControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        StorageTierDescriptor tier) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator::new);
        this.tier = tier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.MIRRORED)) {
                BlockState newState = state.setValue(
                    cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.MIRRORED,
                    formed && mirrored);
                if (newState != state) {
                    level.setBlock(worldPosition, newState, 2);
                }
            }
        }
    }

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }
}
