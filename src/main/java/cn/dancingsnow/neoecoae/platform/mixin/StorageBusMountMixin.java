package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.me.storage.MEInventoryHandler;
import appeng.parts.misc.PartStorageBus;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeTransferFilters;

/** Mark only AE2's bus-owned mount, leaving the external container's ordinary users unchanged. */
@Mixin(value = PartStorageBus.class, remap = false)
public abstract class StorageBusMountMixin {

    @Inject(method = "getInternalHandler()Lappeng/me/storage/MEInventoryHandler;", at = @At("RETURN"), require = 1)
    private void neoecoae$rememberBusMount(CallbackInfoReturnable<MEInventoryHandler> callback) {
        NativeTransferFilters.storageBus(callback.getReturnValue());
    }
}
