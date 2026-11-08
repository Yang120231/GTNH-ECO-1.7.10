package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.ae2.upload.PatternTermEncoder;
import cn.dancingsnow.neoecoae.platform.ae2.upload.PatternUploadService;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Brackets native encoding; AE2's successful-encode listener binds the selected draft to its result. */
@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class PatternTermEncodeMixin implements PatternTermEncoder {

    @Shadow
    public abstract void encode();

    @Override
    public void neoecoae$encode() {
        encode();
    }

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$beforeEncode(CallbackInfo ci) {
        var player = ((AEBaseContainer) (Object) this).getPlayerInv().player;
        if (player == null || player.worldObj.isRemote) return;
        ContainerPatternTerm container = (ContainerPatternTerm) (Object) this;
        if (!ServerTasks.isServerThread()) {
            ServerTasks.captureSession()
                .execute(() -> {
                    if (player.openContainer != container) return;
                    try {
                        ((PatternTermEncoder) container).neoecoae$encode();
                    } catch (RuntimeException failure) {
                        NeoECOAE.LOG.warn("Pattern terminal encode failed", failure);
                    } finally {
                        PatternUploadService.afterEncoded(player);
                    }
                });
            ci.cancel();
            return;
        }
        PatternUploadService.beforeEncoded(container, player);
    }

    @Inject(method = "encode", at = @At("RETURN"), require = 1)
    private void neoecoae$afterEncode(CallbackInfo ci) {
        var player = ((AEBaseContainer) (Object) this).getPlayerInv().player;
        if (player != null && !player.worldObj.isRemote) PatternUploadService.afterEncoded(player);
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 1)
    private void neoecoae$draftCleared(CallbackInfo ci) {
        var player = ((AEBaseContainer) (Object) this).getPlayerInv().player;
        if (player == null || player.worldObj.isRemote) return;
        ContainerPatternTerm container = (ContainerPatternTerm) (Object) this;
        if (ServerTasks.isServerThread()) PatternUploadService.draftCleared(player);
        else ServerTasks.captureSession()
            .execute(() -> { if (player.openContainer == container) PatternUploadService.draftCleared(player); });
    }
}
