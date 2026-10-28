package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Immutable native terminal facade; the original ECO plan and its execution contract remain intact. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class NativeECOCraftingJob implements ICraftingJob {

    private final ICraftingPlan plan;
    private final IAEStack<?> output;
    private final MECraftingInventory snapshot;
    private final String error;
    private final net.minecraft.world.World world;
    private final appeng.api.networking.IGrid grid;
    private final cn.dancingsnow.neoecoae.platform.network.ServerTasks.Session session;
    private volatile Boolean nativeCpuSupport;

    NativeECOCraftingJob(ICraftingPlan plan, IAEStack<?> output, MECraftingInventory snapshot, String error,
        net.minecraft.world.World world, appeng.api.networking.IGrid grid,
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.Session session) {
        this.world = world;
        this.grid = grid;
        this.session = session;
        this.plan = plan;
        this.output = output.copy();
        this.snapshot = snapshot;
        this.error = error;
    }

    public boolean belongsTo(net.minecraft.world.World world, appeng.api.networking.IGrid grid) {
        return this.world == world && this.grid == grid && session.isRunning();
    }

    /** Planned against this grid in the running server session; required before any native CPU stages it. */
    public boolean belongsToGrid(appeng.api.networking.IGrid grid) {
        return this.grid == grid && session.isRunning();
    }

    public ICraftingPlan sourcePlan() {
        return plan;
    }

    /**
     * Submit-ready copy for the report's Shift force start: missing inputs become awaited items (1.21 force start).
     * A complete plan, or a failed planning attempt, is returned unchanged.
     */
    public NativeECOCraftingJob forced() {
        if (plan == null || !plan.simulation()) return this;
        return new NativeECOCraftingJob(
            new cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan(plan),
            output,
            snapshot,
            error,
            world,
            grid,
            session);
    }

    @Override
    public boolean isSimulation() {
        return plan == null || plan.simulation();
    }

    @Override
    public long getByteTotal() {
        return plan == null ? 0 : plan.bytes();
    }

    @Override
    public IAEStack getOutput() {
        return output.copy();
    }

    @Override
    public boolean simulateFor(int millis) {
        return false;
    }

    @Override
    public Future schedule() {
        return CompletableFuture.completedFuture(this);
    }

    /**
     * Whether a native AE2 CPU can run this plan unchanged: an ordinary ECO DAG made only of native patterns.
     * Cycle, exact-order and fuzzy-input plans still require the ECO runtime.
     */
    public boolean supportsNativeCpus() {
        Boolean supported = nativeCpuSupport;
        if (supported == null) {
            supported = plan != null
                && cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport.supportsPlan(plan);
            nativeCpuSupport = supported;
        }
        return supported;
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cpu) {
        if (cpu instanceof NativeECOCpu) return true;
        return cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster && supportsNativeCpus();
    }

    @Override
    public CraftingMode getCraftingMode() {
        return CraftingMode.STANDARD;
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        return snapshot;
    }

    public String getErrorMessage() {
        return error;
    }

    /**
     * Native AE2 CPU fallback: stages the plan's task vector exactly like AE2's own jobs. ECO CPUs never reach
     * this path because their identity submits the original plan to the ECO runtime.
     */
    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU cpu,
        appeng.api.networking.security.BaseActionSource source) {
        if (cpu instanceof NativeECOCpu) throw new IllegalStateException(
            "ECO execution contracts must be submitted through their native ECO CPU identity");
        if (!(cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster) || !supportsNativeCpus())
            // AE2 rolls back the staged CPU state for a branch failure, including its merge path.
            throw new appeng.crafting.CraftBranchFailure(output, output.getStackSize());
        var cluster = (appeng.me.cluster.implementations.CraftingCPUCluster) cpu;
        for (var task : plan.patternTimes()
            .entrySet())
            cluster.addCrafting(
                ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails) task.getKey()).nativeDetails(),
                task.getValue());
        for (var entry : plan.usedItems()) pull(
            storage,
            cluster,
            entry.getKey()
                .toNative(entry.getLongValue()));
        for (var entry : plan.emittedItems()) cluster.addEmitable(
            entry.getKey()
                .toNative(entry.getLongValue()));
    }

    private static void pull(MECraftingInventory storage, appeng.me.cluster.implementations.CraftingCPUCluster cluster,
        IAEStack<?> stack) {
        long wanted = stack.getStackSize();
        if (wanted <= 0) return;
        IAEStack<?> extracted = storage.extractItems(stack.copy(), appeng.api.config.Actionable.MODULATE);
        long got = extracted == null ? 0 : extracted.getStackSize();
        if (got < wanted) {
            if (!cluster.isMissingMode()) throw new appeng.crafting.CraftBranchFailure(stack, wanted);
            cluster.addEmitable(
                stack.copy()
                    .setStackSize(wanted - got));
        }
        if (extracted != null && got > 0) cluster.addStorage(extracted);
    }

    @Override
    public void populatePlan(IItemList target) {
        if (plan == null) {
            target.add(output.copy());
            return;
        }
        for (var entry : plan.usedItems()) target.add(
            entry.getKey()
                .toNative(entry.getLongValue()));
        for (var entry : plan.missingItems()) target.add(
            entry.getKey()
                .toNative(entry.getLongValue()));
        for (var entry : plan.emittedItems()) {
            target.add(
                entry.getKey()
                    .toNative(entry.getLongValue()));
            target.addRequestable(
                entry.getKey()
                    .toNative(0)
                    .setCountRequestable(entry.getLongValue()));
        }
        KeyCounter crafting = new KeyCounter();
        for (var task : plan.patternTimes()
            .entrySet())
            for (var stack : task.getKey()
                .getOutputs())
                crafting.add(
                    stack.what(),
                    cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingMultiply(stack.amount(), task.getValue()));
        for (var entry : crafting) target.addRequestable(
            entry.getKey()
                .toNative(0)
                .setCountRequestable(entry.getLongValue()));
    }
}
