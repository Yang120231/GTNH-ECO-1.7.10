package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.WorldCoord;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** AE2 CPU identity: only the imported ECO runtime owns execution and inventory. */
public final class NativeECOCpu extends CraftingCPUCluster
    implements cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter {

    private final NativeECOCpuRuntime runtime;

    public NativeECOCpu(NativeECOCpuRuntime runtime) {
        super(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        this.runtime = runtime;
        runtime.bindNativeCPU(this);
        runtime.addChangeListener(key -> {
            if (key != null) {
                postChange(key.toNative(1), runtime.actionSource());
                postCraftingStatusChange(key.toNative(1));
            } else {
                KeyCounter items = new KeyCounter();
                runtime.getAllItems(items);
                for (var item : items) {
                    postChange(
                        item.getKey()
                            .toNative(1),
                        runtime.actionSource());
                    postCraftingStatusChange(
                        item.getKey()
                            .toNative(1));
                }
            }
        });
    }

    public NativeECOCpuRuntime runtime() {
        return runtime;
    }

    /** Populate AE2's existing requester display on the CPU that actually owns the job. */
    public void trackRequester(BaseActionSource source) {
        sourcePlayer = source instanceof PlayerSource playerSource ? playerSource.player.getCommandSenderName() : null;
    }

    /** Execute GTNH's registered listeners, including its toast, chat, sound and offline notification queue. */
    public void finishTracking(boolean success, GenericStack output, long elapsed) {
        try {
            if (success && output != null) {
                var nativeOutput = output.what()
                    .toNative(output.amount());
                for (var listener : new java.util.ArrayList<>(craftCompleteListeners)) {
                    try {
                        listener.apply(nativeOutput, output.amount(), elapsed);
                    } catch (RuntimeException failure) {
                        appeng.core.AELog.error(failure, "ECO crafting completion listener failed");
                    }
                }
            } else if (!success) {
                for (var listener : new java.util.ArrayList<>(craftCancelListeners)) {
                    try {
                        listener.run();
                    } catch (RuntimeException failure) {
                        appeng.core.AELog.error(failure, "ECO crafting cancellation listener failed");
                    }
                }
            }
        } finally {
            sourcePlayer = null;
            playersFollowingCurrentCraft.clear();
            craftCompleteListeners = initializeDefaultOnCompleteListener();
            craftCancelListeners.clear();
            craftUpdateListeners.clear();
            markDirty();
        }
    }

    @Override
    public void togglePlayerFollowStatus(String player) {
        super.togglePlayerFollowStatus(player);
        markDirty();
    }

    public void writeTracking(net.minecraft.nbt.NBTTagCompound tag) {
        if (sourcePlayer != null) tag.setString("sourcePlayer", sourcePlayer);
        else tag.removeTag("sourcePlayer");
        var names = new net.minecraft.nbt.NBTTagList();
        for (String name : playersFollowingCurrentCraft) names.appendTag(new net.minecraft.nbt.NBTTagString(name));
        tag.setTag("playerNameList", names);
        var unread = new net.minecraft.nbt.NBTTagList();
        for (var entry : unreadNotifications.entrySet()) {
            var player = new net.minecraft.nbt.NBTTagCompound();
            player.setString("playerName", entry.getKey());
            var notifications = new net.minecraft.nbt.NBTTagList();
            for (var notification : entry.getValue()) {
                var data = new net.minecraft.nbt.NBTTagCompound();
                notification.writeToNBT(data);
                notifications.appendTag(data);
            }
            player.setTag("notifications", notifications);
            unread.appendTag(player);
        }
        tag.setTag("unreadNotifications", unread);
    }

    public void readTracking(net.minecraft.nbt.NBTTagCompound tag) {
        sourcePlayer = tag.hasKey("sourcePlayer", 8) ? tag.getString("sourcePlayer") : null;
        playersFollowingCurrentCraft.clear();
        var names = tag.getTagList("playerNameList", 8);
        for (int i = 0; i < names.tagCount(); i++) playersFollowingCurrentCraft.add(names.getStringTagAt(i));
        unreadNotifications.clear();
        var unread = tag.getTagList("unreadNotifications", 10);
        for (int i = 0; i < unread.tagCount(); i++) {
            var player = unread.getCompoundTagAt(i);
            var notifications = player.getTagList("notifications", 10);
            var decoded = new java.util.ArrayList<CraftNotification>();
            for (int j = 0; j < notifications.tagCount(); j++) {
                var notification = new CraftNotification();
                notification.readFromNBT(notifications.getCompoundTagAt(j));
                decoded.add(notification);
            }
            unreadNotifications.put(player.getString("playerName"), decoded);
        }
    }

    @Override
    public appeng.api.config.CraftingAllow getCraftingAllowMode() {
        return runtime.selectionMode();
    }

    @Override
    public void markDirty() {
        runtime.markDirty();
    }

    @Override
    public void changeCraftingAllowMode(appeng.api.config.CraftingAllow mode) {
        runtime.setSelectionMode(mode);
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
        return runtime.remainingWork();
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
        return runtime.getItemAmount(AEKey.fromNative(stack), which);
    }

    @Override
    public ICraftingLink submitJob(IGrid grid, ICraftingJob job, BaseActionSource source,
        ICraftingRequester requester) {
        if (job != null && !job.isSimulation() && !(job instanceof NativeECOCraftingJob)) {
            ICraftingLink merged = tryMerge(grid, job, source, requester);
            if (merged != null) return merged;
        }
        // A busy identity submits through its cluster like the idle placeholder does: the pool spawns a fresh CPU
        // on a free thread and never hands this running CPU a second plan.
        if (job == null || job.isSimulation()
            || runtime.isBusy() && !runtime.hasFreeThread()
            || !runtime.isActive()
            || job.getByteTotal() > runtime.availableStorage()
            || !job.supportsCPUCluster(this)) return null;
        if (job instanceof NativeECOCraftingJob) {
            var ecoJob = (NativeECOCraftingJob) job;
            return ecoJob.belongsToGrid(grid) ? runtime.submit(ecoJob.sourcePlan(), source, requester) : null;
        }
        try {
            return runtime.submit(NativeJobPlan.capture(grid, job, source, runtime.world()), source, requester);
        } catch (appeng.crafting.CraftBranchFailure changedInventory) {
            return null;
        }
    }

    /**
     * GTNH {@code CraftingCPUCluster#submitJob} preamble: a player's standalone request for the output this busy CPU
     * is crafting merges into it when the extra bytes fit, instead of being rejected as busy.
     */
    private ICraftingLink tryMerge(IGrid grid, ICraftingJob job, BaseActionSource source,
        ICraftingRequester requester) {
        if (requester != null || !runtime.isBusy() || !runtime.isActive() || !isCraftingLinkStandalone()) return null;
        IAEStack<?> running = getFinalMultiOutput();
        if (running == null || job.getOutput() == null
            || !job.getOutput()
                .isSameType((Object) running)
            || runtime.availableStorage() < runtime.usedStorage() + job.getByteTotal()) return null;
        try {
            return runtime.merge(NativeJobPlan.capture(grid, job, source, runtime.world()), source);
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
            collector.isMissingMode = job.getCraftingMode() == appeng.api.config.CraftingMode.IGNORE_MISSING;
            appeng.crafting.MECraftingInventory staged = new appeng.crafting.MECraftingInventory(
                (appeng.api.networking.storage.IStorageGrid) grid
                    .getCache(appeng.api.networking.storage.IStorageGrid.class),
                true,
                false,
                false);
            staged.setMissingMode(collector.isMissingMode);
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
                collector.patterns).markVanillaExecution();
        }
    }
}
