package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;

public final class NativeCasingBlock<C> extends NativeMachineBlock {

    public NativeCasingBlock(BlockEntityType<?> type, String model) {
        super(type, ECOMultiblockParts.Role.CASING, model);
    }
}
