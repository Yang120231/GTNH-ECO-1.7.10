package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;

/** MBCalculator target for the powered large workstation controller. */
public class NEIntegratedWorkingStationControllerCalculator
    extends MBCalculator<ECOLargeIntegratedWorkingStationBlockEntity, NEIntegratedWorkingStationCluster> {

    public NEIntegratedWorkingStationControllerCalculator(ECOLargeIntegratedWorkingStationBlockEntity target) {
        super(target);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        return sizeY == 2 && ((sizeX == 3 && sizeZ == 2) || (sizeX == 2 && sizeZ == 3));
    }

    @Override
    public NEIntegratedWorkingStationCluster createCluster(ServerWorldView level, BlockPos min, BlockPos max) {
        return new NEIntegratedWorkingStationCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
        BlockPos controller = target.getBlockPos();
        if (controller.getY() != max.getY()) return false;
        BlockState state = target.getBlockState();
        Direction front = state.getValue(WorkstationMachines.FACING);
        Direction back = front.getOpposite();
        Direction left = front.getCounterClockWise();
        Direction right = left.getOpposite();
        if (!is(level, controller.relative(left), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)
            || !is(level, controller.relative(right), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)) return false;
        BlockPos expectedMin = controller.below().relative(left);
        BlockPos expectedMax = controller.relative(right).relative(back);
        if (Math.min(expectedMin.getX(), expectedMax.getX()) != min.getX()
            || Math.max(expectedMin.getX(), expectedMax.getX()) != max.getX()
            || Math.min(expectedMin.getZ(), expectedMax.getZ()) != min.getZ()
            || Math.max(expectedMin.getZ(), expectedMax.getZ()) != max.getZ()) return false;
        BlockPos lowerCenter = controller.below();
        if (!is(level, lowerCenter, cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)
            || !is(level, lowerCenter.relative(left), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)
            || !is(level, lowerCenter.relative(right), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)) {
            return false;
        }

        BlockPos functionalCenter = lowerCenter.relative(back);
        if (!is(level, functionalCenter, cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INTERFACE)
            || !is(level, functionalCenter.relative(left), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH)
            || !is(level, functionalCenter.relative(right), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH)) {
            return false;
        }

        BlockPos upperFunctionalCenter = controller.relative(back);
        return is(level, upperFunctionalCenter, cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)
            && is(level, upperFunctionalCenter.relative(left), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING)
            && is(level, upperFunctionalCenter.relative(right), cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING);
    }

    @Override
    public void updateBlockEntities(
        NEIntegratedWorkingStationCluster cluster,
        ServerWorldView level,
        BlockPos min,
        BlockPos max
    ) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            TileEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !isValidBlockEntity(blockEntity)) {
                disconnect();
                return;
            }
            cluster.addBlockEntity(blockEntity);
        }
        for (TileEntity blockEntity : snapshot(cluster)) {
            if (blockEntity instanceof NEBlockEntity<?, ?> member) {
                updateClusterUnchecked(member, cluster);
            } else if (blockEntity instanceof ECOLargeIntegratedWorkingStationBlockEntity member) {
                member.updateCluster(cluster);
            }
        }
        cluster.updateFormed(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void updateClusterUnchecked(NEBlockEntity<?, ?> member, NEIntegratedWorkingStationCluster cluster) {
        ((NEBlockEntity) member).updateCluster(cluster);
    }

    private static java.util.List<TileEntity> snapshot(NEIntegratedWorkingStationCluster cluster) {
        java.util.List<TileEntity> result = new java.util.ArrayList<>();
        cluster.getBlockEntities().forEachRemaining(result::add);
        return result;
    }

    @Override
    public boolean isValidBlockEntity(TileEntity blockEntity) {
        return blockEntity instanceof ECOLargeIntegratedWorkingStationBlockEntity
            || (blockEntity instanceof NEBlockEntity<?, ?> member
                && member.getCalculator() instanceof NEIntegratedWorkingStationClusterCalculator);
    }

    private static boolean is(ServerWorldView level, BlockPos pos, cn.dancingsnow.neoecoae.platform.minecraft.Holder<Block> block) {
        return level.getBlockState(pos).is(block);
    }
}
