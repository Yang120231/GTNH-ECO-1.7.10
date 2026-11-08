package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.client.gui.implementations.GuiPatternTerm;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import cn.dancingsnow.neoecoae.platform.gt.upload.GtNeiRoutes;
import cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.ShapedRecipeHandler;

/**
 * Captures the recipe source at NEI's transfer dispatch, before either the selected overlay or its legacy fallback
 * fills a pattern terminal. NotEnoughEnergistics replaces AE2's overlay, so intercepting AE2's own handler misses it.
 * Anything unreadable clears the hint; the server re-verifies every route regardless.
 */
@Mixin(value = RecipeHandlerRef.class, remap = false)
public abstract class NeiPatternRouteMixin {

    @Shadow
    @Final
    public IRecipeHandler handler;

    @Shadow
    @Final
    public int recipeIndex;

    @Shadow
    private GuiContainer prepareGui(GuiContainer gui) {
        throw new AssertionError();
    }

    @Inject(
        method = "fillCraftingGrid(Lnet/minecraft/client/gui/inventory/GuiContainer;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/nei/recipe/RecipeHandlerRef;allowedTransferAlghoritm(Lnet/minecraft/client/gui/inventory/GuiContainer;)Z"),
        require = 1)
    private void neoecoae$captureFill(GuiContainer gui, int multiplier, CallbackInfo ci) {
        neoecoae$captureRoute(gui);
    }

    @Inject(
        method = "craft(Lnet/minecraft/client/gui/inventory/GuiContainer;I)Z",
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/nei/api/IOverlayHandler;craft(Lnet/minecraft/client/gui/inventory/GuiContainer;Lcodechicken/nei/recipe/IRecipeHandler;II)Z"),
        require = 1)
    private void neoecoae$captureCraft(GuiContainer gui, int multiplier, CallbackInfoReturnable<Boolean> cir) {
        neoecoae$captureRoute(gui);
    }

    private void neoecoae$captureRoute(GuiContainer gui) {
        // Use the same original screen as NEI, including when the player opened nested recipe/usage pages.
        GuiContainer terminal = prepareGui(gui);
        if (!(terminal instanceof GuiPatternTerm)) return;
        String map = null;
        ItemStack circuit = null;
        // GT types stay behind this check; the mixin itself is applied whenever NEI is present.
        if (NativeGregTech.isAvailable()) {
            try {
                GtNeiRoutes.Hint hint = GtNeiRoutes.read(handler, recipeIndex);
                if (hint != null) {
                    map = hint.map();
                    circuit = hint.circuit();
                }
            } catch (RuntimeException unreadable) {
                map = null;
                circuit = null;
            }
        }
        PatternUploadClient
            .captureHint(terminal, map, circuit, neoecoae$category(handler), neoecoae$categoryId(handler, map));
    }

    /** NEI's handler identity and overlay distinguish categories without depending on the display language. */
    private static String neoecoae$categoryId(IRecipeHandler handler, String map) {
        if (handler == null || handler instanceof ShapedRecipeHandler) return "";
        if (map != null && !map.trim()
            .isEmpty()) return "gt:" + map.trim();
        String overlay = "";
        try {
            String value = handler.getOverlayIdentifier();
            if (value != null) overlay = value.trim();
        } catch (RuntimeException | LinkageError unreadable) { /* The handler ID is still usable. */ }
        if ("crafting".equals(overlay) || "crafting2x2".equals(overlay)) return "";
        String id = handler.getClass()
            .getName();
        try {
            String value = handler.getHandlerId();
            if (value != null && !value.trim()
                .isEmpty()) id = value.trim();
        } catch (RuntimeException | LinkageError unreadable) { /* Legacy handler: use its class. */ }
        return "nei:" + id.length() + ":" + id + overlay;
    }

    /** NEI's tab name for machine recipes; crafting-table recipes name no machine and would only hide targets. */
    private static String neoecoae$category(IRecipeHandler handler) {
        try {
            if (handler == null || handler instanceof ShapedRecipeHandler) return "";
            String overlay = handler.getOverlayIdentifier();
            if ("crafting".equals(overlay) || "crafting2x2".equals(overlay)) return "";
            String name = handler.getRecipeName();
            if (name == null) return "";
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(name);
            plain = plain == null ? ""
                : plain.replaceAll("§.?", "")
                    .trim();
            return plain.length() > 128 ? plain.substring(0, 128) : plain;
        } catch (RuntimeException | LinkageError unreadable) {
            return "";
        }
    }
}
