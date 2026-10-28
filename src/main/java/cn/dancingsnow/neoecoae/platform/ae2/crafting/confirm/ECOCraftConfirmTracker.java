package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.mixin.ContainerCraftConfirmAccessor;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;

/**
 * Server half of the ECO crafting report (1.21's confirmation menu mixin). Each tick it observes every open AE2
 * confirmation container; one whose request ECO planned is mirrored to its client, which then shows the ECO report
 * instead of AE2's page. Containers ECO never planned are left completely native.
 */
public final class ECOCraftConfirmTracker {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");
    private static final Map<EntityPlayerMP, Tracked> TRACKED = new WeakHashMap<>();
    /** rv3-beta-1080 pins the crafted item after a start; the pinned 990 jar has no such step. */
    private static volatile Method pushToPins;
    private static volatile boolean pushToPinsResolved;

    private ECOCraftConfirmTracker() {}

    private static final class Tracked {

        final ContainerCraftConfirm container;
        final ECOCraftConfirmSession session = new ECOCraftConfirmSession();

        Tracked(ContainerCraftConfirm container) {
            this.container = container;
        }
    }

    public static void tick() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Iterator<Map.Entry<EntityPlayerMP, Tracked>> it = TRACKED.entrySet()
            .iterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getKey().openContainer != entry.getValue().container) it.remove();
        }
        List<?> players = server.getConfigurationManager().playerEntityList;
        for (Object value : players) {
            if (!(value instanceof EntityPlayerMP player)) continue;
            Container open = player.openContainer;
            if (!(open instanceof ContainerCraftConfirm container)) continue;
            Tracked tracked = TRACKED.get(player);
            if (tracked == null) {
                tracked = new Tracked(container);
                TRACKED.put(player, tracked);
            }
            try {
                update(player, tracked);
            } catch (RuntimeException failure) {
                // The report is presentation only; never let it break the confirmation container.
                LOGGER.warn("[craft-confirm] ECO report update failed for {}", player.getCommandSenderName(), failure);
            }
        }
    }

    public static void clear() {
        TRACKED.clear();
    }

    private static void update(EntityPlayerMP player, Tracked tracked) {
        ContainerCraftConfirm container = tracked.container;
        ECOCraftConfirmSession session = tracked.session;
        var access = (ContainerCraftConfirmAccessor) container;
        Future<ICraftingJob> job = access.neoecoae$getJob();
        ICraftingJob result = access.neoecoae$getResult();
        if (!session.isEco()) {
            if (!NativeECOPlanning.isEcoPlanning(job) && !(result instanceof NativeECOCraftingJob)) return;
            session.begin(cyclePlanningEnabled(container));
        }
        if (result instanceof NativeECOCraftingJob ecoJob) {
            session.observeResult(result, version -> build(container, ecoJob, session, version));
        } else {
            session.observeResult(result, null);
            if (result == null && job == null)
                session.planningFailed(() -> "ECO planning ended without a plan; see the server log");
        }
        session.setCpuTier(selectedCpuTier(container));
        push(player, container, session);
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

    /** Start request from the ECO report; mirrors AE2's startJob but observes whether a CPU accepted the job. */
    public static void start(EntityPlayerMP player, int windowId, boolean forced) {
        Tracked tracked = lookup(player, windowId);
        if (tracked == null) return;
        ContainerCraftConfirm container = tracked.container;
        ECOCraftConfirmSession session = tracked.session;
        ICraftingJob result = ((ContainerCraftConfirmAccessor) container).neoecoae$getResult();
        if (!(result instanceof NativeECOCraftingJob job) || job.sourcePlan() == null) {
            reject(player, tracked, ECOCraftConfirmError.NO_PLAN, "");
            return;
        }
        ECOCraftConfirmError rejected = ECOCraftConfirmRules.rejectStart(session.state(), forced);
        if (rejected != null) {
            reject(player, tracked, rejected, "");
            return;
        }
        IGrid grid = container.getGrid();
        if (grid == null) {
            reject(player, tracked, ECOCraftConfirmError.NO_CPU_ACCEPTED, "");
            return;
        }
        NativeECOCraftingJob submitted = forced ? job.forced() : job;
        ICraftingLink link;
        try {
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            CraftingCPUStatus selected = container.cpuTable.getSelectedCPU();
            ICraftingCPU target = selected == null ? null : selected.getServerCluster();
            link = crafting.submitJob(submitted, null, target, true, actionSource(container), false);
        } catch (RuntimeException failure) {
            LOGGER.warn("[craft-confirm] ECO start failed: container={}, forced={}", windowId, forced, failure);
            reject(player, tracked, ECOCraftConfirmError.SUBMIT_FAILED, String.valueOf(failure));
            return;
        }
        container.setAutoStart(false);
        container.setAutoStartAndFollow(false);
        if (link == null) {
            LOGGER.info(
                "[craft-confirm] ECO start rejected by every CPU: container={}, forced={}, output={}",
                windowId,
                forced,
                job.sourcePlan()
                    .finalOutput());
            reject(player, tracked, ECOCraftConfirmError.NO_CPU_ACCEPTED, "");
            return;
        }
        pushCraftedItemToPins(container);
        container.switchToOriginalGUI();
    }

    public static void clearError(EntityPlayerMP player, int windowId) {
        Tracked tracked = lookup(player, windowId);
        if (tracked == null) return;
        tracked.session.clearError();
        push(player, tracked.container, tracked.session);
    }

    private static @Nullable Tracked lookup(EntityPlayerMP player, int windowId) {
        if (!(player.openContainer instanceof ContainerCraftConfirm container) || container.windowId != windowId)
            return null;
        Tracked tracked = TRACKED.get(player);
        return tracked != null && tracked.container == container && tracked.session.isEco() ? tracked : null;
    }

    private static void reject(EntityPlayerMP player, Tracked tracked, ECOCraftConfirmError error, String detail) {
        tracked.container.setAutoStart(false);
        tracked.session.fail(error, detail);
        push(player, tracked.container, tracked.session);
    }

    /** The same source AE2's confirmation uses for submission: the player acting through the terminal host. */
    private static BaseActionSource actionSource(ContainerCraftConfirm container) {
        Object target = container.getTarget();
        if (target instanceof IActionHost host) return new PlayerSource(container.getPlayerInv().player, host);
        return container.getActionSource();
    }

    private static boolean cyclePlanningEnabled(ContainerCraftConfirm container) {
        IGrid grid = container.getGrid();
        if (grid == null) return false;
        ECOComputationSystemBlockEntity host = NativeECOPlanning
            .settings(grid, IActionSource.of(actionSource(container)));
        return host != null && host.isCyclePlanningEnabled();
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

    private static int tier(@Nullable ICraftingCPU cpu) {
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
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.debug("[craft-confirm] Could not pin the crafted item", failure);
        }
    }
}
