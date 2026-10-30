package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.integration.modules.NEIHelpers.NEICraftingHandler;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import cn.dancingsnow.neoecoae.platform.gt.upload.GtNeiRoutes;
import cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;

/**
 * Captures the GT recipe map (and the virtual circuit GT's NEI overlay strips) and NEI's recipe category name while NEI
 * fills an AE2 pattern terminal. Anything unreadable clears the hint; the server re-verifies every route regardless.
 */
@Mixin(value = NEICraftingHandler.class, remap = false)
public abstract class NeiPatternRouteMixin {

    @Inject(method = "overlayRecipe", at = @At("HEAD"), require = 1)
    private void neoecoae$captureRoute(GuiContainer gui, IRecipeHandler handler, int recipeIndex, boolean shift,
        CallbackInfo ci) {
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
        PatternUploadClient.captureHint(gui, map, circuit, neoecoae$category(handler));
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
