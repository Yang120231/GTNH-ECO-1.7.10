package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.CraftingCPUStatus;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;

/**
 * Server half of the ECO crafting status page. Each tick it observes every open AE2 crafting status container and
 * tells its client whether the network qualifies for the ECO page (an ECO computation system with the fast planner
 * on), the selected CPU's tier and the cycle members of the plan that CPU is running. The container itself and every
 * CPU entry it syncs stay AE2's.
 */
public final class ECOCraftingStatusTracker {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");
    private static final Map<EntityPlayerMP, Tracked> TRACKED = new WeakHashMap<>();
    /** Ticks between processing samples; the monitor drops a survey nobody sampled for 20 ticks. */
    private static final int PROCESSING_INTERVAL = 5;
    private static final int MAX_PROCESSING_ENTRIES = 512;

    private ECOCraftingStatusTracker() {}

    private static final class Tracked {

        final ContainerCraftingStatus container;
        boolean sent;
        boolean eco;
        int tier;
        /** The running plan whose cycle members were last sent; compared by identity. */
        @Nullable
        ICraftingPlan plan;
        /** The output whose machine details the client's tooltip shows, and the detail page it asked for. */
        @Nullable
        AEKey detailKey;
        int detailPage;
        long nextProcessingTick = Long.MIN_VALUE;
        /** The last processing payload sent; an empty tag means the client holds none. */
        CompoundTag processing = new CompoundTag();

        Tracked(ContainerCraftingStatus container) {
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
            if (entry.getKey().openContainer != entry.getValue().container || !players.contains(entry.getKey()))
                it.remove();
        }
        for (Object value : players) {
            if (!(value instanceof EntityPlayerMP player)) continue;
            Container open = player.openContainer;
            if (!(open instanceof ContainerCraftingStatus container)) continue;
            Tracked tracked = TRACKED.get(player);
            if (tracked == null) {
                tracked = new Tracked(container);
                TRACKED.put(player, tracked);
            }
            try {
                update(player, tracked);
            } catch (RuntimeException | LinkageError failure) {
                // The ECO page is presentation only; never let it break AE2's status container.
                LOGGER.warn("[craft-status] ECO status update failed for {}", player.getCommandSenderName(), failure);
            }
        }
    }

    public static void clear() {
        TRACKED.clear();
    }

    private static void update(EntityPlayerMP player, Tracked tracked) {
        ContainerCraftingStatus container = tracked.container;
        IGrid grid = grid(container);
        ECOComputationSystemBlockEntity host = grid == null ? null
            : NativeECOPlanning.settings(grid, IActionSource.of(actionSource(player, container)));
        boolean eco = host != null && host.isFastPlannerEnabled();
        ICraftingCPU cpu = selectedCpu(container);
        int tier = eco ? ECOCraftConfirmTracker.tier(cpu) : 0;
        ICraftingPlan plan = eco ? runningPlan(cpu) : null;
        boolean planChanged = plan != tracked.plan;
        syncProcessing(player, tracked, eco ? ecoRuntime(cpu) : null);
        if (tracked.sent && eco == tracked.eco && tier == tracked.tier && !planChanged) return;
        ECOCycleItemList cycles = null;
        if (planChanged || !tracked.sent) cycles = plan == null ? ECOCycleItemList.EMPTY : cycles(plan);
        tracked.sent = true;
        tracked.eco = eco;
        tracked.tier = tier;
        tracked.plan = plan;
        ECOCraftConfirmPackets.syncStatus(player, container.windowId, eco, tier, cycles);
    }

