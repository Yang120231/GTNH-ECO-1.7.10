package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class CraftingSystemL6 extends ECOCraftingSystemBlockEntity
    implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

    public CraftingSystemL6() {
        super(
            CraftingMachines.CRAFTING_SYSTEM_L6_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) CraftingMachines.CRAFTING_SYSTEM_L6.value()).defaultBlockState(),
            StorageTiers.L6);
    }
}
