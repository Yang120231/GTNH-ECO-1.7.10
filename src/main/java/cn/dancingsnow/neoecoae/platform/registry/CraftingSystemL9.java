package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class CraftingSystemL9 extends ECOCraftingSystemBlockEntity
    implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

    public CraftingSystemL9() {
        super(
            CraftingMachines.CRAFTING_SYSTEM_L9_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) CraftingMachines.CRAFTING_SYSTEM_L9.value()).defaultBlockState(),
            StorageTiers.L9);
    }
}
