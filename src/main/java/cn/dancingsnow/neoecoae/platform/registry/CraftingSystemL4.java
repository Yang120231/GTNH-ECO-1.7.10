package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class CraftingSystemL4 extends ECOCraftingSystemBlockEntity
    implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

    public CraftingSystemL4() {
        super(
            CraftingMachines.CRAFTING_SYSTEM_L4_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) CraftingMachines.CRAFTING_SYSTEM_L4.value()).defaultBlockState(),
            StorageTiers.L4);
    }
}
