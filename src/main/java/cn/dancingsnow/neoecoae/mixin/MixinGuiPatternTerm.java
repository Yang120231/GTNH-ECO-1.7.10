package cn.dancingsnow.neoecoae.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;

@Mixin(value = GuiPatternTerm.class, remap = false)
public abstract class MixinGuiPatternTerm {

    @Shadow
    private GuiImgButton encodeBtn;

    private GuiImgButton neoecoae$openUploadButton;
    private GuiImgButton neoecoae$autoUploadButton;

    @Inject(method = "initGui", at = @At("TAIL"), remap = true)
    private void neoecoae$addUploadButtons(CallbackInfo ci) {
        int x = this.encodeBtn == null ? 0 : this.encodeBtn.xPos() + 31;
        int y = this.encodeBtn == null ? 0 : this.encodeBtn.yPos() + 9;
        this.neoecoae$openUploadButton = this.neoecoae$createUploadButton(
            x,
            y,
            Settings.ACTIONS,
            ActionItems.ENCODE,
            "gui.neoecoae.pattern_upload.button.open.tooltip.title",
            "gui.neoecoae.pattern_upload.button.open.tooltip.description");
        this.neoecoae$autoUploadButton = this.neoecoae$createUploadButton(
            x,
            y + 20,
            Settings.ACTIONS,
            ActionItems.MOLECULAR_ASSEMBLEERS_ON,
            "gui.neoecoae.pattern_upload.button.auto.tooltip.title",
            "gui.neoecoae.pattern_upload.button.auto.tooltip.description");
        ((MixinGuiScreenAccessor) (Object) this).neoecoae$getButtonList()
            .add(this.neoecoae$openUploadButton);
        ((MixinGuiScreenAccessor) (Object) this).neoecoae$getButtonList()
            .add(this.neoecoae$autoUploadButton);
    }

    private GuiImgButton neoecoae$createUploadButton(int x, int y, Settings setting, Enum value, String titleKey,
        String descriptionKey) {
        try {
            Class<?> buttonClass = Class.forName("cn.dancingsnow.neoecoae.client.PatternUploadButton");
            Object button = buttonClass
                .getConstructor(int.class, int.class, Settings.class, Enum.class, String.class, String.class)
                .newInstance(x, y, setting, value, titleKey, descriptionKey);
            if (button instanceof GuiImgButton) return (GuiImgButton) button;
        } catch (Throwable ignored) {
            // A stale client jar must still be able to open the terminal with the stock AE2 tooltip.
        }
        return new GuiImgButton(x, y, setting, value);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, remap = true)
    private void neoecoae$uploadButtonAction(net.minecraft.client.gui.GuiButton button, CallbackInfo ci) {
        if (button == this.neoecoae$openUploadButton) {
            NEPatternUploadNetwork.requestOpenUpload(false);
            ci.cancel();
        } else if (button == this.neoecoae$autoUploadButton) {
            NEPatternUploadNetwork.requestOpenUpload(true);
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void neoecoae$rightClickEncode(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button == 1 && this.encodeBtn != null
            && this.encodeBtn.mousePressed(Minecraft.getMinecraft(), mouseX, mouseY)) {
            NEPatternUploadNetwork.requestPrepare();
            ci.cancel();
        }
    }
}
