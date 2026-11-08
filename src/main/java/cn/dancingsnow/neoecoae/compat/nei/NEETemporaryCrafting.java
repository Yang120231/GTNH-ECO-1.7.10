package cn.dancingsnow.neoecoae.compat.nei;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.vfyjxf.nee.network.packet.PacketCraftingRequest;
import com.github.vfyjxf.nee.utils.GuiUtils;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.TemporaryCraftingOrder;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** Server-side optional integration. NEE continues to own opening the correct wired or wireless confirmation. */
public final class NEETemporaryCrafting {

    public interface PatternLifecycle {

        void neoecoae$deferCleanup(int slot);
    }

    private NEETemporaryCrafting() {}

    /** Retain a physical NEE pattern while an ECO job uses it; NEE's own work tracker removes it afterward. */
    public static boolean deferPatternRemoval(appeng.api.networking.IGrid grid,
        appeng.tile.inventory.AppEngInternalInventory inventory, int slot) {
        var stack = inventory.getStackInSlot(slot);
        if (stack == null) return false;
        var definition = cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(stack);
        appeng.api.networking.crafting.ICraftingGrid crafting = grid
            .getCache(appeng.api.networking.crafting.ICraftingGrid.class);
        boolean inUse = false;
        for (var cpu : crafting.getCpus()) {
            if (!(cpu instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu eco)
                || !eco.isBusy()
                || !(eco
                    .runtime() instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime runtime))
                continue;
            var plan = runtime.cpu()
                .getPlan();
            if (plan != null && plan.patternTimes()
                .keySet()
                .stream()
                .anyMatch(pattern -> definition.equals(pattern.getDefinition()))) {
                inUse = true;
                break;
            }
        }
        if (!inUse) return false;
        for (var node : grid.getMachines(com.github.vfyjxf.nee.block.tile.TilePatternInterface.class)) {
            var tile = (com.github.vfyjxf.nee.block.tile.TilePatternInterface) node.getMachine();
            if (tile.getPatternInventory() != inventory) continue;
            ((PatternLifecycle) tile).neoecoae$deferCleanup(slot);
            try {
                tile.getProxy()
                    .getTick()
                    .alertDevice(
                        tile.getProxy()
                            .getNode());
            } catch (appeng.me.GridAccessException offline) { /*
                                                               * NEE resumes its own work tracker when the grid returns.
                                                               */ }
            return true;
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static boolean open(EntityPlayerMP player, int windowId, List<GenericStack> ingredients, boolean autoStart) {
        if (!(player.openContainer instanceof AEBaseContainer container) || container.windowId != windowId
            || !container.canInteractWith(player)) return false;
        var grid = GuiUtils.getGrid(container);
        if (grid == null) return false;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (security == null || !security.hasPermission(player, SecurityPermissions.CRAFT)) return false;
        var host = NativeECOPlanning.settings(grid, IActionSource.of(container.getActionSource()));
        if (host == null || !host.isFastPlannerEnabled()) return false;
        List<GenericStack> requests = TemporaryCraftingOrder.normalize(ingredients);
        if (requests.size() < 2 || !NativeECOPlanning.supportsOrder(grid, requests)) return false;
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        for (var request : requests) {
            var stack = request.what()
                .toNative(request.amount());
            appeng.api.storage.IMEMonitor monitor = storage.getMEMonitor(stack.getStackType());
            appeng.api.storage.data.IAEStack<?> found = monitor == null ? null
                : (appeng.api.storage.data.IAEStack<?>) monitor.getStorageList()
                    .findPrecise(stack);
            if (found == null || !found.isCraftable()) return false;
        }
        var output = TemporaryCraftingOrder.create(
            requests,
            GridView.of(grid)
                .getStorageService()
                .getInventory()
                .getAvailableStacks())
            .toNative(1);
        var request = new PacketCraftingRequest(
            "AE",
            PacketCraftingRequest.COMMAND_OPEN_CRAFT_CONFIRM,
            output.toNBTGeneric(),
            1,
            false);
        request.openCraftConfirm(container, grid, output, player);
        if (!(player.openContainer instanceof ContainerCraftConfirm confirm)
            || !TemporaryCraftingOrder.isOrder(AEKey.fromNative(confirm.getItemToCraft()))) return false;
        // Arm only after opening succeeds, so an immediately completed plan cannot be mistaken for a rejected request.
        confirm.setAutoStartAndFollow(autoStart);
        return true;
    }
}
