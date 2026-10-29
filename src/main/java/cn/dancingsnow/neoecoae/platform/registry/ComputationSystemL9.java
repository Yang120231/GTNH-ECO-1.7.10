package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class ComputationSystemL9 extends ECOComputationSystemBlockEntity
    implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

    public ComputationSystemL9() {
        super(
            CraftingMachines.COMPUTATION_SYSTEM_L9_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) CraftingMachines.COMPUTATION_SYSTEM_L9.value()).defaultBlockState(),
            StorageTiers.L9);
    }
}
