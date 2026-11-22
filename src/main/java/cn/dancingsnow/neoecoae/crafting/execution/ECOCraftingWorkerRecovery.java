package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.UUID;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkers;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;

/** Worker ownership operations invoked by CPU job lifecycle transitions. */
final class ECOCraftingWorkerRecovery {

    private ECOCraftingWorkerRecovery() {}

    static void releaseCompletedOutputs(World level, GridView grid, UUID craftingJobId) {
        if (grid == null) return;
        for (var worker : ECOCraftingWorkers.forJob(level, craftingJobId)) {
            if (worker.getWorkerGrid() == grid) worker.releaseCompletedJobOutputs(craftingJobId);
        }
    }

}
