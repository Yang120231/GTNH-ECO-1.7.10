package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;

public final class NativeInterfaceBlock extends NativeMachineBlock {

    public static final Property<ECOStorageInterfaceMode> STORAGE_MODE = new Property<>(
        "storage_mode",
        ECOStorageInterfaceMode.class,
        value -> true);

    public NativeInterfaceBlock(BlockEntityType<?> type, String model) {
        super(type, ECOMultiblockParts.Role.INTERFACE, model);
        define(STORAGE_MODE, ECOStorageInterfaceMode.STORAGE);
    }
}
