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
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings.Multiblock.ComputationTopologyMode;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.structurelib.NEStructureLib;

public class NEComputationClusterCalculator extends NEClusterCalculator<NEComputationCluster> {

    private final ComputationTopologyMode topologyMode = PlanningSettings.Multiblock.computationTopologyMode;

    public NEComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return PlanningSettings.Multiblock.computationSystemMaxLength;
    }

    @Override
    protected int maxDiscoverySpan() {
        return usesStructureLib() ? maxLength() : super.maxDiscoverySpan();
    }

    public final boolean usesStructureLib() {
        return getClass() == NEComputationClusterCalculator.class
            && (topologyMode == ComputationTopologyMode.STRUCTURE_LIB
                || topologyMode == ComputationTopologyMode.OPTIMIZED);
    }

    @Override
    public final boolean usesCompactGrid() {
        return usesStructureLib() && topologyMode == ComputationTopologyMode.OPTIMIZED;
    }

    @Override
    public final boolean usesLegacyChecks() {
        return topologyMode == ComputationTopologyMode.LEGACY;
    }

    @Override
    public final boolean restrictsLegacyGridSides() {
        return true;
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
    protected void updateNewCluster(NEComputationCluster cluster) {
        if (!cluster.usesCompactGrid()) super.updateNewCluster(cluster);
    }

    @Override
    protected void updateExistingCluster(NEComputationCluster cluster, boolean claimedOwner) {
        cluster.updateStatus(claimedOwner);
        if (cluster.usesCompactGrid()) cluster.refreshValidatedGrid(claimedOwner);
    }

    @Override
    protected boolean reconcileCluster(ServerWorldView level, NEComputationCluster cluster, BlockPos min,
        BlockPos max) {
        if (!cluster.usesCompactGrid()) return false;
        var members = collectMembers(level, min, max);
        if (members.isEmpty() || !cluster.reconcileMembers(members, min, max)) return false;
        cluster.refreshValidatedGrid(false);
        return true;
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        if (owner instanceof ECOComputationSystemBlockEntity) return super.checkMultiblockScale(min, max);
        NEComputationClusterCalculator controller = candidateControllerCalculator(min, max);
        return controller != null && controller != this ? controller.checkMultiblockScale(min, max)
            : super.checkMultiblockScale(min, max);
    }

    @Override
    protected MBCalculator<NEBlockEntity<NEComputationCluster, ?>, NEComputationCluster> discoveryOwner(
        ServerWorldView level, BlockPos min, BlockPos max) {
        var controller = candidateControllerCalculator(min, max);
        return controller == null ? this : controller;
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

    @Override
    protected boolean revalidateCluster(ServerWorldView level, NEComputationCluster cluster) {
        ECOComputationSystemBlockEntity controller = cluster.getController();
        if (!usesStructureLib() || controller == null || controller != owner) return false;
        BlockPos min = cluster.getBoundsMin(), max = cluster.getBoundsMax();
        if (!chunksLoaded(level, min, max)) return false;
        var context = controllerContext(controller);
        boolean mirrored = context.state()
            .getValue(NativeMachineBlock.MIRRORED);
        return validateKnownBounds(
            level,
            min,
            max,
            () -> sameMembers(level, cluster) && verifyLayout(level, min, max, context, mirrored));
    }

    @Override
    public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
        if (!chunksLoaded(level, min, max)) return false;
        Optional<ControllerContext<ECOComputationSystemBlockEntity>> contextResult = findUniqueController(
            level,
            min,
            max,
            ECOComputationSystemBlockEntity.class);
        if (!contextResult.isPresent()) return false;
        ControllerContext<ECOComputationSystemBlockEntity> context = contextResult
            .orElseThrow(java.util.NoSuchElementException::new);
        ECOComputationSystemBlockEntity controller = context.controller();
        if (controller != owner && controller.getCalculator() instanceof NEComputationClusterCalculator calculator)
            return calculator.verifyInternalStructure(level, min, max);
        if (verifyLayout(level, min, max, context, false) || verifyLayout(level, min, max, context, true)) return true;
        clearNetworkSwitchState(level, context.position(), context.state());
        controller.setMirrored(false);
        return false;
    }

    private boolean verifyLayout(ServerWorldView level, BlockPos min, BlockPos max,
        ControllerContext<ECOComputationSystemBlockEntity> context, boolean mirrored) {
        ECOComputationSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        Direction interfaceSide = mirrored ? context.left() : context.right();
        Direction expandSide = interfaceSide.getOpposite();
        boolean useBlueprint = usesStructureLib();
        int repeats = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) - 3;
        boolean valid = useBlueprint
            ? NEStructureLib
                .check(controller, controller.getBuildDefinition(), repeats, context.front(), mirrored, min, max)
            : verifyStructure(
                level,
                controllerPos,
                controller.getTier(),
                context.front(),
                context.back(),
                context.top(),
                context.down(),
                interfaceSide,
                expandSide,
                interfaceSide,
                mirrored);
        if (!valid) return false;
        controller.setMirrored(mirrored);
        setCoolingControllersMirrored(
            level,
            min,
            max,
            mirrored,
            useBlueprint ? controllerPos.relative(expandSide, repeats + 2) : null);
        syncNetworkSwitchState(level, controllerPos, context.state(), mirrored);
        return true;
    }

    @Override
    protected void invalidateStructure() {
        var cluster = owner.getCluster();
        if (cluster != null && cluster.hasPermanentRemoval()) cluster.breakCluster();
        else super.invalidateStructure();
    }

    @Override
    public void requestMultiblockCheck(ServerWorldView level, BlockPos location) {
        NEComputationCluster cluster = owner.getCluster();
        var controller = cluster != null && !cluster.isDestroyed() ? cluster.getController() : null;
        if (controller != null && controller != owner && !controller.isRemoved() && controller.getLevel() == level) {
            controller.getCalculator()
                .requestMultiblockCheck(level, controller.getBlockPos());
        } else super.requestMultiblockCheck(level, location);
    }

    @Override
    protected boolean sameMembers(ServerWorldView level, NEComputationCluster cluster) {
        if (!usesStructureLib()) return true;
        return super.sameMembers(level, cluster);
    }

    private static void setCoolingControllersMirrored(ServerWorldView level, BlockPos min, BlockPos max,
        boolean mirrored, BlockPos expectedCooler) {
        if (expectedCooler != null
            && level.getBlockEntity(expectedCooler) instanceof ECOComputationCoolingControllerBlockEntity cooler) {
            cooler.setMirrored(mirrored);
            return;
        }
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
        setSwitchFlags(level, controllerPos, normal, highEnergy);
    }

    private static void clearNetworkSwitchState(ServerWorldView level, BlockPos controllerPos,
        BlockState controllerState) {
        NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
        setSwitchFlags(level, controllerPos, false, false);
    }

    private static void setSwitchFlags(ServerWorldView level, BlockPos controllerPos, boolean normal,
        boolean highEnergy) {
        BlockState current = level.getBlockState(controllerPos);
        BlockState next = current.setValue(NativeMachineBlock.NETWORK_SWITCH, normal)
            .setValue(NativeMachineBlock.HIGH_ENERGY_NETWORK_SWITCH, highEnergy);
        if (next != current) level.setBlock(controllerPos, next, 2);
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
