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
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingJobV2;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CalculationStrategy;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingServiceView;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.network.ServerThread;

/** Native terminal entry and callback lifecycle for the real source planning service. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class NativeECOPlanning {

    private static final int MAX_CATALOG_RETRIES = 8;
    private static final Set<Pending> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    private NativeECOPlanning() {}

    public static List<ECOComputationSystemBlockEntity> hosts(IGrid grid) {
        List<ECOComputationSystemBlockEntity> hosts = allHosts(grid);
        hosts.removeIf(
            host -> host.getCluster() == null || !host.getCluster()
                .isActive());
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

    /**
     * Every computation host attached to the grid, including unformed or unpowered ones. Grid-wide planner settings
     * are written to all of them so a host that comes back online cannot reintroduce a stale value.
     */
    public static List<ECOComputationSystemBlockEntity> allHosts(IGrid grid) {
        List<ECOComputationSystemBlockEntity> hosts = new ArrayList<>();
        if (grid == null) return hosts;
        for (Class<? extends IGridHost> type : grid.getMachinesClasses()) {
            if (!ECOComputationSystemBlockEntity.class.isAssignableFrom(type)
                && !ECOMachineInterfaceBlockEntity.class.isAssignableFrom(type)) continue;
            for (IGridNode node : grid.getMachines(type)) {
                if (node.getMachine() instanceof ECOComputationSystemBlockEntity host) hosts.add(host);
                else if (node.getMachine() instanceof ECOMachineInterfaceBlockEntity<?>port
                    && port.getCluster() instanceof NEComputationCluster cluster
                    && !cluster.isDestroyed()
                    && cluster.usesCompactGrid()
                    && cluster.getNode() == node) hosts.add(cluster.getController());
            }
        }
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

    /** Whether a confirmation container's planning future was produced by this ECO route. */
    public static boolean isEcoPlanning(Future<?> future) {
        return future instanceof Pending;
    }

    /** Every ingredient keeps the same native-semantics routing check as an individual request. */
    public static boolean supportsOrder(IGrid grid, List<GenericStack> ingredients, ECOPlannerOptions options) {
        for (var ingredient : ingredients) {
            var output = ingredient.what()
                .toNative(ingredient.amount());
            if (NativePlannerRouting.routeToNative(grid, output, options.ignorePatternSubstitutions())) return false;
        }
        return true;
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
        var options = ECOPlannerOptions.from(host);
        if (NativePlannerRouting.routeToNative(grid, output, options.ignorePatternSubstitutions()))
            return new CraftingJobV2(world, grid, source, output, CraftingMode.STANDARD, callback).schedule();
        Attempt attempt = startEcoAttempt(world, grid, key, output.getStackSize(), options);
        Pending pending = new Pending(
            world,
            grid,
            source,
            action,
            output,
            key,
            attempt,
            callback,
            ServerTasks.captureSession());
        PENDING.add(pending);
        return pending;
    }

    private static Attempt startEcoAttempt(World world, IGrid grid, AEKey key, long amount, ECOPlannerOptions options) {
        var view = GridView.of(grid);
        MECraftingInventory snapshot = new MECraftingInventory();
        for (var entry : view.getStorageService()
            .getInventory()
            .getAvailableStacks())
            snapshot.injectItems(
                entry.getKey()
                    .toNative(entry.getLongValue()),
                Actionable.MODULATE);
        var catalog = view.captureCraftingService(world);
        var future = ECOPlanningService
            .begin(view, catalog, key, amount, CalculationStrategy.REPORT_MISSING_ITEMS, options);
        return new Attempt(future, snapshot, catalog, null, ServerThread.tick());
    }

    public static void tick() {
        for (Pending pending : new ArrayList<>(PENDING)) {
            if (!pending.session.isRunning() || !pending.isWorldLoaded()) {
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
                        pending.snapshot(),
                        String.valueOf(failed.getCause()),
                        pending.world,
                        pending.grid,
                        pending.session,
                        pending.catalog()));
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

    private static final class Attempt {

        final Future<ICraftingPlan> ecoFuture;
        final MECraftingInventory snapshot;
        final CraftingServiceView catalog;
        final Future<ICraftingJob> nativeFallback;
        final long startedTick;

        Attempt(Future<ICraftingPlan> ecoFuture, MECraftingInventory snapshot, CraftingServiceView catalog,
            Future<ICraftingJob> nativeFallback, long startedTick) {
            this.ecoFuture = ecoFuture;
            this.snapshot = snapshot;
            this.catalog = catalog;
            this.nativeFallback = nativeFallback;
            this.startedTick = startedTick;
        }

        boolean isDone() {
            return nativeFallback != null ? nativeFallback.isDone() : ecoFuture.isDone();
        }

        boolean cancel(boolean interrupt) {
            return nativeFallback != null ? nativeFallback.cancel(interrupt) : ecoFuture.cancel(interrupt);
        }

        boolean isCancelled() {
            return nativeFallback != null ? nativeFallback.isCancelled() : ecoFuture.isCancelled();
        }
    }

    private static final class Pending implements Future<ICraftingJob> {

        final World world;
        final IGrid grid;
        final BaseActionSource actionSource;
        final IActionSource action;
        final IAEStack<?> output;
        final AEKey key;
        final ICraftingCallback callback;
        final ServerTasks.Session session;
        private Attempt attempt;
        private int retries;
        private boolean cancelled;
        private ICraftingJob job;

        Pending(World world, IGrid grid, BaseActionSource actionSource, IActionSource action, IAEStack<?> output,
            AEKey key, Attempt attempt, ICraftingCallback callback, ServerTasks.Session session) {
            this.world = world;
            this.grid = grid;
            this.actionSource = actionSource;
            this.action = action;
            this.output = output.copy();
            this.key = key;
            this.attempt = attempt;
            this.callback = callback;
            this.session = session;
        }

        synchronized MECraftingInventory snapshot() {
            return attempt.snapshot;
        }

        synchronized CraftingServiceView catalog() {
            return attempt.catalog;
        }

        boolean isWorldLoaded() {
            return net.minecraftforge.common.DimensionManager.getWorld(world.provider.dimensionId) == world;
        }

        private void retryOnServerThread() {
            retries++;
            try {
                var host = settings(grid, action);
                if (host == null) {
                    if (retries >= MAX_CATALOG_RETRIES) {
                        scheduleNativeFallback();
                    } else {
                        // The computation cluster may be reforming for a tick; wait before falling back to native.
                        attempt = new Attempt(
                            attempt.ecoFuture,
                            attempt.snapshot,
                            attempt.catalog,
                            null,
                            ServerThread.tick());
                    }
                    PENDING.add(this);
                    return;
                }
                if (!host.isFastPlannerEnabled()) {
                    scheduleNativeFallback();
                } else {
                    var options = ECOPlannerOptions.from(host);
                    if (NativePlannerRouting.routeToNative(grid, output, options.ignorePatternSubstitutions())) {
                        scheduleNativeFallback();
                    } else {
                        attempt.cancel(true);
                        attempt = startEcoAttempt(world, grid, key, output.getStackSize(), options);
                    }
                }
                PENDING.add(this);
            } catch (RuntimeException failure) {
                attempt.cancel(true);
                job = new NativeECOCraftingJob(
                    null,
                    output,
                    attempt.snapshot,
                    String.valueOf(failure),
                    world,
                    grid,
                    session,
                    attempt.catalog);
            }
        }

        private void scheduleNativeFallback() {
            attempt.cancel(true);
            Future<ICraftingJob> fallback = new CraftingJobV2(
                world,
                grid,
                actionSource,
                output,
                CraftingMode.STANDARD,
                null).schedule();
            attempt = new Attempt(null, attempt.snapshot, attempt.catalog, fallback, ServerThread.tick());
        }

        private synchronized ICraftingJob wrap(Attempt expected, ICraftingPlan plan) {
            if (job != null) return job;
            if (!session.isRunning() || !expected.catalog.isCatalogCurrent()) {
                job = new NativeECOCraftingJob(
                    null,
                    output,
                    expected.snapshot,
                    "Crafting network or recipes changed during planning; retry the request",
                    world,
                    grid,
                    session,
                    expected.catalog);
            } else if (plan == null) {
                job = new NativeECOCraftingJob(
                    null,
                    output,
                    expected.snapshot,
                    "ECO planning ended without a plan",
                    world,
                    grid,
                    session,
                    expected.catalog);
            } else {
                job = new NativeECOCraftingJob(
                    plan,
                    plan.finalOutput()
                        .what()
                        .toNative(
                            plan.finalOutput()
                                .amount()),
                    expected.snapshot,
                    "",
                    world,
                    grid,
                    session,
                    expected.catalog);
            }
            return job;
        }

        @Override
        public synchronized boolean cancel(boolean interrupt) {
            if (cancelled || job != null) return false;
            boolean logicalDone = attempt.isDone() && (attempt.nativeFallback != null || !isWorldLoaded()
                || !session.isRunning()
                || attempt.catalog.isCatalogCurrent()
                || retries >= MAX_CATALOG_RETRIES);
            boolean attemptCancelled = attempt.cancel(interrupt);
            if (attemptCancelled || !logicalDone) {
                cancelled = true;
                return true;
            }
            return false;
        }

        @Override
        public synchronized boolean isCancelled() {
            return cancelled || attempt.isCancelled();
        }

        @Override
        public synchronized boolean isDone() {
            if (job != null || cancelled) return true;
            if (attempt.nativeFallback != null) return attempt.nativeFallback.isDone();
            if (isWorldLoaded() && session.isRunning()
                && !attempt.catalog.isCatalogCurrent()
                && retries < MAX_CATALOG_RETRIES) {
                if (ServerTasks.isServerThread() && ServerThread.tick() != attempt.startedTick) {
                    retryOnServerThread();
                    if (job != null) return true;
                    if (attempt.nativeFallback != null) return attempt.nativeFallback.isDone();
                    return attempt.catalog.isCatalogCurrent() && attempt.ecoFuture.isDone();
                }
                return false;
            }
            return attempt.ecoFuture.isDone();
        }

        @Override
        public ICraftingJob get() throws InterruptedException, ExecutionException {
            try {
                return resolve(false, 0L, TimeUnit.NANOSECONDS);
            } catch (TimeoutException impossible) {
                throw new IllegalStateException("Untimed get threw TimeoutException", impossible);
            }
        }

        @Override
        public ICraftingJob get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
            return resolve(true, timeout, unit);
        }

        private ICraftingJob resolve(boolean timed, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
            Attempt current;
            synchronized (this) {
                if (job != null) return job;
                current = attempt;
            }
            if (current.nativeFallback != null) {
                ICraftingJob fallbackJob = timed ? current.nativeFallback.get(timeout, unit)
                    : current.nativeFallback.get();
                synchronized (this) {
                    if (attempt == current && job == null) job = fallbackJob;
                    return job != null ? job : fallbackJob;
                }
            }
            ICraftingPlan plan = timed ? current.ecoFuture.get(timeout, unit) : current.ecoFuture.get();
            return wrap(current, plan);
        }
    }
}
