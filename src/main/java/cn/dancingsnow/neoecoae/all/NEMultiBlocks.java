package cn.dancingsnow.neoecoae.all;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.Vec3;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.registry.NativeEnergyCellBlock;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.NativeVentBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public class NEMultiBlocks {

    public static final List<MultiBlockDefinition> DEFINITIONS = new ArrayList<>();

    public static final MultiBlockDefinition STORAGE_SYSTEM_L4 = storageSystem(
        StorageMachines.STORAGE_SYSTEM_L4,
        BlockState.defaultState(StorageMachines.STORAGE_SYSTEM_L4.value()),
        BlockState.defaultState(StorageMachines.ENERGY_CELL_L4.value())
            .setValue(NativeEnergyCellBlock.FACING, Direction.SOUTH));

    public static final MultiBlockDefinition STORAGE_SYSTEM_L6 = storageSystem(
        StorageMachines.STORAGE_SYSTEM_L6,
        BlockState.defaultState(StorageMachines.STORAGE_SYSTEM_L6.value()),
        BlockState.defaultState(StorageMachines.ENERGY_CELL_L6.value())
            .setValue(NativeEnergyCellBlock.FACING, Direction.SOUTH));

    public static final MultiBlockDefinition STORAGE_SYSTEM_L9 = storageSystem(
        StorageMachines.STORAGE_SYSTEM_L9,
        BlockState.defaultState(StorageMachines.STORAGE_SYSTEM_L9.value()),
        BlockState.defaultState(StorageMachines.ENERGY_CELL_L9.value())
            .setValue(NativeEnergyCellBlock.FACING, Direction.SOUTH));

    private static MultiBlockDefinition storageSystem(Holder<Block> owner, BlockState system, BlockState energyCell) {
        MultiBlockDefinition.Builder builder = MultiBlockDefinition.builder(owner)
            .setBlock(pos(1, 1, 0), system)
            .setBlock(pos(1, 0, 0), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(2, 0, 0), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(2, 1, 0), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(1, 2, 0), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(2, 2, 0), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(1, 0, 1), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(2, 0, 1), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(2, 1, 1), BlockState.defaultState(StorageMachines.STORAGE_INTERFACE.value()))
            .setBlock(pos(1, 1, 1), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(1, 2, 1), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()))
            .setBlock(pos(2, 2, 1), BlockState.defaultState(StorageMachines.STORAGE_CASING.value()));

        BlockState casing = BlockState.defaultState(StorageMachines.STORAGE_CASING.value());
        for (int y = 0; y < 3; y++) {
            builder.setBlock(pos(0, y, 0), casing);
            builder.setBlock(pos(0, y, 1), casing);
        }

        return builder
            .setBlockRepeatable(
                pos(-1, 0, 0),
                Direction.WEST,
                BlockState.defaultState(StorageMachines.ECO_DRIVE.value()))
            .setBlockRepeatable(
                pos(-1, 1, 0),
                Direction.WEST,
                BlockState.defaultState(StorageMachines.ECO_DRIVE.value()))
            .setBlockRepeatable(
                pos(-1, 2, 0),
                Direction.WEST,
                BlockState.defaultState(StorageMachines.ECO_DRIVE.value()))
            .setBlockRepeatable(pos(-1, 0, 1), Direction.WEST, energyCell)
            .setBlockRepeatable(
                pos(-1, 1, 1),
                Direction.WEST,
                BlockState.defaultState(StorageMachines.STORAGE_VENT.value())
                    .setValue(NativeVentBlock.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(-1, 2, 1), Direction.WEST, energyCell)
            .setBlockWithRepeatShifted(pos(0, 0, 0), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 0, 1), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 1, 0), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 1, 1), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 2, 0), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 2, 1), Direction.WEST, 1, casing)
            .expandMin(1)
            .expandMax(PlanningSettings.storageSystemMaxLength - 4)
            .onFormed((pos, level) -> {
                BlockState state = level.getBlockState(pos);
                BlockState newState = state;
                if (state.hasProperty(NativeMachineBlock.FORMED)) {
                    newState = newState.setValue(NativeMachineBlock.FORMED, true);
                }
                if (newState.hasProperty(NativeMachineBlock.INVISIBLE)) {
                    Vec3 myPos = Vec3.createVectorHelper(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                    Vec3 controllerPos = Vec3.createVectorHelper(1.5, 1.5, .5);
                    newState = newState
                        .setValue(NativeMachineBlock.INVISIBLE, myPos.squareDistanceTo(controllerPos) <= 3);
                }
                if (newState != state) {
                    level.setBlockAndUpdate(pos, newState);
                }
            })
            .create();
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    public static MultiBlockDefinition getStorageSystemDefinition(StorageTierDescriptor tier) {
        return switch (tier.getTier()) {
            case 1 -> STORAGE_SYSTEM_L4;
            case 2 -> STORAGE_SYSTEM_L6;
            case 3 -> STORAGE_SYSTEM_L9;
            default -> null;
        };
    }
}
