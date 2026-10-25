package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.NetworkInventoryHandler;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeStackAmounts;

/** Preserve the imported network listing semantics in GTNH's per-network accumulator. */
@Mixin(value = NetworkInventoryHandler.class, remap = false)
public abstract class NetworkInventoryHandlerMixin {

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
        return cn.dancingsnow.neoecoae.platform.ae2.storage.NativeTransferFilters.excluded(inventory) ? offered
            : inventory.injectItems(offered, mode, source);
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
        return cn.dancingsnow.neoecoae.platform.ae2.storage.NativeTransferFilters.excluded(inventory) ? out
            : inventory.getAvailableItems(out, iteration, filter);
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
