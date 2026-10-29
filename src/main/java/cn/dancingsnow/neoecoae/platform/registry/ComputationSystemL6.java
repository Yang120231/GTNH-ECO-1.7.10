package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class ComputationSystemL6 extends ECOComputationSystemBlockEntity
    implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

    public ComputationSystemL6() {
        super(
            CraftingMachines.COMPUTATION_SYSTEM_L6_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) CraftingMachines.COMPUTATION_SYSTEM_L6.value()).defaultBlockState(),
            StorageTiers.L6);
    }
}
