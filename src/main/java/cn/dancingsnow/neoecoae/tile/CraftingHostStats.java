package cn.dancingsnow.neoecoae.tile;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;

public final class CraftingHostStats {

    public static final CraftingHostStats EMPTY = new CraftingHostStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public final int patternCount;
    public final int patternBusCount;
    public final int workerCount;
    public final int runningWorkerCount;
    public final int queuedWorkCount;
    public final int parallelCount;
    public final int parallelCoreCount;
    public final int inputCachedItems;
    public final int outputCachedItems;
    public final int occupiedCacheSlots;

    private CraftingHostStats(int patternCount, int patternBusCount, int workerCount, int runningWorkerCount,
        int queuedWorkCount, int parallelCount, int parallelCoreCount, int inputCachedItems, int outputCachedItems,
        int occupiedCacheSlots) {
        this.patternCount = Math.max(0, patternCount);
        this.patternBusCount = Math.max(0, patternBusCount);
        this.workerCount = Math.max(0, workerCount);
        this.runningWorkerCount = Math.max(0, runningWorkerCount);
        this.queuedWorkCount = Math.max(0, queuedWorkCount);
        this.parallelCount = Math.max(0, parallelCount);
        this.parallelCoreCount = Math.max(0, parallelCoreCount);
        this.inputCachedItems = Math.max(0, inputCachedItems);
        this.outputCachedItems = Math.max(0, outputCachedItems);
        this.occupiedCacheSlots = Math.max(0, occupiedCacheSlots);
    }

    public static CraftingHostStats fromSaved(int patternCount, int patternBusCount, int workerCount,
        int runningWorkerCount, int parallelCount, int parallelCoreCount, int inputCachedItems, int outputCachedItems,
        int occupiedCacheSlots) {
        return new CraftingHostStats(
            patternCount,
            patternBusCount,
            workerCount,
            runningWorkerCount,
            runningWorkerCount,
            parallelCount,
            parallelCoreCount,
            inputCachedItems,
            outputCachedItems,
            occupiedCacheSlots);
    }

    public static CraftingHostStats create(TileECOController controller, List<ECOFormationBlockPos> formedMembers,
        List<ECOFormationBlockPos> hiddenMembers) {
        if (controller == null) {
            return EMPTY;
        }
        World world = controller.getWorldObj();
        if (world == null) {
            return EMPTY;
        }

        int patternCount = 0;
        int patternBusCount = 0;
        int workerCount = 0;
        int runningWorkerCount = 0;
        int queuedWorkCount = 0;
        int parallelCount = 0;
        int parallelCoreCount = 0;
        if (formedMembers != null) {
            for (ECOFormationBlockPos pos : formedMembers) {
                Block block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
                if (block == NEBlocks.craftingPatternBus) {
                    patternBusCount++;
                    if (patternCount > 100000) {
                        break;
                    }
                    TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                    if (tile instanceof TileCraftingPatternBus) {
                        patternCount = saturatedAdd(patternCount, ((TileCraftingPatternBus) tile).getPatternCount());
                    }
                } else if (block == NEBlocks.craftingWorker) {
                    workerCount++;
                    TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                    if (tile instanceof TileCraftingWorker) {
                        TileCraftingWorker worker = (TileCraftingWorker) tile;
                        if (worker.isRunning()) {
                            runningWorkerCount++;
                        }
                        queuedWorkCount = saturatedAdd(queuedWorkCount, worker.queueSize());
                    }
                } else if (isParallelCore(block)) {
                    parallelCoreCount++;
                    parallelCount = saturatedAdd(
                        parallelCount,
                        parallelContribution(pos.getTier(), controller.isCraftingOverclocked()));
                }
            }
        }

        int inputCachedItems = 0;
        int outputCachedItems = 0;
        int occupiedCacheSlots = 0;
        if (hiddenMembers != null) {
            for (ECOFormationBlockPos pos : hiddenMembers) {
                TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                if (tile instanceof TileCraftingHatch) {
                    TileCraftingHatch hatch = (TileCraftingHatch) tile;
                    if (hatch.isInput()) {
                        inputCachedItems = saturatedAdd(inputCachedItems, hatch.getCachedItemCount());
                    } else {
                        outputCachedItems = saturatedAdd(outputCachedItems, hatch.getCachedItemCount());
                    }
                    occupiedCacheSlots = saturatedAdd(occupiedCacheSlots, hatch.getOccupiedSlotCount());
                }
            }
        }

        return new CraftingHostStats(
            patternCount,
            patternBusCount,
            workerCount,
            runningWorkerCount,
            queuedWorkCount,
            parallelCount,
            parallelCoreCount,
            inputCachedItems,
            outputCachedItems,
            occupiedCacheSlots);
    }

