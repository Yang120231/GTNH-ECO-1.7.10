package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.platform.ae2.upload.PatternTermEncoder;
import cn.dancingsnow.neoecoae.platform.ae2.upload.PatternUploadService;

/** AE2's encode is private in rv3-beta-990; expose it and bind a pending NEI route hint to its result. */
@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class PatternTermEncodeMixin implements PatternTermEncoder {

    @Shadow
    private void encode() {}

    @Override
    public void neoecoae$encode() {
        encode();
    }

    @Inject(method = "encode", at = @At("RETURN"), require = 1)
    private void neoecoae$encoded(CallbackInfo ci) {
        var player = ((AEBaseContainer) (Object) this).getPlayerInv().player;
        if (player != null && !player.worldObj.isRemote)
            PatternUploadService.onEncoded((ContainerPatternTerm) (Object) this, player);
    }
}
