package cn.dancingsnow.neoecoae.crafting.execution;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;

/** Live computation-cluster services consumed by the unchanged CPU ownership rules. */
public interface ECOCraftingCPUHost {

    long getAvailableStorage();

    boolean isPlanRegistered(ICraftingPlan plan);

    int getPooledParallelism();

    CpuSelectionMode getSelectionMode();

    boolean isActive();

    void cancelJob(ICraftingPlan plan);

    void deactivate(ICraftingPlan plan);

    boolean replaceBigOrderPlan(ECOCraftingCPU cpu, ICraftingPlan plan);

    World getLevel();

    GridView getGrid();

    IActionSource getActionSource();

    appeng.api.networking.crafting.ICraftingCPU nativeCPU(ECOCraftingCPU cpu);

    cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult submitJob(GridView grid, ICraftingPlan plan,
        IActionSource source, appeng.api.networking.crafting.ICraftingRequester requester);

    cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions getPlanningOptions();

    ECOCraftingCPU findCPU(ICraftingPlan plan);
}
