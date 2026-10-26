package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.UUID;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;

/** Worker ownership operations invoked by CPU job lifecycle transitions. */
final class ECOCraftingWorkerRecovery {

    private ECOCraftingWorkerRecovery() {}

    static void releaseCompletedOutputs(GridView grid, UUID craftingJobId) {
        if (grid == null) return;
        for (var worker : cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkers.onGrid(grid)) {
            worker.releaseCompletedJobOutputs(craftingJobId);
        }
    }

    static void recoverTerminatedInputs(World level, UUID craftingJobId) {
        if (level == null || cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level) == null)
            return;
        // Loaded workers may already have left this grid. Unloaded workers reconcile the durable decision later.
        for (var worker : cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkers.loaded()) {
            if (worker.getNativeLevel() != null
                && cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(worker.getNativeLevel())
                    == cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level)) {
                worker.recoverTerminatedJob(craftingJobId);
            }
        }
    }
}
