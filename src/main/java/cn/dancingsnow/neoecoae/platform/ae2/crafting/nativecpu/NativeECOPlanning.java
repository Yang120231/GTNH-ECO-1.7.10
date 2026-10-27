package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.MECraftingInventory;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CalculationStrategy;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Native terminal entry and callback lifecycle for the real source planning service. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class NativeECOPlanning {

    private static final Set<Pending> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    private NativeECOPlanning() {}

    public static List<ECOComputationSystemBlockEntity> hosts(IGrid grid) {
        List<ECOComputationSystemBlockEntity> hosts = new ArrayList<>();
        for (Class<? extends IGridHost> type : grid.getMachinesClasses())
            if (ECOComputationSystemBlockEntity.class.isAssignableFrom(type))
                for (IGridNode node : grid.getMachines(type)) {
                    var host = (ECOComputationSystemBlockEntity) node.getMachine();
                    if (host.getCluster() != null && !host.getCluster()
                        .isDestroyed() && node.isActive()) hosts.add(host);
                }
        hosts.sort(
            Comparator.comparingInt(
                (ECOComputationSystemBlockEntity host) -> host.getBlockPos()
                    .getX())
                .thenComparingInt(
                    host -> host.getBlockPos()
                        .getY())
                .thenComparingInt(
                    host -> host.getBlockPos()
                        .getZ()));
        return hosts;
    }

    public static ECOComputationSystemBlockEntity settings(IGrid grid, IActionSource source) {
        List<ECOComputationSystemBlockEntity> hosts = hosts(grid);
        hosts.removeIf(
            host -> source != null && !host.getCluster()
                .canBeAutoSelectedFor(source));
        if (source != null) hosts.sort(
            Comparator.comparing(
                (ECOComputationSystemBlockEntity host) -> !host.getCluster()
                    .isPreferredFor(source)));
        return hosts.isEmpty() ? null : hosts.get(0);
    }

    public static Future<ICraftingJob> begin(World world, IGrid grid, BaseActionSource source, IAEStack<?> output,
        ICraftingCallback callback) {
        if (world == null || world.isRemote
            || grid == null
            || source == null
            || output == null
            || output.getStackSize() <= 0) return null;
        var key = AEKey.fromNative(output);
        if (key == null) return null;
        var action = IActionSource.of(source);
        var host = settings(grid, action);
        if (host == null || !host.isFastPlannerEnabled()) return null;
        ServerTasks.assertServerThread();
        var view = GridView.of(grid);
        MECraftingInventory snapshot = new MECraftingInventory();
        for (var entry : view.getStorageService()
            .getInventory()
            .getAvailableStacks())
            snapshot.injectItems(
                entry.getKey()
                    .toNative(entry.getLongValue()),
                Actionable.MODULATE);
        var future = ECOPlanningService.begin(
            world,
            view,
            action,
            key,
            output.getStackSize(),
            CalculationStrategy.REPORT_MISSING_ITEMS,
            cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions.from(host));
        Pending pending = new Pending(world, grid, output, future, snapshot, callback, ServerTasks.captureSession());
        PENDING.add(pending);
        return pending;
    }

    public static void tick() {
        for (Pending pending : new ArrayList<>(PENDING)) {
            if (!pending.session.isRunning()
                || net.minecraftforge.common.DimensionManager.getWorld(pending.world.provider.dimensionId)
                    != pending.world) {
                pending.cancel(true);
                PENDING.remove(pending);
                continue;
            }
            if (!pending.isDone()) continue;
            PENDING.remove(pending);
            if (pending.isCancelled() || pending.callback == null) continue;
            try {
                pending.callback.calculationComplete(pending.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
            } catch (ExecutionException failed) {
                pending.callback.calculationComplete(
                    new NativeECOCraftingJob(
                        null,
                        pending.output,
                        pending.snapshot,
                        String.valueOf(failed.getCause()),
                        pending.world,
                        pending.grid,
                        pending.session));
            }
        }
    }

    public static void unload(World world) {
        for (Pending pending : new ArrayList<>(PENDING)) if (pending.world == world) {
            pending.cancel(true);
            PENDING.remove(pending);
        }
    }

    public static void clear() {
        for (Pending pending : new ArrayList<>(PENDING)) pending.cancel(true);
        PENDING.clear();
    }

    private static final class Pending implements Future<ICraftingJob> {

        final World world;
        final IGrid grid;
        final IAEStack<?> output;
        final Future<ICraftingPlan> source;
        final MECraftingInventory snapshot;
        final ICraftingCallback callback;
        final ServerTasks.Session session;
        private NativeECOCraftingJob job;

        Pending(World world, IGrid grid, IAEStack<?> output, Future<ICraftingPlan> source, MECraftingInventory snapshot,
            ICraftingCallback callback, ServerTasks.Session session) {
            this.world = world;
            this.grid = grid;
            this.output = output.copy();
            this.source = source;
            this.snapshot = snapshot;
            this.callback = callback;
            this.session = session;
        }

        private synchronized NativeECOCraftingJob wrap(ICraftingPlan plan) {
            if (job == null) job = new NativeECOCraftingJob(
                plan,
                plan.finalOutput()
                    .what()
                    .toNative(
                        plan.finalOutput()
                            .amount()),
                snapshot,
                "",
                world,
                grid,
                session);
            return job;
        }

        public boolean cancel(boolean interrupt) {
            return source.cancel(interrupt);
        }

        public boolean isCancelled() {
            return source.isCancelled();
        }

        public boolean isDone() {
            return source.isDone();
        }

        public ICraftingJob get() throws InterruptedException, ExecutionException {
            return wrap(source.get());
        }

        public ICraftingJob get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
            return wrap(source.get(timeout, unit));
        }
    }
}
