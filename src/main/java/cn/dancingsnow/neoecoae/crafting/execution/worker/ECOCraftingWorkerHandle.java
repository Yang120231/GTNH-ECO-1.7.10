package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.UUID;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;

/** Live worker ownership operations used by CPU termination and unloaded-worker reconciliation. */
public interface ECOCraftingWorkerHandle {

    World getNativeLevel();

    GridView getWorkerGrid();

    void releaseCompletedJobOutputs(UUID job);

    void recoverTerminatedJob(UUID job);
}
