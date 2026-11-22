package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.NetworkInventoryHandler;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeNetworkReads;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeStackAmounts;

/** Preserve the imported network listing semantics in GTNH's per-network accumulator. */
@Mixin(value = NetworkInventoryHandler.class, remap = false)
public abstract class NetworkInventoryHandlerMixin implements NativeNetworkReads {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.List<appeng.api.storage.IMEInventoryHandler<?>> priorityInventory;

    @org.spongepowered.asm.mixin.Unique
    private long neoecoae$readsCheckedTick = Long.MIN_VALUE;

    @org.spongepowered.asm.mixin.Unique
    private boolean neoecoae$readsOtherNetwork;

    @org.spongepowered.asm.mixin.Unique
    private long neoecoae$craftingInsertedAmount;

    @org.spongepowered.asm.mixin.Unique
    private long neoecoae$monitoredCraftingInsertedAmount;

    @Override
    public long neoecoae$craftingInsertedAmount() {
        return neoecoae$craftingInsertedAmount;
    }

    @Override
    public long neoecoae$monitoredCraftingInsertedAmount() {
        return neoecoae$monitoredCraftingInsertedAmount;
    }

    @Override
    public void neoecoae$reportMonitoredCraftingInsertion(long amount) {
        neoecoae$monitoredCraftingInsertedAmount += amount;
    }

    /** Re-evaluated at most once per server tick: a storage bus can retarget without rebuilding this handler. */
    @Override
    public boolean neoecoae$readsOtherNetwork() {
        long tick = cn.dancingsnow.neoecoae.platform.network.ServerThread.tick();
        if (tick != neoecoae$readsCheckedTick) {
            neoecoae$readsCheckedTick = tick;
            boolean reads = false;
            for (var inventory : priorityInventory) if (inventory.getExternalNetworkInventory() != null) {
                reads = true;
                break;
            }
            neoecoae$readsOtherNetwork = reads;
        }
        return neoecoae$readsOtherNetwork;
    }

    @Redirect(
        method = "extractItems",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/IMEInventoryHandler;extractItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;"),
        require = 1,
        allow = 1)
    private IAEStack neoecoae$extractPhysicalSource(appeng.api.storage.IMEInventoryHandler inventory, IAEStack request,
        appeng.api.config.Actionable mode, appeng.api.networking.security.BaseActionSource source) {
        return cn.dancingsnow.neoecoae.platform.ae2.storage.NativeTransferFilters
            .extract(inventory, request, mode, source);
    }

    @Redirect(
        method = "injectItems",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/IMEInventoryHandler;injectItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;"),
        require = 5,
        allow = 5)
    private IAEStack neoecoae$insertPhysicalTarget(appeng.api.storage.IMEInventoryHandler inventory, IAEStack offered,
        appeng.api.config.Actionable mode, appeng.api.networking.security.BaseActionSource source) {
        if (cn.dancingsnow.neoecoae.platform.ae2.storage.NativeTransferFilters.excluded(inventory)) return offered;
        if (mode != appeng.api.config.Actionable.MODULATE) return inventory.injectItems(offered, mode, source);
        boolean crafting = inventory.isAutoCraftingInventory();
        NativeNetworkReads external = !crafting
            && inventory.getExternalNetworkInventory() instanceof NativeNetworkReads reads ? reads : null;
        long before = offered.getStackSize();
        long externalBefore = external == null ? 0L : external.neoecoae$monitoredCraftingInsertedAmount();
        IAEStack remaining = inventory.injectItems(offered, mode, source);
        long accepted = before - (remaining == null ? 0L : remaining.getStackSize());
        if (accepted > 0L) {
            if (crafting) neoecoae$craftingInsertedAmount += accepted;
            else if (external != null) neoecoae$craftingInsertedAmount += Math
                .min(accepted, external.neoecoae$monitoredCraftingInsertedAmount() - externalBefore);
        }
        return remaining;
    }

    @Redirect(
        method = "getAvailableItems(Lappeng/api/storage/data/IItemList;ILjava/util/Optional;)Lappeng/api/storage/data/IItemList;",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/IMEInventoryHandler;getAvailableItems(Lappeng/api/storage/data/IItemList;ILjava/util/Optional;)Lappeng/api/storage/data/IItemList;"),
        require = 1,
        allow = 1)
    private IItemList neoecoae$listAllowedSources(appeng.api.storage.IMEInventoryHandler inventory, IItemList out,
        int iteration, java.util.Optional filter) {
        if (cn.dancingsnow.neoecoae.platform.ae2.storage.NativeTransferFilters.excluded(inventory)) return out;
        IItemList listed = inventory.getAvailableItems(out, iteration, filter);
        NativeStackAmounts.capture(inventory, listed);
        return listed;
    }

    @Redirect(
        method = "getAvailableItems(Lappeng/api/storage/data/IItemList;ILjava/util/Optional;)Lappeng/api/storage/data/IItemList;",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IItemList;add(Lappeng/api/storage/data/IAEStack;)V"),
        require = 1,
        allow = 1)
    private void neoecoae$mergeStoredProjection(IItemList out, IAEStack stack) {
        NativeStackAmounts.add(out, stack);
    }

    @Redirect(
        method = "getAvailableItemsWithPriority(I)Lappeng/util/item/PrioritizedNetworkItemList;",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IItemList;add(Lappeng/api/storage/data/IAEStack;)V"),
        require = 1,
        allow = 1)
    private void neoecoae$mergePrioritizedProjection(IItemList out, IAEStack stack) {
        NativeStackAmounts.add(out, stack);
    }
}
