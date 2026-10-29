package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class ComputationSystemL4 extends ECOComputationSystemBlockEntity
    implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

    public ComputationSystemL4() {
        super(
            CraftingMachines.COMPUTATION_SYSTEM_L4_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) CraftingMachines.COMPUTATION_SYSTEM_L4.value()).defaultBlockState(),
            StorageTiers.L4);
    }
}
