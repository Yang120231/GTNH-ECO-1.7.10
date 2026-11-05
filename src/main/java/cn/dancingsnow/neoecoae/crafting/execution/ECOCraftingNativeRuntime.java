package cn.dancingsnow.neoecoae.crafting.execution;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpuRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Platform publication consumes the real source CPU ledgers and never advances native AE2 execution. */
public final class ECOCraftingNativeRuntime implements NativeECOCpuRuntime {

    private final ECOCraftingCPU cpu;
    private World lastWorld;
    private long lastTick = Long.MIN_VALUE;
    private CraftingCPUCluster submittedCpu;

    public ECOCraftingNativeRuntime(ECOCraftingCPU cpu) {
        this.cpu = java.util.Objects.requireNonNull(cpu);
    }

    /** The source CPU, for read-only presentation such as the crafting report's tier glyph. */
    public ECOCraftingCPU cpu() {
        return cpu;
    }

    @Override
    public World world() {
        return cpu.getLevel();
    }

    @Override
    public GridView grid() {
        return cpu.getGrid();
    }

    @Override
    public BaseActionSource actionSource() {
        return cpu.getActionSource()
            .nativeSource();
    }

    @Override
    public String name() {
        return cpu.getName() == null ? ""
            : cpu.getName()
                .getString();
    }

    @Override
    public boolean isActive() {
        return cpu.isActive();
    }

    @Override
    public boolean isBusy() {
        return cpu.isBusy();
    }

    @Override
    public appeng.api.config.CraftingAllow selectionMode() {
        switch (cpu.getSelectionMode()) {
            case PLAYER_ONLY:
                return appeng.api.config.CraftingAllow.ONLY_PLAYER;
            case MACHINE_ONLY:
                return appeng.api.config.CraftingAllow.ONLY_NONPLAYER;
            default:
                return appeng.api.config.CraftingAllow.ALLOW_ALL;
        }
    }

    @Override
    public void setSelectionMode(appeng.api.config.CraftingAllow mode) {
        cpu.getCluster()
            .setSelectionMode(
                mode == appeng.api.config.CraftingAllow.ONLY_PLAYER
                    ? cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode.PLAYER_ONLY
                    : mode == appeng.api.config.CraftingAllow.ONLY_NONPLAYER
                        ? cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode.MACHINE_ONLY
                        : cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode.ANY);
    }

    @Override
    public void markDirty() {
        cpu.markDirty();
    }

    @Override
    public void addChangeListener(java.util.function.Consumer<AEKey> listener) {
        cpu.getLogic()
            .addListener(listener);
    }

    @Override
    public boolean isSuspended() {
        return cpu.getLogic()
            .isJobSuspended();
    }

    @Override
    public void setSuspended(boolean suspended) {
        cpu.getLogic()
            .setJobSuspended(suspended);
    }

    @Override
    public long availableStorage() {
        return cpu.getAvailableStorage();
    }

    @Override
    public long usedStorage() {
        return cpu.isBusy() && cpu.getPlan() != null ? cpu.getPlan()
            .bytes() : 0;
    }

    @Override
    public int coProcessors() {
        return cpu.getCoProcessors();
    }

    @Override
    public GenericStack finalOutput() {
        return cpu.getLogic()
            .getFinalJobOutput();
    }

    @Override
    public long remainingOutput() {
        return cpu.getLogic()
            .getRemainingJobOutputAmount();
    }

    @Override
    public long startedWork() {
        return cpu.getLogic()
            .getElapsedTimeTracker()
            .getSyntheticStartItemCount();
    }

    @Override
    public long remainingWork() {
        return cpu.getLogic()
            .getElapsedTimeTracker()
            .getSyntheticRemainingItemCount();
    }

    @Override
    public long elapsedTimeNanos() {
        return cpu.getLogic()
            .getElapsedTimeTracker()
            .getElapsedTime();
    }

    @Override
    public ECOPlannerOptions planningOptions() {
        return cpu.getCluster()
            .getPlanningOptions();
    }

    @Override
    public void tick() {
        World world = world();
        GridView grid = grid();
        if (world == null || grid == null) return;
        long tick = world.getTotalWorldTime();
        if (lastWorld == world && lastTick == tick) return;
        lastWorld = world;
        lastTick = tick;
        cpu.getLogic()
            .tickCraftingLogic(grid.getEnergyService(), grid.getCraftingService());
    }

    @Override
    public void cancel() {
        cpu.cancelJob();
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode) {
        return cpu.getLogic()
            .insert(key, amount, mode);
    }

