package cn.dancingsnow.neoecoae.crafting.ae2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingJobV2;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningEngine;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionHost;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionRuntime;

/** AE2's normal confirmation/submission lifecycle with an immutable ECO plan and native fallback. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class ECOCraftingJob implements ICraftingJob, Future<ICraftingJob> {

    private final World world;
    private final IGrid grid;
    private final BaseActionSource source;
    private final IAEStack output;
    private final ICraftingCallback callback;
    private final ECOCraftingSnapshot snapshot;
    private volatile boolean cancelled;
    private volatile boolean done;
    private ECOPlanningResult<ECOResourceKey, ICraftingPatternDetails> result;
    private CraftingJobV2 nativeJob;

    public ECOCraftingJob(World world, IGrid grid, BaseActionSource source, IAEStack<?> output,
        ICraftingCallback callback, ECOCraftingSnapshot snapshot) {
        this.world = world;
        this.grid = grid;
        this.source = source;
        this.output = output.copy();
        this.callback = callback;
        this.snapshot = snapshot;
    }

    @Override
    public synchronized boolean simulateFor(int milliseconds) {
        if (done || cancelled) return false;
        calculatePlanIfNeeded();
        if (nativeJob != null && nativeJob.simulateFor(Math.max(1, milliseconds))) return true;
        if (cancelled) return false;
        done = true;
        if (callback != null) callback.calculationComplete(this);
        return false;
    }

    private void calculatePlanIfNeeded() {
        if (result != null || nativeJob != null) return;
        Map<ECOResourceKey, Long> inventory = new LinkedHashMap<>(snapshot.inventory);
        result = new ECOPlanningEngine<>(snapshot.recipes, () -> cancelled, 32768, TimeUnit.MILLISECONDS.toNanos(50))
            .planAdditional(new ECOResourceKey(output), output.getStackSize(), inventory);
        if (result.status != ECOPlanningResult.Status.SUCCESS
            && result.status != ECOPlanningResult.Status.AMOUNT_OVERFLOW
            && !cancelled)
            nativeJob = new CraftingJobV2(world, grid, source, output.copy(), CraftingMode.STANDARD, null);
    }

    @Override
    public Future<ICraftingJob> schedule() {
        TickHandler.INSTANCE.registerCraftingSimulation(world, this);
        return this;
    }

    @Override
    public boolean isSimulation() {
        return !done
            || (nativeJob != null ? nativeJob.isSimulation() : result.status != ECOPlanningResult.Status.SUCCESS);
    }

    @Override
    public long getByteTotal() {
        return nativeJob != null ? nativeJob.getByteTotal() : result == null ? 0 : result.bytes;
    }

    @Override
    public IAEStack getOutput() {
        return output.copy();
    }

    @Override
    public CraftingMode getCraftingMode() {
        return CraftingMode.STANDARD;
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        return nativeJob != null ? nativeJob.getStorageAtBeginning() : snapshot.storage;
    }

    @Override
    public void populatePlan(IItemList plan) {
        if (nativeJob != null) {
            nativeJob.populatePlan(plan);
            return;
        }
        if (result == null) return;
        result.extracted.forEach((key, count) -> plan.add(key.stack(count)));
        for (ECOPlanningResult.Step<ECOResourceKey, ICraftingPatternDetails> step : result.schedule) {
            step.recipe.outputs.forEach(
                (key, count) -> plan.addRequestable(
                    key.stack(0)
                        .setCountRequestable(Math.multiplyExact(count, step.crafts))
                        .setCountRequestableCrafts(step.crafts)));
        }
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cpu) {
        return nativeJob != null ? nativeJob.supportsCPUCluster(cpu)
            : cpu instanceof CraftingCPUCluster && cpu instanceof ECOExecutionHost;
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU cpu, BaseActionSource actionSource) {
        if (cancelled || !done || isSimulation()) throw new IllegalStateException("Unfinished crafting plan");
        if (nativeJob != null) {
            nativeJob.startCrafting(storage, cpu, actionSource);
            return;
        }
        CraftingCPUCluster cluster = (CraftingCPUCluster) cpu;
        // Revalidate descriptions before touching even the speculative AE inventory.
        for (ECOPlanningResult.Step<ECOResourceKey, ICraftingPatternDetails> step : result.schedule) {
            if (!step.recipe.inputs.equals(ECOCraftingSnapshot.amounts(step.recipe.token.getCondensedAEInputs()))
                || !step.recipe.outputs.equals(ECOCraftingSnapshot.amounts(step.recipe.token.getCondensedAEOutputs())))
                throw new CraftBranchFailure(output, output.getStackSize());
        }
        for (Map.Entry<ECOResourceKey, Long> entry : result.extracted.entrySet()) {
            IAEStack requested = entry.getKey()
                .stack(entry.getValue());
            IAEStack<?> extracted = storage.extractItems(requested, Actionable.MODULATE);
            if (extracted == null || extracted.getStackSize() != requested.getStackSize())
                throw new CraftBranchFailure(requested, requested.getStackSize());
            cluster.addStorage(extracted);
        }
        for (ECOPlanningResult.Step<ECOResourceKey, ICraftingPatternDetails> step : result.schedule)
            cluster.addCrafting(step.recipe.token, step.crafts);
        ((ECOExecutionHost) cpu).neoecoae$setExecution(new ECOExecutionRuntime(result));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (done || cancelled) return false;
        cancelled = true;
        if (nativeJob != null) nativeJob.cancel(mayInterruptIfRunning);
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return done || cancelled;
    }

    @Override
    public ICraftingJob get() {
        while (!isDone()) simulateFor(10);
        if (cancelled) throw new CancellationException();
        return this;
    }

    @Override
    public ICraftingJob get(long timeout, TimeUnit unit) throws TimeoutException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!isDone() && System.nanoTime() - deadline < 0) simulateFor(1);
        if (cancelled) throw new CancellationException();
        if (!done) throw new TimeoutException();
        return this;
    }
}
