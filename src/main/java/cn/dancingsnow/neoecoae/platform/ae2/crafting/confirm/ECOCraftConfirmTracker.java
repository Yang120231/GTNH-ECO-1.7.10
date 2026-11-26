package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.CraftingMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.command.CraftingPlanExport;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.mixin.ContainerCraftConfirmAccessor;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;

/**
 * Server half of the ECO crafting report (1.21's confirmation menu mixin). Each tick it observes every open AE2
 * confirmation container; one whose request ECO planned is mirrored to its client, which then shows the ECO report
 * instead of AE2's page. Containers ECO never planned are left completely native.
 */
public final class ECOCraftConfirmTracker {

    private static final Logger LOG = LogManager.getLogger(ECOCraftConfirmTracker.class);
    private static final Map<EntityPlayerMP, Tracked> TRACKED = new WeakHashMap<>();
    /** rv3-beta-1080 pins the crafted item after a start; the pinned 990 jar has no such step. */
    private static volatile Method pushToPins;
    private static volatile boolean pushToPinsResolved;

    private ECOCraftConfirmTracker() {}

    /** Only AE2's original quick-start gesture authorizes submitting a replacement without another click. */
    private enum Replan {

        REQUESTED,
        REVIEW,
        AUTO_START,
        AUTO_FOLLOW;

        boolean automatic() {
            return this == AUTO_START || this == AUTO_FOLLOW;
        }
    }

    private static final class Tracked {

        final ContainerCraftConfirm container;
        final ECOCraftConfirmSession session = new ECOCraftConfirmSession();
        /** Planner options of the ECO request, kept for a big order's later segment planning. */
        @Nullable
        ECOPlannerOptions options;
        /** The last result an armed plan export looked at, so each calculated plan is offered once. */
        @Nullable
        Object exportObserved;
        /** The last result whose CPU selection was checked for ECO compatibility, so that check runs once per plan. */
        @Nullable
        Object cpuCheckedResult;

        Tracked(ContainerCraftConfirm container) {
            this.container = container;
        }
    }

