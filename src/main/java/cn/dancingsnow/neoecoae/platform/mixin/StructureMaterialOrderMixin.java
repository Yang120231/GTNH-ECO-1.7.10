package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.Collections;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.implementations.GuiPatternTerm;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient;

/** BlockRenderer fills NEE directly, bypassing NEI's normal recipe overlay dispatch. */
@Mixin(targets = "blockrenderer6343.client.utils.BRUtil", remap = false)
public abstract class StructureMaterialOrderMixin {

    @Inject(method = "neiOverlay", at = @At("RETURN"), require = 1)
    private static void neoecoae$materialOrder(CallbackInfo ci) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiPatternTerm terminal)) return;
        // Follow the fill packet on the same connection, so its clear cannot erase this source hint.
        PatternUploadClient.captureHint(
            terminal,
            "",
            null,
            "gui.neoecoae.pattern_upload.material_order",
            RouteHint.MATERIAL_ORDER_CATEGORY,
            Collections.emptyList());
    }
}
