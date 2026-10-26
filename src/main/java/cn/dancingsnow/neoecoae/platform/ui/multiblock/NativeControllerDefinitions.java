package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;

/** Locked source NEMultiBlocks crafting/computation geometry over the actual native hardware. */
public final class NativeControllerDefinitions {

    private static final Map<String, MultiBlockDefinition> DEFINITIONS = new HashMap<>();

    private NativeControllerDefinitions() {}

    public static MultiBlockDefinition get(ECOMachineKind kind, int tier) {
        if (tier < 1 || tier > 3 || kind == ECOMachineKind.STORAGE) return null;
        return DEFINITIONS.computeIfAbsent(kind.name() + tier, key -> create(kind, tier));
    }

    private static MultiBlockDefinition create(ECOMachineKind kind, int tier) {
        boolean crafting = kind == ECOMachineKind.CRAFTING;
        Holder<Block> main = crafting
            ? tier == 1 ? CraftingMachines.CRAFTING_SYSTEM_L4
                : tier == 2 ? CraftingMachines.CRAFTING_SYSTEM_L6 : CraftingMachines.CRAFTING_SYSTEM_L9
            : tier == 1 ? CraftingMachines.COMPUTATION_SYSTEM_L4
                : tier == 2 ? CraftingMachines.COMPUTATION_SYSTEM_L6 : CraftingMachines.COMPUTATION_SYSTEM_L9;
        Holder<Block> parallel = crafting
            ? tier == 1 ? CraftingMachines.CRAFTING_PARALLEL_CORE_L4
                : tier == 2 ? CraftingMachines.CRAFTING_PARALLEL_CORE_L6 : CraftingMachines.CRAFTING_PARALLEL_CORE_L9
            : tier == 1 ? CraftingMachines.COMPUTATION_PARALLEL_CORE_L4
                : tier == 2 ? CraftingMachines.COMPUTATION_PARALLEL_CORE_L6
                    : CraftingMachines.COMPUTATION_PARALLEL_CORE_L9;
        BlockState casing = state(crafting ? CraftingMachines.CRAFTING_CASING : CraftingMachines.COMPUTATION_CASING);
        MultiBlockDefinition.Builder builder = MultiBlockDefinition.builder(main);
        for (int x = 0; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 1; z++) builder.setBlock(
            new BlockPos(x, y, z),
            x == 1 && y == 1 && z == 0 ? state(main)
                : x == 2 && y == 1 && z == 1
                    ? state(crafting ? CraftingMachines.CRAFTING_INTERFACE : CraftingMachines.COMPUTATION_INTERFACE)
                    : crafting && x == 2 && y == 0 && z == 1 ? state(CraftingMachines.OUTPUT_HATCH)
                        : crafting && x == 2 && y == 2 && z == 1 ? state(CraftingMachines.INPUT_HATCH) : casing);
        builder.setBlockRepeatable(
            new BlockPos(-1, 1, 0),
            Direction.WEST,
            state(crafting ? CraftingMachines.CRAFTING_WORKER : CraftingMachines.COMPUTATION_TRANSMITTER));
        for (int y : new int[] { 0, 2 }) {
            builder.setBlockRepeatable(
                new BlockPos(-1, y, 0),
                Direction.WEST,
                state(crafting ? parallel : CraftingMachines.COMPUTATION_DRIVE));
            builder.setBlockRepeatable(
                new BlockPos(-1, y, 1),
                Direction.WEST,
                state(crafting ? CraftingMachines.CRAFTING_PATTERN_BUS : parallel)
                    .setValue(NativeMachineBlock.FACING, Direction.SOUTH));
        }
        Holder<Block> threading = tier == 1 ? CraftingMachines.COMPUTATION_THREADING_CORE_L4
            : tier == 2 ? CraftingMachines.COMPUTATION_THREADING_CORE_L6
                : CraftingMachines.COMPUTATION_THREADING_CORE_L9;
        builder.setBlockRepeatable(
            new BlockPos(-1, 1, 1),
            Direction.WEST,
            state(crafting ? CraftingMachines.CRAFTING_VENT : threading)
                .setValue(NativeMachineBlock.FACING, Direction.SOUTH));
        Holder<Block> cooler = tier == 1 ? CraftingMachines.COMPUTATION_COOLING_CONTROLLER_L4
            : tier == 2 ? CraftingMachines.COMPUTATION_COOLING_CONTROLLER_L6
                : CraftingMachines.COMPUTATION_COOLING_CONTROLLER_L9;
        for (int y = 0; y <= 2; y++) for (int z = 0; z <= 1; z++) builder.setBlockWithRepeatShifted(
            new BlockPos(-1, y, z),
            Direction.WEST,
            0,
            !crafting && y == 1 && z == 0 ? state(cooler).setValue(NativeMachineBlock.FACING, Direction.WEST) : casing);
        return builder.expandMin(1)
            .expandMax(
                (crafting ? PlanningSettings.craftingSystemMaxLength : PlanningSettings.computationSystemMaxLength) - 4)
            .create();
    }

    private static BlockState state(Holder<Block> block) {
        return ((NativeMachineBlock) block.value()).defaultBlockState();
    }
}
