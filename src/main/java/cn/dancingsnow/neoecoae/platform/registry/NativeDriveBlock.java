package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;

public final class NativeDriveBlock extends NativeMachineBlock {

    public NativeDriveBlock(BlockEntityType<?> type, String model) {
        super(type, ECOMultiblockParts.Role.STORAGE_DRIVE, model);
    }
}