    public static void tick() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        List<?> players = server.getConfigurationManager().playerEntityList;
        for (Iterator<Map.Entry<EntityPlayerMP, Tracked>> it = TRACKED.entrySet()
            .iterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getKey().openContainer != entry.getValue().container || !players.contains(entry.getKey())) {
                // A closed confirmation must not keep its background plan search alive.
                entry.getValue().session.closeImprovements();
                it.remove();
            }
        }
        for (Object value : players) {
            if (!(value instanceof EntityPlayerMP player)) continue;
            Container open = player.openContainer;
            if (!(open instanceof ContainerCraftConfirm container)) continue;
            Tracked tracked = track(player, container);
            try {
                update(player, tracked);
            } catch (RuntimeException | LinkageError failure) {
                // The report is presentation only; never let it break the confirmation container.
            }
        }
    }

    private static Tracked track(EntityPlayerMP player, ContainerCraftConfirm container) {
        Tracked tracked = TRACKED.get(player);
        if (tracked == null || tracked.container != container) {
            if (tracked != null) tracked.session.closeImprovements();
            tracked = new Tracked(container);
            TRACKED.put(player, tracked);
        }
        return tracked;
    }

    public static void clear() {
        for (Tracked tracked : TRACKED.values()) tracked.session.closeImprovements();
        TRACKED.clear();
    }

    private static boolean begin(Tracked tracked, boolean ecoPlanned) {
        if (tracked.session.isEco()) return true;
        var host = planningHost(tracked.container);
        // The ECO page also shows native plans when the fast planner routed a request back to AE2.
        if (!ecoPlanned && (host == null || !host.isFastPlannerEnabled())) return false;
        tracked.options = host == null ? null : ECOPlannerOptions.from(host);
        tracked.session.begin(host != null && host.isCyclePlanningEnabled());
        return true;
    }

    private static void update(EntityPlayerMP player, Tracked tracked) {
        ContainerCraftConfirm container = tracked.container;
        ECOCraftConfirmSession session = tracked.session;
        var access = (ContainerCraftConfirmAccessor) container;
        Future<ICraftingJob> job = access.neoecoae$getJob();
        ICraftingJob result = access.neoecoae$getResult();
        boolean eco = begin(tracked, NativeECOPlanning.isEcoPlanning(job) || result instanceof NativeECOCraftingJob);
        exportPlanIfArmed(player, tracked, result);
        if (!eco) return;
        // AE2 only clears these flags after a submission, not when a calculation returns a simulation.
        if (result != null && result.isSimulation()) stopAutoStart(container);
        observe(container, session, result);
        if (result == null && job == null)
            session.planningFailed(() -> "ECO planning ended without a plan; see the server log");
        if (result != null && result != tracked.cpuCheckedResult) {
            // AE2 selects a CPU by default; one that cannot take this plan would reject every start, so drop it once.
            tracked.cpuCheckedResult = result;
            if (nativeIncompatible(selectedCpu(container), result)) container.cpuTable.selectCPU(-1);
        }
        session.setCpuTier(selectedCpuTier(container));
        session.setBigOrderCpu(
            ECOBigOrderSubmission.resolveCpu(container.getGrid(), selectedCpu(container), actionSource(container))
                != null);
        session.setMergeOnStart(willMerge(container, result));
        push(player, container, session);
    }

    private static void observe(ContainerCraftConfirm container, ECOCraftConfirmSession session,
        @Nullable ICraftingJob result) {
        session.observeResult(result, version -> {
            if (result instanceof NativeECOCraftingJob ecoJob) {
                session.display(ECOCraftConfirmReports.resolve(ecoJob.sourcePlan()));
                return build(container, ecoJob, session, version);
            }
            return NativeCraftConfirmReports
                .build(result, actionSource(container), session.cyclePlanningEnabled(), version);
        });
    }

    /**
     * Whether a CPU can take this job without ECO and AE2 disagreeing: an ECO-only plan never fits a plain AE2 CPU,
     * and a native ECO job the CPU refuses on its own terms.
     */
    public static boolean nativeIncompatible(@Nullable ICraftingCPU cpu, @Nullable ICraftingJob job) {
        return cpu instanceof CraftingCPUCluster && !(cpu instanceof NativeECOCpu)
            && job instanceof NativeECOCraftingJob eco
            && !eco.supportsNativeCpus();
    }

    /**
     * Whether the report version and result still match the server's current session: returns true when both match,
     * and true for an untracked session, which needs no check.
     */
    public static boolean isCurrentReport(EntityPlayerMP player, int windowId, long version, Object result) {
        Tracked tracked = lookup(player, windowId);
        if (tracked == null) return true;
        var state = tracked.session.state();
        return state.reportReady && state.reportVersion == version && tracked.session.observedResult() == result;
    }

    /**
     * Whether starting this plan joins the selected AE2 CPU's running standalone job instead of queueing a new one,
     * with the same preconditions AE2's CraftingCPUCluster.submitJob applies before a merge.
     */
    private static boolean willMerge(ContainerCraftConfirm container, @Nullable ICraftingJob result) {
        if (result == null || result.isSimulation()) return false;
        ICraftingCPU selected = selectedCpu(container);
        // An ECO CPU never merges an ECO plan: it either starts a new thread or rejects the start.
        if (!(selected instanceof CraftingCPUCluster c) || c instanceof NativeECOCpu) return false;
        if (result instanceof NativeECOCraftingJob eco && !eco.supportsNativeCpus()) return false;
        return c.isActive() && c.isBusy()
            && c.isCraftingLinkStandalone()
            && c.getFinalMultiOutput() != null
            && result.getOutput() != null
            && result.getOutput()
                .isSameType((Object) c.getFinalMultiOutput())
            && c.getAvailableStorage() >= c.getUsedStorage() + result.getByteTotal();
    }

    /**
     * 1.21's hook on the confirmation's plan summary: when this container receives a calculated plan and its player
     * armed {@code /neoecoae export_plan}, the plan is exported once. Ordinary confirmations only compare the result
     * identity; diagnostics are resolved for an armed export alone, so their behavior is unchanged.
     */
    private static void exportPlanIfArmed(EntityPlayerMP player, Tracked tracked, @Nullable ICraftingJob result) {
        if (result == tracked.exportObserved) return;
        tracked.exportObserved = result;
        if (result == null || !CraftingPlanExport.isArmed(player)) return;
        ContainerCraftConfirm container = tracked.container;
        IGrid grid = container.getGrid();
        if (grid == null) return;
        GridView view = GridView.of(grid);
        if (result instanceof NativeECOCraftingJob ecoJob) {
            // A failed ECO calculation has no plan; the export stays armed for the next one.
            ICraftingPlan plan = ecoJob.sourcePlan();
            if (plan != null) CraftingPlanExport
                .onPlanReady(player, view, plan, ECOCraftConfirmReports.resolve(plan), tracked.options);
            return;
        }
        NativePlanExport exported;
        try {
            exported = NativePlanExport
                .of(result, actionSource(container), view.getCraftingService(), container.getWorld());
        } catch (RuntimeException | LinkageError failure) {
            CraftingPlanExport.onPlanUnreadable(player, failure);
            return;
        }
        if (exported != null) CraftingPlanExport.onNativePlanReady(player, view, exported.plan, exported.tasksKnown);
    }

    private static ECOCraftConfirmReports.Built build(ContainerCraftConfirm container, NativeECOCraftingJob job,
        ECOCraftConfirmSession session, long version) {
        if (job.sourcePlan() == null) {
            String message = job.getErrorMessage();
            return ECOCraftConfirmReports.failed(
                message == null || message.isEmpty() ? "ECO planning failed" : message,
                session.cyclePlanningEnabled(),
                version);
        }
        IGrid grid = container.getGrid();
        return ECOCraftConfirmReports.build(
            job.sourcePlan(),
            grid == null ? null : GridView.of(grid),
            IActionSource.of(actionSource(container)),
            session.cyclePlanningEnabled(),
            version);
    }

    private static void push(EntityPlayerMP player, ContainerCraftConfirm container, ECOCraftConfirmSession session) {
        ECOCraftConfirmSession.Outgoing outgoing = session.drain();
        if (outgoing != null) ECOCraftConfirmPackets.sync(player, container.windowId, outgoing.state, outgoing.report);
    }

    /** Handle AE2's unversioned start for standard ECO plans. Manual starts must come from the versioned report. */
    public static boolean handleNativeStart(ContainerCraftConfirm container, boolean follow) {
        var access = (ContainerCraftConfirmAccessor) container;
        if (!(access.neoecoae$getResult() instanceof NativeECOCraftingJob job)
            || job.getCraftingMode() != CraftingMode.STANDARD
            || !(container.getPlayerInv().player instanceof EntityPlayerMP player)) return false;
        // A late click from AE2's old screen has no report version. Only the server-armed gesture can bypass review.
        var completed = access.neoecoae$getJob();
        if (player.openContainer != container || job.isSimulation()
            || !(container.isAutoStart() || container.isAutoStartAndFollow())
            || follow != container.isAutoStartAndFollow()
            || completed == null
            || !completed.isDone()) return true;
        stopAutoStart(container);
        Tracked tracked = track(player, container);
        try {
            begin(tracked, true);
            exportPlanIfArmed(player, tracked, job);
            tracked.session.display(ECOCraftConfirmReports.resolve(job.sourcePlan()));
            // No report was confirmed: don't build or send the material table just to close it on success.
            submit(player, tracked, job, false, follow, follow ? Replan.AUTO_FOLLOW : Replan.AUTO_START);
        } catch (RuntimeException | LinkageError failure) {
            reject(player, tracked, ECOCraftConfirmError.SUBMIT_FAILED, String.valueOf(failure));
        }
        // Without a replacement AE2 will consume the same completed future next tick with auto-start off. This
        // publishes its bytes/CPU list for the error page without submitting again.
        return true;
    }

    /** Start request from the ECO report; only the exact result/version the player saw can be submitted. */
    public static void start(EntityPlayerMP player, int windowId, boolean forced, boolean follow, long reportVersion) {
        Tracked tracked = lookup(player, windowId);
        if (tracked == null) return;
        ContainerCraftConfirm container = tracked.container;
        ECOCraftConfirmSession session = tracked.session;
        ICraftingJob result = ((ContainerCraftConfirmAccessor) container).neoecoae$getResult();
        var state = session.state();
        if (!state.reportReady) return; // Late click while a replacement is calculating.
        if (reportVersion != state.reportVersion || session.observedResult() != result) {
            // The client confirmed a report the server has since replaced; it must review the new plan first.
            reject(player, tracked, ECOCraftConfirmError.PLAN_CHANGED, "");
            return;
        }
        if (result == null || result instanceof NativeECOCraftingJob eco && eco.sourcePlan() == null) {
            reject(player, tracked, ECOCraftConfirmError.NO_PLAN, "");
            return;
        }
        ECOCraftConfirmError rejected = ECOCraftConfirmRules.rejectStart(state, forced);
        if (rejected != null) {
            reject(player, tracked, rejected, "");
            return;
        }
        submit(player, tracked, result, forced, follow, Replan.REVIEW);
    }

    private static void submit(EntityPlayerMP player, Tracked tracked, ICraftingJob result, boolean forced,
        boolean follow, Replan retry) {
        ContainerCraftConfirm container = tracked.container;
        ECOCraftConfirmSession session = tracked.session;
        NativeECOCraftingJob job = result instanceof NativeECOCraftingJob eco ? eco : null;
        IGrid grid = container.getGrid();
        if (grid == null) {
            reject(player, tracked, ECOCraftConfirmError.NO_CPU_ACCEPTED, "");
            return;
        }
        if (!canCraft(player, container, grid)) {
            reject(player, tracked, ECOCraftConfirmError.SUBMIT_FAILED, "");
            return;
        }
        if (job != null && !job.belongsToGrid(grid)) {
            reject(player, tracked, ECOCraftConfirmError.PLAN_STALE, "");
            return;
        }
        ICraftingJob submitted = result;
        boolean checkStock = job != null && !forced && job.getCraftingMode() == CraftingMode.STANDARD;
        session.closeImprovements();
        stopAutoStart(container);
        try {
            BaseActionSource source = actionSource(container);
            if (job != null && (checkStock || forced)) {
                KeyCounter shortfall = CraftingCpuHelper
                    .initialShortfall(job.sourcePlan(), GridView.of(grid), IActionSource.of(source));
                if (!shortfall.isEmpty()) {
                    if (!forced) {
                        stockChanged(player, tracked, retry, "", () -> shortages(shortfall));
                        return;
                    }
                    if (session.state().cycleDetected) {
                        reject(player, tracked, ECOCraftConfirmError.FORCE_REJECTED, "", shortages(shortfall));
                        return;
                    }
                }
                if (forced) submitted = job.forced(shortfall);
            } else if (job == null && forced && result.isSimulation()) {
                if (!NativeForcedCraftingJob.supports(result)) {
                    reject(player, tracked, ECOCraftConfirmError.FORCE_REJECTED, "");
                    return;
                }
                submitted = new NativeForcedCraftingJob(result);
            }
            ConfirmSubmitSource confirmation = checkStock && source instanceof PlayerSource actor
                ? new ConfirmSubmitSource(actor, job.sourcePlan())
                : null;
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            ICraftingCPU target = selectedCpu(container);
            if (nativeIncompatible(target, submitted)) {
                target = null;
            }
            ICraftingLink link = crafting
                .submitJob(submitted, null, target, true, confirmation == null ? source : confirmation, follow);
            var receipt = confirmation == null ? null : confirmation.receipt;
            if (link == null && (receipt == null || !receipt.successful())) {
                if (receipt != null && receipt.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT) {
                    stockChanged(
                        player,
                        tracked,
                        retry,
                        "",
                        () -> receipt.missingIngredient() == null ? Collections.emptyList()
                            : Collections.singletonList(receipt.missingIngredient()));
                    return;
                }
                // A null link with no receipt is ambiguous. Show any shortage we can see now, but never replay
                // a submission based on a stock reading taken after it might already have consumed those items.
                if (receipt == null && submitted instanceof NativeECOCraftingJob eco) {
                    var missing = CraftingCpuHelper
                        .initialShortfall(eco.sourcePlan(), GridView.of(grid), IActionSource.of(source));
                    if (!missing.isEmpty()) {
                        reject(player, tracked, ECOCraftConfirmError.MISSING_INGREDIENT, "", shortages(missing));
                        return;
                    }
                }
                reject(player, tracked, ECOCraftConfirmError.NO_CPU_ACCEPTED, "");
                return;
            }
        } catch (RuntimeException | LinkageError failure) {
            reject(player, tracked, ECOCraftConfirmError.SUBMIT_FAILED, String.valueOf(failure));
            return;
        }
        pushCraftedItemToPins(container);
        container.switchToOriginalGUI();
    }

    /**
     * Big-order request from the ECO report (1.21's ECOBigOrderStartC2SPacket): the server resolves the displayed
     * result, the ECO CPU and the admission itself; the client only states its intent.
     */
    public static void startBigOrder(EntityPlayerMP player, int windowId, boolean forced, long reportVersion) {
        Tracked tracked = lookup(player, windowId);
        if (tracked == null) {
            return;
        }
        ContainerCraftConfirm container = tracked.container;
        ECOCraftConfirmSession session = tracked.session;
        ICraftingJob result = ((ContainerCraftConfirmAccessor) container).neoecoae$getResult();
        var state = session.state();
        if (!state.reportReady) return;
        if (reportVersion != state.reportVersion || session.observedResult() != result) {
            rejectBigOrder(player, tracked, ECOCraftConfirmError.PLAN_CHANGED, "REPORT_VERSION_CHANGED", "");
            return;
        }
        if (!(result instanceof NativeECOCraftingJob job) || job.sourcePlan() == null) {
            rejectBigOrder(player, tracked, ECOCraftConfirmError.NO_PLAN, "NO_PLAN", "");
            return;
        }
        ECOCraftConfirmError gate = ECOCraftConfirmRules.rejectBigOrderStart(state, forced);
        if (gate != null) {
            rejectBigOrder(player, tracked, gate, "NOT_A_BIG_ORDER", "");
            return;
        }
        IGrid grid = container.getGrid();
        if (grid != null && !job.belongsToGrid(grid)) {
            reject(player, tracked, ECOCraftConfirmError.PLAN_STALE, "");
            return;
        }
        if (grid != null && !canCraft(player, container, grid)) {
            reject(player, tracked, ECOCraftConfirmError.SUBMIT_FAILED, "");
            return;
        }
        ECOPlanningResult exact = ECOCraftConfirmReports.resolve(job.sourcePlan());
        if (exact == null || exact != session.displayed()) {
            rejectBigOrder(
                player,
                tracked,
                ECOCraftConfirmError.INCOMPLETE_PLAN,
                exact == null ? "NO_EXACT_RESULT" : "CONFIRMED_RESULT_CHANGED",
                "");
            return;
        }
        session.closeImprovements();
        ECOBigOrderSubmission.Rejection rejection;
        try {
            rejection = ECOBigOrderSubmission
                .submit(exact, tracked.options, forced, grid, selectedCpu(container), actionSource(container));
        } catch (RuntimeException | LinkageError failure) {
            rejection = new ECOBigOrderSubmission.Rejection(
                ECOCraftConfirmError.SUBMIT_FAILED,
                "EXCEPTION",
                String.valueOf(failure));
        }
        stopAutoStart(container);
        if (rejection != null) {
            if (!forced && job.getCraftingMode() == CraftingMode.STANDARD
                && rejection.error == ECOCraftConfirmError.MISSING_INGREDIENT) {
                stockChanged(player, tracked, Replan.REVIEW, rejection.detail, Collections::emptyList);
                return;
            }
            rejectBigOrder(player, tracked, rejection.error, rejection.reason, rejection.detail);
            return;
        }
        container.switchToOriginalGUI();
    }

    private static void rejectBigOrder(EntityPlayerMP player, Tracked tracked, ECOCraftConfirmError error,
        String reason, String detail) {
        reject(player, tracked, error, detail);
    }

    /**
     * AE2's own auto-start calls {@code ContainerCraftConfirm.startJob} without passing through {@link #start}.
     * Report its submission failure on the open confirmation instead of letting it escape the container tick.
     */
    public static void autoStartFailed(ContainerCraftConfirm container, Throwable failure) {
        stopAutoStart(container);
        if (!(container.getPlayerInv().player instanceof EntityPlayerMP player)) return;
        Tracked tracked = TRACKED.get(player);
        if (tracked == null || tracked.container != container) return;
        reject(player, tracked, ECOCraftConfirmError.SUBMIT_FAILED, String.valueOf(failure));
    }

    public static void clearError(EntityPlayerMP player, int windowId) {
        Tracked tracked = lookup(player, windowId);
        if (tracked == null) return;
        tracked.session.clearError();
        push(player, tracked.container, tracked.session);
    }

    /** Return to the terminal if it is still accessible. A late cancel must not touch a replacement window. */
    public static void cancel(EntityPlayerMP player, int windowId) {
        if (!(player.openContainer instanceof ContainerCraftConfirm confirm) || confirm.windowId != windowId) return;
        try {
            if (confirm.getOpenContext() != null && confirm.getPrimaryGui() != null && confirm.canInteractWith(player))
                confirm.switchToOriginalGUI();
        } catch (RuntimeException failure) {
            LOG.warn("Could not reopen the terminal for crafting confirmation " + windowId, failure);
        }
        if (player.openContainer == confirm) {
            // No open packet was sent. The client dismisses this screen locally; a vanilla close ignores window IDs.
            player.closeContainer();
        } else if (player.openContainer instanceof ContainerCraftConfirm next && next.serializedItemToCraft.isEmpty()) {
            // A nested request can reopen a blank confirmation as its own parent. Leave real replacement jobs alone.
            var access = (ContainerCraftConfirmAccessor) next;
            if (access.neoecoae$getJob() == null && access.neoecoae$getResult() == null) player.closeScreen();
        }
    }

    /** Re-read the grid and stock through the normal planner routing, retaining the requested output and CPU choice. */
    public static void replan(EntityPlayerMP player, int windowId) {
        Tracked tracked = lookup(player, windowId);
        if (tracked != null) replan(player, tracked, Replan.REQUESTED);
    }

    /** No submission is retried synchronously: replace the entire plan once, or leave an actionable error. */
    private static void stockChanged(EntityPlayerMP player, Tracked tracked, Replan retry, String detail,
        Supplier<List<GenericStack>> shortages) {
        if (!tracked.session.canReplanForStock()) {
            reject(player, tracked, ECOCraftConfirmError.STOCK_UNSTABLE, detail, shortages.get());
            return;
        }
        replan(player, tracked, retry);
    }

    private static void replan(EntityPlayerMP player, Tracked tracked, Replan intent) {
        ContainerCraftConfirm container = tracked.container;
        var access = (ContainerCraftConfirmAccessor) container;
        var previousFuture = access.neoecoae$getJob();
        // Inside AE2's automatic start the *completed* future still occupies the slot. Never displace a live job.
        if (previousFuture != null && !(intent.automatic() && previousFuture.isDone())) return;
        IGrid grid = container.getGrid();
        if (grid == null || !canCraft(player, container, grid)) {
            reject(player, tracked, ECOCraftConfirmError.REPLAN_FAILED, "");
            return;
        }
        Future<ICraftingJob> pending;
        ECOPlannerOptions options;
        tracked.session.closeImprovements();
        try {
            ICraftingJob previous = access.neoecoae$getResult();
            var output = previous == null ? container.getItemToCraft() : previous.getOutput();
            if (output == null || output.getStackSize() <= 0) {
                reject(player, tracked, ECOCraftConfirmError.NO_PLAN, "");
                return;
            }
            var host = planningHost(container);
            options = host == null ? null : ECOPlannerOptions.from(host);
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            pending = crafting
                .beginCraftingJob(container.getWorld(), grid, actionSource(container), output.copy(), null);
        } catch (RuntimeException | LinkageError failure) {
            reject(player, tracked, ECOCraftConfirmError.REPLAN_FAILED, String.valueOf(failure));
            return;
        }
        if (pending == null) {
            reject(player, tracked, ECOCraftConfirmError.REPLAN_FAILED, "");
            return;
        }
        tracked.session.restart(options != null && options.cyclePlanningEnabled(), intent != Replan.REQUESTED);
        ECOCraftingGraphSync.discard(player);
        tracked.options = options;
        tracked.exportObserved = null;
        tracked.cpuCheckedResult = null;
        access.neoecoae$setResult(null);
        container.bytesUsed = 0;
        container.simulation = true;
        container.errorMessage = "";
        // If current routing is no longer ECO, show its report for manual review instead of an unguarded auto-start.
        boolean eco = NativeECOPlanning.isEcoPlanning(pending);
        container.setAutoStart(eco && intent == Replan.AUTO_START);
        container.setAutoStartAndFollow(eco && intent == Replan.AUTO_FOLLOW);
        container.setJob(pending);
        push(player, container, tracked.session);
    }

    private static boolean canCraft(EntityPlayerMP player, ContainerCraftConfirm container, IGrid grid) {
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        return player.openContainer == container && container.canInteractWith(player)
            && security != null
            && security.hasPermission(player, SecurityPermissions.CRAFT);
    }

    private static void stopAutoStart(ContainerCraftConfirm container) {
        container.setAutoStart(false);
        container.setAutoStartAndFollow(false);
    }

    private static @Nullable Tracked lookup(EntityPlayerMP player, int windowId) {
        if (!(player.openContainer instanceof ContainerCraftConfirm container) || container.windowId != windowId)
            return null;
        Tracked tracked = TRACKED.get(player);
        return tracked != null && tracked.container == container && tracked.session.isEco() ? tracked : null;
    }

    private static void reject(EntityPlayerMP player, Tracked tracked, ECOCraftConfirmError error, String detail) {
        reject(player, tracked, error, detail, Collections.emptyList());
    }

    private static void reject(EntityPlayerMP player, Tracked tracked, ECOCraftConfirmError error, String detail,
        List<GenericStack> shortages) {
        stopAutoStart(tracked.container);
        // A failed quick start can precede the first tracker tick. Build that plan's report before attaching its error.
        observe(
            tracked.container,
            tracked.session,
            ((ContainerCraftConfirmAccessor) tracked.container).neoecoae$getResult());
        tracked.session.fail(error, detail, shortages);
        push(player, tracked.container, tracked.session);
    }

    /** Largest shortage first, so the page's limited rows show what matters most. */
    private static List<GenericStack> shortages(KeyCounter shortfall) {
        List<GenericStack> list = new java.util.ArrayList<>(shortfall.size());
        for (var entry : shortfall)
            if (entry.getLongValue() > 0) list.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        list.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        return list;
    }

    /** The same source AE2's confirmation uses for submission: the player acting through the terminal host. */
    static BaseActionSource actionSource(ContainerCraftConfirm container) {
        Object target = container.getTarget();
        if (target instanceof IActionHost host) return new PlayerSource(container.getPlayerInv().player, host);
        return container.getActionSource();
    }

    /** The computation host whose settings planned this request (the one NativeECOPlanning.begin chose). */
    private static @Nullable ECOComputationSystemBlockEntity planningHost(ContainerCraftConfirm container) {
        IGrid grid = container.getGrid();
        if (grid == null) return null;
        return NativeECOPlanning.settings(grid, IActionSource.of(actionSource(container)));
    }

    /** The CPU chosen in the confirmation's CPU list, or {@code null} for automatic selection. */
    private static @Nullable ICraftingCPU selectedCpu(ContainerCraftConfirm container) {
        CraftingCPUStatus selected = container.cpuTable.getSelectedCPU();
        return selected == null ? null : selected.getServerCluster();
    }

    /** The selected CPU's tier, or with automatic selection the first idle ECO CPU (1.21's report header). */
    private static int selectedCpuTier(ContainerCraftConfirm container) {
        CraftingCPUStatus selected = container.cpuTable.getSelectedCPU();
        if (selected != null) return tier(selected.getServerCluster());
        IGrid grid = container.getGrid();
        if (grid == null) return 0;
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        if (crafting == null) return 0;
        for (ICraftingCPU cpu : crafting.getCpus())
            if (cpu instanceof NativeECOCpu eco && eco.isActive() && !eco.isBusy()) return tier(eco);
        return 0;
    }

    static int tier(@Nullable ICraftingCPU cpu) {
        if (!(cpu instanceof NativeECOCpu eco) || !(eco.runtime() instanceof ECOCraftingNativeRuntime runtime))
            return 0;
        var tier = runtime.cpu()
            .getTier();
        return tier == null ? 0 : ECOCraftConfirmRules.cpuTierGlyph(tier.getTier());
    }

    private static void pushCraftedItemToPins(ContainerCraftConfirm container) {
        if (!pushToPinsResolved) {
            Method method = null;
            try {
                method = ContainerCraftConfirm.class.getDeclaredMethod("pushCraftedItemToPins");
                method.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException absent) {
                method = null;
            }
            pushToPins = method;
            pushToPinsResolved = true;
        }
        Method method = pushToPins;
        if (method == null) return;
        try {
            method.invoke(container);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
