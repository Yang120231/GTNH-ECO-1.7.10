package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingPostPatternChangeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.cache.CraftingGridCache;
import appeng.util.item.OreListMultiMap;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternPublication;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Coalesce the cost of AE2's subscriber, not the events delivered to other mods. */
@Mixin(value = CraftingGridCache.class, remap = false, priority = 1100)
public abstract class CraftingPatternPublicationMixin implements NativePatternPublication.Owner {

    @Shadow
    protected static int pauseRebuilds;
    @Shadow
    @Final
    protected Set<ICraftingProvider> craftingProviders;
    @Shadow
    @Final
    protected Map<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods;
    @Shadow
    @Final
    protected Map<IAEStack<?>, List<ICraftingMedium>> emitableMediums;
    @Shadow
    @Final
    protected Map<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> craftableItems;
    @Shadow
    @Final
    protected Map<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItemsLegacy;
    @Shadow
    @Final
    protected OreListMultiMap<ICraftingPatternDetails> craftableItemSubstitutes;
    @Shadow
    @Final
    protected Map<UUID, ICraftingPatternDetails> inputOnlyPatterns;
    @Shadow
    @Final
    protected Set<ICraftingPostPatternChangeListener> postPatternChangeListeners;
    @Shadow
    protected IStorageGrid storageGrid;

    @Shadow
    protected abstract void updatePatterns();

    @Shadow
    protected abstract void setPatternsFromCraftingMethods();

    @Unique
    private final NativePatternPublication neoecoae$patterns = new NativePatternPublication();

    @Inject(
        method = "updateCPUClusters(Lappeng/api/networking/events/MENetworkCraftingPatternChange;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void neoecoae$queue(MENetworkCraftingPatternChange event, CallbackInfo ci) {
        if (!ServerTasks.isServerThread()) return;
        // Missing/indirect event sources are not evidence that a publication was empty.
        ICraftingProvider source = event.provider;
        if (!craftingProviders.contains(source)) {
            // DualityInterface posts itself, but AE2 registered its tile/part, which delegates to that duality.
            source = source instanceof appeng.helpers.DualityInterface duality && event.node != null
                && event.node.getMachine() instanceof appeng.helpers.IInterfaceHost host
                && host.getInterfaceDuality() == duality
                && craftingProviders.contains(host) ? host : null;
        }
        neoecoae$patterns.request(this, source);
        ci.cancel();
    }

    @WrapMethod(method = "updatePatterns")
    private void neoecoae$journalFullBuild(Operation<Void> original) {
        if (pauseRebuilds != 0) {
            original.call();
            return;
        }
        if (neoecoae$patterns.busy()) {
            neoecoae$patterns.request(this, null);
            return;
        }
        neoecoae$patterns.fullRebuild(this, () -> {
            // AE2 1095 clears the multi-type table but not the legacy mirror on a native rebuild.
            craftableItemsLegacy.clear();
            original.call();
        });
    }

    @WrapOperation(
        method = "updatePatterns",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;provideCrafting(Lappeng/api/networking/crafting/ICraftingProviderHelper;)V"),
        require = 1)
    private void neoecoae$record(ICraftingProvider provider, ICraftingProviderHelper helper, Operation<Void> original) {
        neoecoae$patterns.record(provider, () -> original.call(provider, helper));
    }

    @Inject(method = "addCraftingOption", at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$option(ICraftingMedium medium, ICraftingPatternDetails pattern, CallbackInfo ci) {
        if (neoecoae$patterns.option(medium, pattern)) ci.cancel();
    }

    @Inject(
        method = "setEmitable(Lappeng/api/networking/crafting/ICraftingMedium;Lappeng/api/storage/data/IAEStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void neoecoae$emitter(ICraftingMedium medium, IAEStack<?> key, CallbackInfo ci) {
        if (neoecoae$patterns.emitter(medium, key)) ci.cancel();
    }

    @Inject(
        method = { "getCraftingMultiPatterns", "getCraftingPatterns", "getCraftingFor", "getInputOnlyPattern",
            "getMediums", "getEmitableItems", "getEmitableMediums", "canEmitFor", "getAvailableItems",
            "beginCraftingJob", "submitJob" },
        at = @At("HEAD"),
        require = 1)
    private void neoecoae$beforeRead(CallbackInfoReturnable<?> cir) {
        neoecoae$flushPatterns();
    }

    @Override
    public void neoecoae$flushPatterns() {
        if (!neoecoae$patterns.pending() || neoecoae$patterns.busy()
            || pauseRebuilds != 0
            || !ServerTasks.isServerThread()) return;
        if (neoecoae$patterns.needsFull(craftingProviders)) {
            updatePatterns();
            return;
        }
        neoecoae$patterns.flush(
            this,
            craftingProviders,
            (ICraftingProviderHelper) (Object) this,
            craftingMethods,
            emitableMediums,
            () -> {
                Set<IAEStack<?>> before = neoecoae$visible();
                return () -> neoecoae$publish(before);
            });
    }

    @Unique
    private Set<IAEStack<?>> neoecoae$visible() {
        Set<IAEStack<?>> visible = new HashSet<>(craftableItems.keySet());
        visible.addAll(emitableMediums.keySet());
        return visible;
    }

    @Unique
    private void neoecoae$publish(Set<IAEStack<?>> before) {
        craftableItems.clear();
        craftableItemsLegacy.clear();
        inputOnlyPatterns.clear();
        craftableItemSubstitutes.clear();
        setPatternsFromCraftingMethods();
        Set<IAEStack<?>> after = neoecoae$visible();
        List<IAEStack<?>> changed = new ArrayList<>();
        for (IAEStack<?> key : after) if (!before.remove(key)) changed.add(key);
        changed.addAll(before);
        if (!changed.isEmpty()) for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            List<IAEStack<?>> channel = new ArrayList<>();
            for (IAEStack<?> key : changed) if (key.getStackType() == type) channel.add(key);
            if (!channel.isEmpty()) storageGrid.postAlterationOfStoredItems(type, channel, new BaseActionSource());
        }
        for (ICraftingPostPatternChangeListener listener : ImmutableList.copyOf(postPatternChangeListeners))
            listener.onPostPatternChange();
    }
}
