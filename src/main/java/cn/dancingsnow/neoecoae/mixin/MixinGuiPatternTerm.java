package cn.dancingsnow.neoecoae.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;

@Mixin(value = GuiPatternTerm.class, remap = false)
public abstract class MixinGuiPatternTerm {

    @Shadow
    private GuiImgButton encodeBtn;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void neoecoae$rightClickEncode(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button == 1 && this.encodeBtn != null
            && this.encodeBtn.mousePressed(Minecraft.getMinecraft(), mouseX, mouseY)) {
            NEPatternUploadNetwork.requestPrepare();
            ci.cancel();
        }
    }
}
