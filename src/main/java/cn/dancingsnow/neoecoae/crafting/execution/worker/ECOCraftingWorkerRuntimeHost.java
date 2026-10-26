package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.networking.security.IActionHost;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Actual worker inventory and lifecycle services required by the source thread implementation. */
public interface ECOCraftingWorkerRuntimeHost extends ECOCraftingWorkerHandle, IActionHost {

    BlockPos getBlockPos();

    ECOCraftingFastPathCache getFastPathCache();

    int getAvailableBatchCapacity();

    boolean isControlledBy(ECOCraftingControllerHost controller);

    void markDisplayDirty();

    void onBatchStarted();

    void onBatchStopped();

    void onThreadAvailabilityChanged();

    void setChanged();
}
