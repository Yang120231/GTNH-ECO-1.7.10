package cn.dancingsnow.neoecoae.platform.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.NetworkMonitor;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeIncrementalStorageList;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;

/** Keeps rv3's cached network list current from posted changes; see {@link NativeIncrementalStorageList}. */
@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class NetworkMonitorMixin {

    @Shadow
    private boolean hasChanged;

    @Shadow
    @Final
    private IItemList<?> cachedList;

    @Shadow
    @Final
    private IAEStackType<?> stackType;

    @Shadow
    private volatile boolean locked;

    @Unique
    private boolean neoecoae$removal;

    @Unique
    private Iterable<?> neoecoae$changes;

    @Unique
    private boolean neoecoae$appliedSinceRebuild;

    @Unique
    private int neoecoae$ticksSinceRebuild;

    @Shadow
    public abstract IMEInventoryHandler<?> getHandler();

    // notifyListenersOfChange receives removal batches unsigned; those always take AE2's rebuild.
    @Inject(method = "postChange", at = @At("HEAD"), require = 1)
    private void neoecoae$captureDirection(boolean add, Iterable<?> changes, BaseActionSource source, CallbackInfo ci) {
        neoecoae$removal = !add;
    }

    @Inject(method = "notifyListenersOfChange", at = @At("HEAD"), require = 1)
    private void neoecoae$captureChanges(Iterable<?> diff, BaseActionSource source, CallbackInfo ci) {
        neoecoae$changes = diff;
    }

    @Redirect(
        method = "notifyListenersOfChange",
        at = @At(value = "FIELD", target = "Lappeng/me/cache/NetworkMonitor;hasChanged:Z", opcode = Opcodes.PUTFIELD),
        require = 1,
        allow = 1)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void neoecoae$applyChanges(NetworkMonitor<?> monitor, boolean value) {
        Iterable diff = neoecoae$changes;
        neoecoae$changes = null;
        // A pending rebuild, a cell-array update (monitors locked) or a removal batch keep AE2's behaviour.
        if (diff != null && !hasChanged
            && !locked
            && !neoecoae$removal
            && NativeIncrementalStorageList.eligible(getHandler())
            && NativeIncrementalStorageList.apply(cachedList, stackType, diff)) {
            neoecoae$appliedSinceRebuild = true;
            return;
        }
        hasChanged = value;
    }

    @Inject(method = "getStorageList", at = @At("HEAD"), require = 1)
    private void neoecoae$rebuildResetsRepair(CallbackInfoReturnable<IItemList<?>> cir) {
        if (hasChanged) {
            neoecoae$appliedSinceRebuild = false;
            neoecoae$ticksSinceRebuild = 0;
        }
    }

    @Inject(method = "onTick", at = @At("HEAD"), require = 1)
    private void neoecoae$scheduleRepair(CallbackInfo ci) {
        if (neoecoae$appliedSinceRebuild
            && ++neoecoae$ticksSinceRebuild >= PlanningSettings.networkStorageListRepairTicks) {
            neoecoae$appliedSinceRebuild = false;
            neoecoae$ticksSinceRebuild = 0;
            hasChanged = true;
        }
    }
}
