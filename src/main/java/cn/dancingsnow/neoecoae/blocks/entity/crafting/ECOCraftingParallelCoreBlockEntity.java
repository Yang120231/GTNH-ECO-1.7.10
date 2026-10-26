package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public class ECOCraftingParallelCoreBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingParallelCoreBlockEntity> {

    private final StorageTierDescriptor tier;

    public ECOCraftingParallelCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        StorageTierDescriptor tier) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
        this.tier = tier;
    }

    @Override
    public void onReady() {
        super.onReady();
        applyIdlePower(ECOPowerRole.CRAFTING_PARALLEL_CORE, 64, 0);
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }
}
