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

    public ECOCraftingNativeRuntime(ECOCraftingCPU cpu) {
        this.cpu = java.util.Objects.requireNonNull(cpu);
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
        KeyCounter all = new KeyCounter();
        getAllItems(all);
        for (var entry : all) {
            AEKey key = entry.getKey();
            long amount = which == CraftingItemList.STORAGE ? cpu.getLogic()
                .getStored(key)
                : which == CraftingItemList.ACTIVE ? cpu.getLogic()
                    .getWaitingFor(key)
                    : cpu.getLogic()
                        .getPendingOutputs(key);
            if (amount > 0) out.add(key, amount);
        }
    }

    @Override
    public ICraftingLink lastLink() {
        var link = cpu.getLogic()
            .getLastLink();
        return link == null ? null : link.nativeLink();
    }

    @Override
    public ICraftingLink submit(ICraftingPlan plan, BaseActionSource source, ICraftingRequester requester) {
        var grid = grid();
        if (grid == null) return null;
        var result = cpu.getOwner() == null ? cpu.getCluster()
            .submitJob(grid, plan, IActionSource.of(source), requester)
            : cpu.getLogic()
                .trySubmitJob(grid, plan, IActionSource.of(source), requester);
        if (!result.successful()) return null;
        ECOCraftingCPU assigned = cpu.getOwner() == null ? cpu.getCluster()
            .findCPU(plan) : cpu;
        var link = result.link() == null && assigned != null ? assigned.getLogic()
            .getLastLink() : result.link();
        return link == null ? null : link.nativeLink();
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