    /**
     * Live machine status of every output the selected ECO CPU is waiting for, through the CPU's own budgeted
     * processing monitor (the same one its ECO status page reads). Native AE2 CPUs record no machine targets.
     */
    private static void syncProcessing(EntityPlayerMP player, Tracked tracked,
        @Nullable ECOCraftingNativeRuntime runtime) {
        if (runtime == null) {
            if (!tracked.processing.isEmpty()) {
                tracked.processing = new CompoundTag();
                ECOCraftConfirmPackets.syncStatusProcessing(player, tracked.container.windowId, tracked.processing);
            }
            return;
        }
        var logic = runtime.cpu()
            .getLogic();
        // Scans advance a few coordinates per tick; the samples below only queue and publish them.
        logic.refreshProcessingProgress();
        long tick = player.worldObj.getTotalWorldTime();
        if (tick < tracked.nextProcessingTick) return;
        tracked.nextProcessingTick = tick + PROCESSING_INTERVAL;
        KeyCounter active = new KeyCounter();
        runtime.getItems(active, CraftingItemList.ACTIVE);
        ListTag entries = new ListTag();
        for (var entry : active) {
            if (entries.size() >= MAX_PROCESSING_ENTRIES) break;
            AEKey key = entry.getKey();
            ECOProcessingProgress progress = logic.getProcessingProgress(key);
            if (progress.machineCount() == 0 && !progress.refreshing()) continue;
            CompoundTag row = new CompoundTag();
            row.put("key", key.toTagGeneric(RegistryAccess.EMPTY));
            row.put(
                "progress",
                progress.page(key.equals(tracked.detailKey) ? tracked.detailPage : -1)
                    .write());
            entries.add(row);
        }
        CompoundTag payload = new CompoundTag();
        payload.putBoolean("eco", true);
        payload.put("entries", entries);
        if (payload.nativeTag()
            .equals(tracked.processing.nativeTag())) return;
        tracked.processing = payload;
        ECOCraftConfirmPackets.syncStatusProcessing(player, tracked.container.windowId, payload);
    }

    /** Client request: the hovered output's machine details, or none when {@code key} is null. */
    public static void requestDetail(EntityPlayerMP player, int windowId, @Nullable AEKey key, int page) {
        if (!(player.openContainer instanceof ContainerCraftingStatus container) || container.windowId != windowId)
            return;
        Tracked tracked = TRACKED.get(player);
        if (tracked == null || tracked.container != container) return;
        tracked.detailKey = key;
        tracked.detailPage = Math.max(0, page);
        tracked.nextProcessingTick = Long.MIN_VALUE;
    }

    private static @Nullable ECOCraftingNativeRuntime ecoRuntime(@Nullable ICraftingCPU cpu) {
        if (!(cpu instanceof NativeECOCpu eco) || !eco.isBusy()
            || !(eco.runtime() instanceof ECOCraftingNativeRuntime runtime)) return null;
        return runtime;
    }

    /** The plan an ECO CPU is running; native AE2 CPUs keep no plan to read cycles from. */
    private static @Nullable ICraftingPlan runningPlan(@Nullable ICraftingCPU cpu) {
        if (!(cpu instanceof NativeECOCpu eco) || !(eco.runtime() instanceof ECOCraftingNativeRuntime runtime))
            return null;
        return eco.isBusy() ? runtime.cpu()
            .getPlan() : null;
    }

    /** The confirmation report's cycle member list, rebuilt from the planning result of the running plan. */
    private static ECOCycleItemList cycles(ICraftingPlan plan) {
        try {
            ECOPlanningResult result = ECOCraftConfirmReports.resolve(plan);
            if (result == null) return ECOCycleItemList.EMPTY;
            ECOCycleItemList cycles = ECOCraftConfirmReports
                .cycleItems(result, CraftingGraphSnapshotFactory.create(result));
            List<ECOCycleItemList.Entry> items = cycles.items();
            return items.size() > ECOCraftConfirmReport.MAX_CYCLE_ITEMS
                ? new ECOCycleItemList(new ArrayList<>(items.subList(0, ECOCraftConfirmReport.MAX_CYCLE_ITEMS)))
                : cycles;
        } catch (RuntimeException failure) {
            LOGGER.warn("[craft-status] ECO cycle list failed for {}", plan.finalOutput(), failure);
            return ECOCycleItemList.EMPTY;
        }
    }

    private static @Nullable IGrid grid(ContainerCraftingStatus container) {
        Object target = container.getTarget();
        IGridNode node = null;
        if (target instanceof IActionHost host) node = host.getActionableNode();
        if (node == null && target instanceof IGridHost host) node = host.getGridNode(ForgeDirection.UNKNOWN);
        return node == null ? null : node.getGrid();
    }

    private static BaseActionSource actionSource(EntityPlayerMP player, ContainerCraftingStatus container) {
        Object target = container.getTarget();
        if (target instanceof IActionHost host) return new PlayerSource(player, host);
        return container.getActionSource();
    }

    private static @Nullable ICraftingCPU selectedCpu(ContainerCraftingStatus container) {
        CraftingCPUStatus selected = container.getCPUTable()
            .getSelectedCPU();
        return selected == null ? null : selected.getServerCluster();
    }
}
