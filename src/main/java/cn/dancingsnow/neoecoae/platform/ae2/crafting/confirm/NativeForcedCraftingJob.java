package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.item.IAEStackList;

/** Submits a completed native shortage plan, replacing simulated inputs with CPU waiting items. */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class NativeForcedCraftingJob implements ICraftingJob {

    private final ICraftingJob delegate;

    NativeForcedCraftingJob(ICraftingJob delegate) {
        if (!supports(delegate)) throw new IllegalArgumentException("Native plan cannot be force-started");
        this.delegate = delegate;
    }

    static boolean supports(ICraftingJob job) {
        if (job == null) return false;
        String error = job.getErrorMessage();
        if (error != null && !error.isEmpty()) return false;
        if (job instanceof CraftingJobV2 v2) return v2.isDone() && !v2.isCancelled() && v2.getContext() != null;
        // The lite planner already stages missing inputs when the CPU is in IGNORE_MISSING mode.
        return job.getClass()
            .getName()
            .equals("appeng.crafting.fast.CraftingJobFast");
    }

    @Override
    public boolean isSimulation() {
        return false;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return CraftingMode.IGNORE_MISSING;
    }

    @Override
    public long getByteTotal() {
        return delegate.getByteTotal();
    }

    @Override
    public IAEStack getOutput() {
        return delegate.getOutput();
    }

    @Override
    public void populatePlan(IItemList plan) {
        delegate.populatePlan(plan);
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cpu) {
        return delegate.supportsCPUCluster(cpu);
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        return delegate.getStorageAtBeginning();
    }

    @Override
    public boolean simulateFor(int millis) {
        return false;
    }

    @Override
    public Future schedule() {
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU cpu, BaseActionSource source) {
        if (!(delegate instanceof CraftingJobV2 v2)) {
            delegate.startCrafting(storage, cpu, source);
            return;
        }
        CraftingCPUCluster cluster = (CraftingCPUCluster) cpu;
        var context = v2.getContext();
        context.actionSource = source;
        for (var task : context.getResolvedTasks()) {
            if (task instanceof SimulateMissingItemResolver.ConjureItemTask) {
                IItemList<IAEStack<?>> missing = new IAEStackList();
                task.populatePlan(missing);
                for (IAEStack<?> stack : missing) cluster.addEmitable(stack.copy());
            } else task.startOnCpu(context, cluster, storage);
        }
    }
}
