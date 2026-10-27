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

    public ICraftingPlan sourcePlan() {
        return plan;
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

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cpu) {
        return cpu instanceof NativeECOCpu;
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

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU cpu,
        appeng.api.networking.security.BaseActionSource source) {
        throw new IllegalStateException(
            "ECO execution contracts must be submitted through their native ECO CPU identity");
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