    @Override
    public long waitingFor(AEKey key) {
        return cpu.getLogic()
            .getWaitingFor(key);
    }

    @Override
    public void getAllItems(KeyCounter out) {
        cpu.getLogic()
            .getAllItems(out);
    }

    @Override
    public void getItems(KeyCounter out, CraftingItemList which) {
        if (which == CraftingItemList.ALL) {
            getAllItems(out);
            return;
        }
        var logic = cpu.getLogic();
        if (which == CraftingItemList.PENDING) {
            logic.collectPendingOutputs(out);
            return;
        }
        if (which == CraftingItemList.STORAGE) {
            addPositive(out, logic.getInventory().list);
            return;
        }
        var job = logic.getJob();
        if (job != null) addPositive(out, job.waitingFor.list);
    }

    @Override
    public long getItemAmount(AEKey key, CraftingItemList which) {
        if (key == null) return 0L;
        var logic = cpu.getLogic();
        long amount = which == CraftingItemList.ALL ? logic.getAllAmount(key)
            : which == CraftingItemList.STORAGE ? logic.getStored(key)
                : which == CraftingItemList.ACTIVE ? logic.getWaitingFor(key) : logic.getPendingOutputs(key);
        return Math.max(0L, amount);
    }

    private static void addPositive(KeyCounter out, KeyCounter source) {
        for (var entry : source) {
            if (entry.getLongValue() > 0) out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public ICraftingLink lastLink() {
        var link = cpu.getLogic()
            .getLastLink();
        return link == null ? null : link.nativeLink();
    }

    /**
     * Every published identity (placeholder, running or finished CPU) submits through its cluster, so network
     * pooling, thread spawning and byte reservation always apply. A CPU whose job ended is never handed a
     * new plan; the cluster spawns a fresh CPU on a free thread instead.
     */
    @Override
    public ICraftingLink submit(ICraftingPlan plan, BaseActionSource source, ICraftingRequester requester) {
        var grid = grid();
        if (grid == null) return null;
        var result = cpu.getCluster()
            .submitJob(grid, plan, IActionSource.of(source), requester);
        if (!result.successful()) return null;
        ECOCraftingCPU assigned = cpu.getCluster()
            .findCPU(plan);
        submittedCpu = assigned != null && assigned.nativeCPU() instanceof CraftingCPUCluster bridge ? bridge : null;
        var link = result.link() == null && assigned != null ? assigned.getLogic()
            .getLastLink() : result.link();
        return link == null ? null : link.nativeLink();
    }

    @Override
    public CraftingCPUCluster submittedCpu() {
        return submittedCpu;
    }

    @Override
    public ICraftingLink merge(cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan plan,
        BaseActionSource source) {
        var link = cpu.getLogic()
            .tryMergeVanillaJob(grid(), plan, IActionSource.of(source));
        if (link != null && cpu.nativeCPU() instanceof CraftingCPUCluster bridge) submittedCpu = bridge;
        return link == null ? null : link.nativeLink();
    }

    @Override
    public Object submissionPool() {
        return cpu.getCluster()
            .submissionPool();
    }

    @Override
    public boolean hasFreeThread() {
        return cpu.getCluster()
            .hasFreeThread();
    }

    @Override
    public boolean isPreferredFor(BaseActionSource source) {
        return cpu.getCluster()
            .isPreferredFor(IActionSource.of(source));
    }

    @Override
    public Object lifecycleOwner() {
        // One release per switch group: every member shares the group's published placeholder.
        return cpu.getCluster()
            .submissionPool();
    }

    @Override
    public boolean releaseFinishedCpus() {
        return cpu.getCluster()
            .pruneSubmissionPool();
    }

    @Override
    public void bindNativeCPU(CraftingCPUCluster bridge) {
        cpu.bindNativeCPU(bridge);
    }

    @Override
    public boolean ownsJob(java.util.UUID job) {
        return cpu.getLogic()
            .hasCraftingJob(job);
    }

    @Override
    public long neoecoae$insertIntoCpuForJob(java.util.UUID job, AEKey key, long amount, Actionable mode) {
        return cpu.getLogic()
            .insertForJob(job, key, amount, mode);
    }

    @Override
    public java.math.BigInteger neoecoae$insertExactIntoCpuForJob(java.util.UUID job, AEKey key,
        java.math.BigInteger amount, Actionable mode) {
        return cpu.getLogic()
            .insertExactForJob(job, key, amount, mode);
    }
}