    public static CraftingHostStats fromCache(TileECOController controller, CraftingMemberCache cache) {
        if (controller == null || cache == null) {
            return EMPTY;
        }

        int patternCount = 0;
        int patternBusCount = 0;
        for (TileCraftingPatternBus bus : cache.patternBuses()) {
            patternBusCount++;
            patternCount = saturatedAdd(patternCount, bus.getPatternCount());
        }

        int workerCount = 0;
        int runningWorkerCount = 0;
        int queuedWorkCount = 0;
        for (TileCraftingWorker worker : cache.workers()) {
            workerCount++;
            if (worker.isRunning()) {
                runningWorkerCount++;
            }
            queuedWorkCount = saturatedAdd(queuedWorkCount, worker.queueSize());
        }

        int parallelCount = 0;
        int parallelCoreCount = 0;
        World world = controller.getWorldObj();
        List<ECOFormationBlockPos> formedMembers = controller.getFormedMemberBlocks();
        if (world != null && formedMembers != null) {
            for (ECOFormationBlockPos pos : formedMembers) {
                Block block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
                if (isParallelCore(block)) {
                    parallelCoreCount++;
                    parallelCount = saturatedAdd(
                        parallelCount,
                        parallelContribution(pos.getTier(), controller.isCraftingOverclocked()));
                }
            }
        }

        int inputCachedItems = 0;
        int outputCachedItems = 0;
        int occupiedCacheSlots = 0;
        for (TileCraftingHatch hatch : cache.inputHatches()) {
            inputCachedItems = saturatedAdd(inputCachedItems, hatch.getCachedItemCount());
            occupiedCacheSlots = saturatedAdd(occupiedCacheSlots, hatch.getOccupiedSlotCount());
        }
        for (TileCraftingHatch hatch : cache.outputHatches()) {
            outputCachedItems = saturatedAdd(outputCachedItems, hatch.getCachedItemCount());
            occupiedCacheSlots = saturatedAdd(occupiedCacheSlots, hatch.getOccupiedSlotCount());
        }

        return new CraftingHostStats(
            patternCount,
            patternBusCount,
            workerCount,
            runningWorkerCount,
            queuedWorkCount,
            parallelCount,
            parallelCoreCount,
            inputCachedItems,
            outputCachedItems,
            occupiedCacheSlots);
    }

    private static boolean isParallelCore(Block block) {
        return block == NEBlocks.craftingParallelCoreL4 || block == NEBlocks.craftingParallelCoreL6
            || block == NEBlocks.craftingParallelCoreL9;
    }

    private static int parallelContribution(ECOControllerTier tier, boolean overclocked) {
        if (tier == null) {
            return 0;
        }
        int parallel = ECOEnergyProfile.craftingParallel(tier);
        return overclocked ? saturatedAdd(parallel, ECOEnergyProfile.overclockedCraftingParallel(tier)) : parallel;
    }

    private static int saturatedAdd(int left, int right) {
        if (right <= 0) {
            return left;
        }
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }
}
