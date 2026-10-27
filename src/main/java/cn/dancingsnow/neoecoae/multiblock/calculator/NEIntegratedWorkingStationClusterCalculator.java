package cn.dancingsnow.neoecoae.multiblock.calculator;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

/** Verifies the fixed 3x2x2 large integrated working station footprint. */
public class NEIntegratedWorkingStationClusterCalculator
    extends NEClusterCalculator<NEIntegratedWorkingStationCluster> {

    public NEIntegratedWorkingStationClusterCalculator(NEBlockEntity<NEIntegratedWorkingStationCluster, ?> target) {
        super(target);
    }

    @Override
    protected int maxLength() {
        return 3;
    }

    @Override
    protected Holder<Block> casing() {
        return cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING;
    }

    @Override
    public void calculateMultiblock(ServerWorldView level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.hasChunkAt(candidate)
                && level.getBlockEntity(candidate) instanceof ECOLargeIntegratedWorkingStationBlockEntity controller) {
                controller.rebuildMultiblock();
            }
        }
    }

    @Override
    public void updateMultiblockAfterNeighborUpdate(ServerWorldView level, BlockPos pos, BlockPos changedPos) {
        calculateMultiblock(level, pos);
    }

    @Override
    public NEIntegratedWorkingStationCluster createCluster(ServerWorldView level, BlockPos min, BlockPos max) {
        return new NEIntegratedWorkingStationCluster(min, max);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        return sizeY == 2 && ((sizeX == 3 && sizeZ == 2) || (sizeX == 2 && sizeZ == 3));
    }

    @Override
    public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
        // Controller validation is performed by the powered controller calculator. These
        // element entities intentionally never form a cluster on their own.
        return false;
    }

    @Override
    public boolean isValidBlockEntity(TileEntity blockEntity) {
        return blockEntity instanceof NEBlockEntity<?, ?>ne
            && ne.getCalculator() instanceof NEIntegratedWorkingStationClusterCalculator;
    }
}
