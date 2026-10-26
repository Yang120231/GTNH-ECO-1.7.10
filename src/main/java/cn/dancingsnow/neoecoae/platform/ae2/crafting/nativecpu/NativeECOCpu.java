package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.util.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.*;
import appeng.api.util.WorldCoord;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.*;

/** AE2 CPU identity: only the imported ECO runtime owns execution and inventory. */
public final class NativeECOCpu extends CraftingCPUCluster
    implements cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter {

    private final NativeECOCpuRuntime runtime;

    public NativeECOCpu(NativeECOCpuRuntime runtime) {
        super(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        this.runtime = runtime;
        runtime.bindNativeCPU(this);
    }

    @Override
    public appeng.api.config.CraftingAllow getCraftingAllowMode() {
        return runtime.selectionMode();
    }

    @Override
    public boolean isActive() {
        return runtime.isActive();
    }

    @Override
    public boolean isBusy() {
        return runtime.isBusy();
    }

    @Override
    public boolean isDestroyed() {
        return !runtime.isActive() && runtime.world() == null;
    }

    public boolean isCoreInvalid() {
        return false;
    }

    @Override
    public IGrid getGrid() {
        return runtime.grid() == null ? null
            : runtime.grid()
                .nativeGrid();
    }

    @Override
    public String getName() {
        return runtime.name();
    }

    @Override
    public long getAvailableStorage() {
        return runtime.availableStorage();
    }

    @Override
    public long getUsedStorage() {
        return runtime.usedStorage();
    }

    @Override
    public int getCoProcessors() {
        return runtime.coProcessors();
    }

    @Override
    public BaseActionSource getActionSource() {
        return runtime.actionSource();
    }

    @Override
    public ICraftingLink getLastCraftingLink() {
        return runtime.lastLink();
    }

    @Override
    public boolean isCraftingLinkStandalone() {
        return runtime.lastLink() != null && runtime.lastLink()
            .isStandalone();
    }

    @Override
    public IAEStack<?> getFinalMultiOutput() {
        var out = runtime.finalOutput();
        return out == null ? null
            : out.what()
                .toNative(runtime.remainingOutput());
    }

    @Override
    public void setSuspended(boolean value) {
        runtime.setSuspended(value);
    }

    @Override
    public boolean isSuspended() {
        return runtime.isSuspended();
    }

    @Override
    public long getStartItemCount() {
        return runtime.startedWork();
    }

    @Override
    public long getRemainingItemCount() {
        return runtime.remainingOutput();
    }

    @Override
    public long getElapsedTime() {
        return runtime.elapsedTimeNanos();
    }

    @Override
    public void cancel() {
        runtime.cancel();
    }

    @Override
    public void updateCraftingLogic(IGrid grid, IEnergyGrid energy, CraftingGridCache cache) {
        runtime.tick();
    }

    @Override
    public boolean canAccept(IAEStack<?> stack) {
        AEKey key = AEKey.fromNative(stack);
        return key != null && runtime.waitingFor(key) > 0;
    }

    @Override
    public IAEStack<?> injectItems(IAEStack<?> stack, Actionable mode, BaseActionSource source) {
        if (stack == null) return null;
        AEKey key = AEKey.fromNative(stack);
        if (key == null) return stack;
        long accepted = runtime.insert(key, stack.getStackSize(), mode);
        return accepted >= stack.getStackSize() ? null
            : stack.copy()
                .setStackSize(stack.getStackSize() - accepted);
    }

    @Override
    public boolean isMaking(IAEStack<?> stack) {
        AEKey key = AEKey.fromNative(stack);
        return key != null && runtime.waitingFor(key) > 0;
    }

    @Override
    public boolean isMaking(IAEItemStack stack) {
        return isMaking((IAEStack<?>) stack);
    }

    @Override
    public void getModernListOfItem(IItemList<IAEStack<?>> list, CraftingItemList which) {
        KeyCounter items = new KeyCounter();
        runtime.getItems(items, which);
        for (var entry : items) list.add(
            entry.getKey()
                .toNative(entry.getLongValue()));
    }

    @Override
    public void getListOfItem(IItemList<IAEItemStack> list, CraftingItemList which) {
        KeyCounter items = new KeyCounter();
        runtime.getItems(items, which);
        for (var entry : items) {
            var item = entry.getKey()
                .toNative(entry.getLongValue());
            if (item instanceof IAEItemStack) list.add((IAEItemStack) item);
        }
    }

    @Override
    public long getStackAmount(IAEStack stack, CraftingItemList which) {
        if (stack == null) return 0;
        KeyCounter items = new KeyCounter();
        runtime.getItems(items, which);
        return items.get(AEKey.fromNative(stack));
    }

    @Override
    public ICraftingLink submitJob(IGrid grid, ICraftingJob job, BaseActionSource source,
        ICraftingRequester requester) {
        if (job == null || job.isSimulation()
            || runtime.isBusy()
            || !runtime.isActive()
            || job.getByteTotal() > runtime.availableStorage()
            || !job.supportsCPUCluster(this)) return null;
        try {
            return runtime.submit(NativeJobPlan.capture(grid, job, source, runtime.world()), source, requester);
        } catch (appeng.crafting.CraftBranchFailure changedInventory) {
            return null;
        }
    }

    @Override
    public long neoecoae$insertIntoCpuForJob(UUID job, AEKey key, long amount, Actionable mode) {
        return runtime.neoecoae$insertIntoCpuForJob(job, key, amount, mode);
    }

    @Override
    public java.math.BigInteger neoecoae$insertExactIntoCpuForJob(UUID job, AEKey key, java.math.BigInteger amount,
        Actionable mode) {
        return runtime.neoecoae$insertExactIntoCpuForJob(job, key, amount, mode);
    }

    /** Snapshot native job by using its original staging transaction without committing any network extraction. */
    private static final class NativeJobPlan extends CraftingCPUCluster {

        private final KeyCounter used = new KeyCounter(), emitted = new KeyCounter();
        private final Map<IPatternDetails, Long> patterns = new LinkedHashMap<>();
        private final net.minecraft.world.World world;

        private NativeJobPlan(net.minecraft.world.World world) {
            super(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
            this.world = world;
        }

        @Override
        public void addStorage(IAEStack<?> stack) {
            var key = AEKey.fromNative(stack);
            if (key != null) used.add(key, stack.getStackSize());
        }

        @Override
        public void addEmitable(IAEStack<?> stack) {
            var key = AEKey.fromNative(stack);
            if (key != null) emitted.add(key, stack.getStackSize());
        }

        @Override
        public void addCrafting(ICraftingPatternDetails pattern, long count) {
            patterns
                .merge(NativePatternDetails.capture(pattern, world, Collections.emptyList()), count, Math::addExact);
        }

        static CraftingPlan capture(IGrid grid, ICraftingJob job, BaseActionSource source,
            net.minecraft.world.World world) {
            NativeJobPlan collector = new NativeJobPlan(world);
            appeng.crafting.MECraftingInventory staged = new appeng.crafting.MECraftingInventory(
                (appeng.api.networking.storage.IStorageGrid) grid
                    .getCache(appeng.api.networking.storage.IStorageGrid.class),
                true,
                false,
                false);
            job.startCrafting(staged, collector, source);
            var out = job.getOutput();
            return new CraftingPlan(
                new GenericStack(AEKey.fromNative(out), out.getStackSize()),
                job.getByteTotal(),
                false,
                false,
                collector.used,
                collector.emitted,
                new KeyCounter(),
                collector.patterns);
        }
    }
}
