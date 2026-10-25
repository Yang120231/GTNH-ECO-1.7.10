package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;

public final class NativeEnergyCellBlock extends NativeMachineBlock {

    public static final Property<Integer> LEVEL = Property.integer("level", 0, 4);

    public NativeEnergyCellBlock(BlockEntityType<?> type, String model) {
        super(type, ECOMultiblockParts.Role.ENERGY_CELL, model);
        define(LEVEL, 0);
    }
}
