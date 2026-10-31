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

    void setSelectionMode(CpuSelectionMode mode);

    boolean isActive();

    void cancelJob(ICraftingPlan plan);

    void deactivate(ICraftingPlan plan);

    boolean replaceBigOrderPlan(ECOCraftingCPU cpu, ICraftingPlan plan);

    /** Swaps the running job's byte reservation for a merged plan; false when the extra bytes do not fit. */
    default boolean replaceMergedPlan(ECOCraftingCPU cpu, ICraftingPlan plan) {
        return false;
    }

    World getLevel();

    GridView getGrid();

    IActionSource getActionSource();

    appeng.api.networking.crafting.ICraftingCPU nativeCPU(ECOCraftingCPU cpu);

    cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult submitJob(GridView grid, ICraftingPlan plan,
        IActionSource source, appeng.api.networking.crafting.ICraftingRequester requester);

    cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions getPlanningOptions();

    ECOCraftingCPU findCPU(ICraftingPlan plan);

    /** Releases CPUs whose job finished; true when the published CPU set changed. Idempotent. */
    boolean pruneInactiveCPUs();

    /**
     * Releases finished CPUs in the whole {@link #submissionPool()}. A network member whose CPU finished before
     * AE2 ever listed it is otherwise never visited, and its stale thread would hide the group's placeholder.
     */
    default boolean pruneSubmissionPool() {
        return pruneInactiveCPUs();
    }

    /** Admission pool that {@link #submitJob} really submits into (a computation network or this host). */
    default Object submissionPool() {
        return this;
    }

    /** Whether a new job can still get a thread from {@link #submissionPool()}. */
    default boolean hasFreeThread() {
        return true;
    }

    default boolean isPreferredFor(IActionSource source) {
        return false;
    }
}
