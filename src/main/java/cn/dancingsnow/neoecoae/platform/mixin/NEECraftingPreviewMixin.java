package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiOpenEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.vfyjxf.nee.nei.NEECraftingPreviewHandler;
import com.github.vfyjxf.nee.utils.IngredientTracker;

import appeng.client.gui.implementations.GuiCraftConfirm;
import cn.dancingsnow.neoecoae.compat.nei.NEETemporaryCraftingClient;
import cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftConfirmGui;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.recipe.IRecipeHandler;

/** Adapts NEE's existing tracker and cleanup rather than maintaining a second recipe/shortage calculation. */
@Mixin(value = NEECraftingPreviewHandler.class, remap = false)
public abstract class NEECraftingPreviewMixin implements NEETemporaryCraftingClient.PreviewLifecycle {

    @Shadow
    private IngredientTracker tracker;
    @Shadow
    private NBTTagCompound patternCompound;
    @Shadow
    private boolean isAutoStart;
    @Shadow
    private boolean isRequesting;
    @Unique
    private boolean neoecoae$nativeQueue;

    @Shadow
    private void requestNextIngredient() {
        throw new AssertionError();
    }

    @Shadow
    private void removePatternIfNeeded() {
        throw new AssertionError();
    }

    @Inject(method = "handle", at = @At("HEAD"), require = 1)
    private void neoecoae$newRequest(GuiContainer gui, IRecipeHandler recipe, int index,
        CallbackInfoReturnable<Boolean> cir) {
        neoecoae$nativeQueue = false;
        NEETemporaryCraftingClient.clear();
    }

    @Inject(method = "requestNextIngredient", at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$batchMissing(CallbackInfo ci) {
        if (neoecoae$nativeQueue || tracker == null) return;
        IngredientTracker saved = tracker;
        boolean requesting = isRequesting;
        if (!NEETemporaryCraftingClient.request(saved.getRequireStacks(), isAutoStart, () -> {
            tracker = saved;
            isRequesting = requesting;
            neoecoae$nativeQueue = true;
            requestNextIngredient();
        })) return;
        tracker = null;
        isRequesting = false;
        ci.cancel();
    }

    @Override
    public void neoecoae$cancelPreview() {
        removePatternIfNeeded();
        tracker = null;
        isRequesting = false;
        neoecoae$nativeQueue = false;
    }

    @Inject(method = "onGuiCraftConfirmOpen", at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$confirmationLifecycle(GuiOpenEvent event, CallbackInfo ci) {
        var old = Minecraft.getMinecraft().currentScreen;
        // Replacing the view of the same container is not confirmation, cancellation or leaving the menu.
        if (old instanceof GuiCraftConfirm nativeGui && event.gui instanceof ECOCraftConfirmGui eco
            && nativeGui.inventorySlots == eco.inventorySlots) {
            ci.cancel();
            return;
        }
        var oldConfirm = old instanceof ECOCraftConfirmGui eco ? eco
            : old instanceof IGuiContainerOverlay overlay && overlay.getFirstScreen() instanceof ECOCraftConfirmGui eco
                ? eco
                : null;
        if (oldConfirm == null) return;
        var next = event.gui instanceof IGuiContainerOverlay overlay ? overlay.getFirstScreen() : event.gui;
        if (next instanceof GuiContainer gui && gui.inventorySlots == oldConfirm.inventorySlots) {
            ci.cancel();
            return;
        }
        if (patternCompound != null) {
            removePatternIfNeeded();
            isRequesting = false;
        }
        if (tracker != null) {
            if (tracker.hasNext()) requestNextIngredient();
            else {
                tracker = null;
                isRequesting = false;
            }
        }
        ci.cancel();
    }
}
