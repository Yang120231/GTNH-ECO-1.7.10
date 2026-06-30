package cn.dancingsnow.neoecoae.gui.computation;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEStorageItems;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileComputationDrive;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public final class ComputationHostStats {

    public static final ComputationHostStats EMPTY = new ComputationHostStats(0, 0, 0, 0L);

    public final int totalThreads;
    public final int parallelCount;
    public final int parallelCores;
    public final long totalBytes;

    private ComputationHostStats(int totalThreads, int parallelCount, int parallelCores, long totalBytes) {
        this.totalThreads = Math.max(0, totalThreads);
        this.parallelCount = Math.max(0, parallelCount);
        this.parallelCores = Math.max(0, parallelCores);
        this.totalBytes = Math.max(0L, totalBytes);
    }

    public static ComputationHostStats create(TileECOController controller, List<ECOFormationBlockPos> positions) {
        if (controller == null || positions == null || positions.isEmpty()) {
            return EMPTY;
        }
        World world = controller.getWorldObj();
        if (world == null) {
            return EMPTY;
        }

        int totalThreads = 0;
        int parallelCount = 0;
        int parallelCores = 0;
        long totalBytes = 0L;
        for (ECOFormationBlockPos pos : positions) {
            Block block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
            ECOControllerTier memberTier = pos.getTier();
            if (isThreadingCore(block)) {
                totalThreads += threadContribution(memberTier);
            } else if (isParallelCore(block)) {
                parallelCount += acceleratorContribution(memberTier);
                parallelCores++;
            }

            TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileComputationDrive) {
                totalBytes = saturatedAdd(totalBytes, computationBytes((TileComputationDrive) tile));
            }
        }
        return new ComputationHostStats(totalThreads, parallelCount, parallelCores, totalBytes);
    }

    private static int threadContribution(ECOControllerTier tier) {
        if (tier == null) {
            return 0;
        }
        if (tier == ECOControllerTier.L9) {
            return ECOEnergyProfile.computationThreads(ECOControllerTier.L9);
        }
        if (tier == ECOControllerTier.L6) {
            return ECOEnergyProfile.computationThreads(ECOControllerTier.L6);
        }
        return ECOEnergyProfile.computationThreads(ECOControllerTier.L4);
    }

    private static int acceleratorContribution(ECOControllerTier tier) {
        if (tier == null) {
            return 0;
        }
        if (tier == ECOControllerTier.L9) {
            return ECOEnergyProfile.computationAccelerators(ECOControllerTier.L9);
        }
        if (tier == ECOControllerTier.L6) {
            return ECOEnergyProfile.computationAccelerators(ECOControllerTier.L6);
        }
        return ECOEnergyProfile.computationAccelerators(ECOControllerTier.L4);
    }

    private static boolean isThreadingCore(Block block) {
        return block == NEBlocks.computationThreadingCoreL4 || block == NEBlocks.computationThreadingCoreL6
            || block == NEBlocks.computationThreadingCoreL9;
    }

    private static boolean isParallelCore(Block block) {
        return block == NEBlocks.computationParallelCoreL4 || block == NEBlocks.computationParallelCoreL6
            || block == NEBlocks.computationParallelCoreL9;
    }

    private static long computationBytes(TileComputationDrive drive) {
        ItemStack stack = drive.getCellStack();
        if (stack != null && stack.getItem() instanceof NEStorageItems.ECOComputationCellItem) {
            return ((NEStorageItems.ECOComputationCellItem) stack.getItem()).getBytes();
        }
        return 0L;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
