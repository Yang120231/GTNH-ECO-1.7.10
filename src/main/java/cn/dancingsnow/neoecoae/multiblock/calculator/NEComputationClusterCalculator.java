package cn.dancingsnow.neoecoae.multiblock.calculator;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
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

public class NEComputationClusterCalculator extends NEClusterCalculator<NEComputationCluster> {

    public NEComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return PlanningSettings.computationSystemMaxLength;
    }

    @Override
    protected Holder<Block> casing() {
        return CraftingMachines.COMPUTATION_CASING;
    }

    @Override
    public NEComputationCluster createCluster(ServerWorldView level, BlockPos min, BlockPos max) {
        return new NEComputationCluster(min, max);
    }

    @Override
    protected void onClusterAttached(NEComputationCluster cluster) {
        NELogicalNetworkManager.attach(cluster);
        // The AE2 crafting service may have already rebuilt its CPU list before the
        // multiblock calculator finished attaching all members. Publish the custom
        // CPU now so it cannot remain absent until a world reload.
        cluster.notifyGridCpuChange();
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        NEComputationClusterCalculator controller = candidateControllerCalculator(min, max);
        return controller != null && controller != this ? controller.checkMultiblockScale(min, max)
            : super.checkMultiblockScale(min, max);
    }

    private NEComputationClusterCalculator candidateControllerCalculator(BlockPos min, BlockPos max) {
        if (!(owner.getLevel() instanceof ServerWorldView level)) {
            return null;
        }
        return findCandidateController(level, min, max, ECOComputationSystemBlockEntity.class)
            .map(ECOComputationSystemBlockEntity::getCalculator)
            .filter(NEComputationClusterCalculator.class::isInstance)
            .map(NEComputationClusterCalculator.class::cast)
            .orElse(null);
    }

    private NEComputationClusterCalculator controllerCalculator(BlockPos min, BlockPos max) {
        if (!(owner.getLevel() instanceof ServerWorldView level)) {
            return null;
        }
        return findUniqueController(level, min, max, ECOComputationSystemBlockEntity.class)
            .map(
                context -> context.controller()
                    .getCalculator())
            .filter(NEComputationClusterCalculator.class::isInstance)
            .map(NEComputationClusterCalculator.class::cast)
            .orElse(null);
    }

    @Override
    public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
        NEComputationClusterCalculator controllerCalculator = controllerCalculator(min, max);
        if (controllerCalculator != null && controllerCalculator != this) {
            return controllerCalculator.verifyInternalStructure(level, min, max);
        }
        Optional<ControllerContext<ECOComputationSystemBlockEntity>> contextResult = findUniqueController(
            level,
            min,
            max,
            ECOComputationSystemBlockEntity.class);
        if (!contextResult.isPresent()) return false;
        ControllerContext<ECOComputationSystemBlockEntity> context = contextResult
            .orElseThrow(java.util.NoSuchElementException::new);
        ECOComputationSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        StorageTierDescriptor tier = controller.getTier();
        BlockState controllerState = context.state();
        Direction front = context.front();
        Direction back = context.back();
        Direction top = context.top();
        Direction down = context.down();
        Direction left = context.left();
        Direction right = context.right();
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, right, left, right, false)) {
            controller.setMirrored(false);
            setCoolingControllersMirrored(level, min, max, false);
            syncNetworkSwitchState(level, controllerPos, controllerState, false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, left, right, left, true)) {
            controller.setMirrored(true);
            setCoolingControllersMirrored(level, min, max, true);
            syncNetworkSwitchState(level, controllerPos, controllerState, true);
            return true;
        }
        clearNetworkSwitchState(level, controllerPos, controllerState);
        controller.setMirrored(false);
        return false;
    }

    private static void setCoolingControllersMirrored(ServerWorldView level, BlockPos min, BlockPos max,
        boolean mirrored) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOComputationCoolingControllerBlockEntity cooler) {
                cooler.setMirrored(mirrored);
            }
        }
    }

    protected boolean verifyStructure(ServerWorldView level, BlockPos controllerPos, StorageTierDescriptor tier,
        Direction front, Direction back, Direction top, Direction down, Direction interfaceSide, Direction expandSide,
        Direction networkSwitchSide, boolean mirrored) {
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
        if (!validateInterface(
            level,
            interfacePos,
            top,
            down,
            CraftingMachines.COMPUTATION_INTERFACE,
            CraftingMachines.COMPUTATION_CASING)) return false;
        BlockPos connectorStart = controllerPos.relative(expandSide)
            .relative(expandSide);
        Optional<BlockPos> connectorEndResult = validateBlockLine(
            level,
            expandSide,
            connectorStart,
            matchingStateFacing(CraftingMachines.COMPUTATION_TRANSMITTER, front));
        if (!connectorEndResult.isPresent()) {
            return false;
        }
        BlockPos connectorEnd = connectorEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos threadingCoreStart = connectorStart.relative(back);
        Optional<BlockPos> threadingCoreEndResult = validateBlockLine(
            level,
            expandSide,
            threadingCoreStart,
            matchingThreadingCore(level, tier, back));
        if (!threadingCoreEndResult.isPresent()) {
            return false;
        }
        BlockPos threadingCoreEnd = threadingCoreEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos upperParallelCoreStart = threadingCoreStart.relative(top);
        Optional<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, back));
        if (!upperParallelCoreEndResult.isPresent()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos lowerParallelCoreStart = threadingCoreStart.relative(down);
        Optional<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, back));
        if (!lowerParallelCoreEndResult.isPresent()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos upperDriveStart = connectorStart.relative(top);
        Optional<BlockPos> upperDriveEndResult = validateBlockLine(
            level,
            expandSide,
            upperDriveStart,
            matchingStateFacing(CraftingMachines.COMPUTATION_DRIVE, front));
        if (!upperDriveEndResult.isPresent()) {
            return false;
        }
        BlockPos upperDriveEnd = upperDriveEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos lowerDriveStart = connectorStart.relative(down);
        Optional<BlockPos> lowerDriveEndResult = validateBlockLine(
            level,
            expandSide,
            lowerDriveStart,
            matchingStateFacing(CraftingMachines.COMPUTATION_DRIVE, front));
        if (!lowerDriveEndResult.isPresent()) {
            return false;
        }
        BlockPos lowerDriveEnd = lowerDriveEndResult.orElseThrow(java.util.NoSuchElementException::new);

        List<BlockPos> tails = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            connectorEnd,
            threadingCoreEnd,
            upperDriveEnd,
            lowerDriveEnd,
            upperParallelCoreEnd,
            lowerParallelCoreEnd);

        if (!ensureSameSurface(tails)) {
            return false;
        }
        List<BlockPos> tailCasings = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            threadingCoreEnd.relative(expandSide),
            upperDriveEnd.relative(expandSide),
            lowerDriveEnd.relative(expandSide),
            upperParallelCoreEnd.relative(expandSide),
            lowerParallelCoreEnd.relative(expandSide));
        BlockPos coolerPos = connectorEnd.relative(expandSide);
        if (!validateBlock(level, coolerPos, matchingCoolingController(level, tier, expandSide), coolerPos)) {
            return false;
        }
        return validateBlocks(level, tailCasings, BlockState::is, CraftingMachines.COMPUTATION_CASING);
    }

    @Override
    public boolean isValidBlockEntity(TileEntity te) {
        if (te instanceof cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity sw) {
            return sw.getLevel() instanceof ServerWorldView level && isNetworkSwitchAt(level, sw.getBlockPos());
        }
        return (te instanceof NEBlockEntity<?, ?>neBlockEntity
            && neBlockEntity.getCalculator() instanceof NEComputationClusterCalculator);
    }

    private static boolean isNetworkSwitchAt(ServerWorldView level, BlockPos switchPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level
                .getBlockEntity(switchPos.relative(direction)) instanceof ECOComputationSystemBlockEntity controller
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
        boolean normal = switchState.is(CraftingMachines.COMPUTATION_NETWORK_SWITCH);
        boolean highEnergy = switchState.is(CraftingMachines.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH);
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
        boolean sw = state.is(CraftingMachines.COMPUTATION_NETWORK_SWITCH)
            || state.is(CraftingMachines.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH);
        if (!state.is(CraftingMachines.COMPUTATION_CASING) && !sw) return false;
        if (sw && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) return false;
        return validateBlock(level, center.relative(top), BlockState::is, CraftingMachines.COMPUTATION_CASING)
            && validateBlock(level, center.relative(down), BlockState::is, CraftingMachines.COMPUTATION_CASING);
    }

    protected BiPredicate<BlockState, BlockPos> matchingParallelCore(WorldView level, StorageTierDescriptor tier,
        Direction facing) {
        return (s, p) -> {
            var context = ECOMultiblockParts.context(level, p);
            return ECOMultiblockParts.matchesTiered(
                context,
                ECOMultiblockParts.Role.COMPUTATION_PARALLEL_CORE,
                tier,
                s.getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock,
                facing,
                cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity.class);
        };
    }

    protected BiPredicate<BlockState, BlockPos> matchingThreadingCore(WorldView level, StorageTierDescriptor tier,
        Direction facing) {
        return (s, p) -> {
            var context = ECOMultiblockParts.context(level, p);
            return ECOMultiblockParts.matchesTiered(
                context,
                ECOMultiblockParts.Role.COMPUTATION_THREADING_CORE,
                tier,
                s.getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock,
                facing,
                cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity.class);
        };
    }

    protected BiPredicate<BlockState, BlockPos> matchingCoolingController(WorldView level, StorageTierDescriptor tier,
        Direction facing) {
        return (s, p) -> {
            var context = ECOMultiblockParts.context(level, p);
            return ECOMultiblockParts.matchesTiered(
                context,
                ECOMultiblockParts.Role.COOLING_CONTROLLER,
                tier,
                s.getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock,
                facing,
                ECOComputationCoolingControllerBlockEntity.class);
        };
    }

}
