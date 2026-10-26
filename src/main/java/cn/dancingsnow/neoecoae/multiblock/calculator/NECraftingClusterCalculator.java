package cn.dancingsnow.neoecoae.multiblock.calculator;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public class NECraftingClusterCalculator extends NEClusterCalculator<NECraftingCluster> {

    public NECraftingClusterCalculator(NEBlockEntity<NECraftingCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return PlanningSettings.craftingSystemMaxLength;
    }

    @Override
    protected Holder<Block> casing() {
        return CraftingMachines.CRAFTING_CASING;
    }

    @Override
    public NECraftingCluster createCluster(ServerWorldView level, BlockPos min, BlockPos max) {
        return new NECraftingCluster(min, max);
    }

    @Override
    protected void onClusterAttached(NECraftingCluster cluster) {
        NELogicalNetworkManager.attach(cluster);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        NECraftingClusterCalculator controller = candidateControllerCalculator(min, max);
        return controller != null && controller != this ? controller.checkMultiblockScale(min, max)
            : super.checkMultiblockScale(min, max);
    }

    private NECraftingClusterCalculator candidateControllerCalculator(BlockPos min, BlockPos max) {
        if (!(owner.getLevel() instanceof ServerWorldView level)) {
            return null;
        }
        return findCandidateController(level, min, max, ECOCraftingSystemBlockEntity.class)
            .map(ECOCraftingSystemBlockEntity::getCalculator)
            .filter(NECraftingClusterCalculator.class::isInstance)
            .map(NECraftingClusterCalculator.class::cast)
            .orElse(null);
    }

    private NECraftingClusterCalculator controllerCalculator(BlockPos min, BlockPos max) {
        if (!(owner.getLevel() instanceof ServerWorldView level)) {
            return null;
        }
        return findUniqueController(level, min, max, ECOCraftingSystemBlockEntity.class)
            .map(
                context -> context.controller()
                    .getCalculator())
            .filter(NECraftingClusterCalculator.class::isInstance)
            .map(NECraftingClusterCalculator.class::cast)
            .orElse(null);
    }

    @Override
    public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
        NECraftingClusterCalculator controllerCalculator = controllerCalculator(min, max);
        if (controllerCalculator != null && controllerCalculator != this) {
            return controllerCalculator.verifyInternalStructure(level, min, max);
        }
        Optional<ControllerContext<ECOCraftingSystemBlockEntity>> contextResult = findUniqueController(
            level,
            min,
            max,
            ECOCraftingSystemBlockEntity.class);
        if (!contextResult.isPresent()) return false;
        ControllerContext<ECOCraftingSystemBlockEntity> context = contextResult
            .orElseThrow(java.util.NoSuchElementException::new);
        ECOCraftingSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        StorageTierDescriptor tier = controller.getTier();
        BlockState controllerState = context.state();
        Direction front = context.front();
        Direction back = context.back();
        Direction top = context.top();
        Direction down = context.down();
        Direction left = context.left();
        Direction right = context.right();
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, right, left, right)) {
            controller.setMirrored(false);
            syncNetworkSwitchState(level, controllerPos, controllerState, false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, left, right, left)) {
            controller.setMirrored(true);
            syncNetworkSwitchState(level, controllerPos, controllerState, true);
            return true;
        }
        clearNetworkSwitchState(level, controllerPos, controllerState);
        controller.setMirrored(false);
        return false;
    }

    protected boolean verifyStructure(ServerWorldView level, BlockPos controllerPos, StorageTierDescriptor tier,
        Direction front, Direction back, Direction top, Direction down, Direction interfaceSide, Direction expandSide,
        Direction networkSwitchSide) {
        if (!validateCasingOrNetworkSwitch(level, controllerPos, tier, top, down, networkSwitchSide)) return false;
        if (!validateCasing(
            level,
            controllerPos,
            top,
            down,
            interfaceSide == networkSwitchSide ? expandSide : interfaceSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateCasing(
            level,
            controllerPos.relative(back)
                .relative(expandSide),
            top,
            down)) return false;
        BlockPos interfacePos = controllerPos.relative(back)
            .relative(interfaceSide);
        if (!validateHatchAndInterface(level, interfacePos, top, down)) {
            return false;
        }
        BlockPos workerStart = controllerPos.relative(expandSide)
            .relative(expandSide);
        Optional<BlockPos> workerEndResult = validateBlockLine(
            level,
            expandSide,
            workerStart,
            matchingStateFacing(CraftingMachines.CRAFTING_WORKER, front)
                .or(matchingStateFacing(CraftingMachines.FX_MONITOR_CORE, front)));
        if (!workerEndResult.isPresent()) {
            return false;
        }
        BlockPos workerEnd = workerEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos upperParallelCoreStart = workerStart.relative(top);
        Optional<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, front));
        if (!upperParallelCoreEndResult.isPresent()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos lowerParallelCoreStart = workerStart.relative(down);
        Optional<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, front));
        if (!lowerParallelCoreEndResult.isPresent()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos ventStart = workerStart.relative(back);
        Optional<BlockPos> ventEndResult = validateBlockLine(
            level,
            expandSide,
            ventStart,
            matchingStateFacing(CraftingMachines.CRAFTING_VENT, back));
        if (!ventEndResult.isPresent()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos upperPatternBusStart = ventStart.relative(top);
        Optional<BlockPos> upperPatternBusEndResult = validateBlockLine(
            level,
            expandSide,
            upperPatternBusStart,
            matchingStateFacing(CraftingMachines.CRAFTING_PATTERN_BUS, back));
        if (!upperPatternBusEndResult.isPresent()) {
            return false;
        }
        BlockPos upperPatternBusEnd = upperPatternBusEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos lowerPatternBusStart = ventStart.relative(down);
        Optional<BlockPos> lowerPatternBusEndResult = validateBlockLine(
            level,
            expandSide,
            lowerPatternBusStart,
            matchingStateFacing(CraftingMachines.CRAFTING_PATTERN_BUS, back));
        if (!lowerPatternBusEndResult.isPresent()) {
            return false;
        }
        BlockPos lowerPatternBusEnd = lowerPatternBusEndResult.orElseThrow(java.util.NoSuchElementException::new);

        List<BlockPos> endCasing = Stream
            .of(workerEnd, upperParallelCoreEnd, lowerParallelCoreEnd, upperPatternBusEnd, lowerPatternBusEnd, ventEnd)
            .map(it -> it.relative(expandSide))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());

        if (!ensureSameSurface(endCasing)) {
            return false;
        }

        return validateBlocks(level, endCasing, BlockState::is, CraftingMachines.CRAFTING_CASING);
    }

    @Override
    public boolean isValidBlockEntity(TileEntity te) {
        if (te instanceof cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity sw) {
            return sw.getLevel() instanceof ServerWorldView level && isNetworkSwitchAt(level, sw.getBlockPos());
        }
        return (te instanceof NEBlockEntity<?, ?>neBlockEntity
            && neBlockEntity.getCalculator() instanceof NECraftingClusterCalculator);
    }

    private static boolean isNetworkSwitchAt(ServerWorldView level, BlockPos switchPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof ECOCraftingSystemBlockEntity controller
                && NENetworkSwitchUtil.canUseNetworkSwitch(controller.getTier())
                && NENetworkSwitchUtil
                    .isSwitchPosition(switchPos, controller.getBlockPos(), controller.getBlockState()))
                return true;
        }
        return false;
    }

    private static void syncNetworkSwitchState(ServerWorldView level, BlockPos controllerPos,
        BlockState controllerState, boolean mirrored) {
        BlockPos switchPos = NENetworkSwitchUtil.switchPosition(controllerPos, controllerState, mirrored);
        BlockState switchState = level.getBlockState(switchPos);
        boolean normal = switchState.is(CraftingMachines.CRAFTING_NETWORK_SWITCH);
        boolean highEnergy = switchState.is(CraftingMachines.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH);
        NENetworkSwitchUtil.syncFormed(level, controllerPos, controllerState, mirrored);
        level.setBlock(
            controllerPos,
            level.getBlockState(controllerPos)
                .setValue(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.NETWORK_SWITCH, normal)
                .setValue(
                    cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.HIGH_ENERGY_NETWORK_SWITCH,
                    highEnergy),
            2);
    }

    private static void clearNetworkSwitchState(ServerWorldView level, BlockPos controllerPos,
        BlockState controllerState) {
        NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
        BlockState current = level.getBlockState(controllerPos);
        level.setBlock(
            controllerPos,
            current.setValue(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.NETWORK_SWITCH, false)
                .setValue(
                    cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.HIGH_ENERGY_NETWORK_SWITCH,
                    false),
            2);
    }

    protected boolean validateCasingOrNetworkSwitch(ServerWorldView level, BlockPos controllerPos,
        StorageTierDescriptor tier, Direction top, Direction down, Direction side) {
        BlockPos center = controllerPos.relative(side);
        BlockState state = level.getBlockState(center);
        boolean sw = state.is(CraftingMachines.CRAFTING_NETWORK_SWITCH)
            || state.is(CraftingMachines.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH);
        if (!state.is(CraftingMachines.CRAFTING_CASING) && !sw) return false;
        if (sw && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) return false;
        return validateBlock(level, center.relative(top), BlockState::is, CraftingMachines.CRAFTING_CASING)
            && validateBlock(level, center.relative(down), BlockState::is, CraftingMachines.CRAFTING_CASING);
    }

    private static boolean validateHatchAndInterface(ServerWorldView level, BlockPos interfacePos, Direction top,
        Direction down) {
        if (!validateBlock(level, interfacePos, BlockState::is, CraftingMachines.CRAFTING_INTERFACE)) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, CraftingMachines.INPUT_HATCH)) {
            return false;
        }
        return validateBlock(level, interfacePos.relative(down), BlockState::is, CraftingMachines.OUTPUT_HATCH);
    }

    protected BiPredicate<BlockState, BlockPos> matchingParallelCore(WorldView level, StorageTierDescriptor tier,
        Direction facing) {
        return (s, p) -> ECOMultiblockParts.matchesTiered(
            ECOMultiblockParts.context(level, p),
            ECOMultiblockParts.Role.CRAFTING_PARALLEL_CORE,
            tier,
            s.getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock,
            facing,
            cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity.class);
    }

}
