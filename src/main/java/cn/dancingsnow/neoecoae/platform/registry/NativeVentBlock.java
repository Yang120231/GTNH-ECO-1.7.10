package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;

public final class NativeVentBlock extends NativeMachineBlock {

    public NativeVentBlock(BlockEntityType<?> type, String model) {
        super(type, ECOMultiblockParts.Role.VENT, model);
    }
}
