package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GuideNH 1.3.54 rebuilds slider-driven structures without invalidating their material totals. */
@Mixin(targets = "com.hfstudio.guidenh.guide.scene.LytGuidebookScene", remap = false)
public abstract class GuideStructureBlockStatsMixin {

    @Shadow
    private void markBlockStatsDirty() {}

    @Inject(method = { "build", "clear" }, at = @At("RETURN"), require = 1)
    private void neoecoae$refreshBlockStats(CallbackInfo ci) {
        markBlockStatsDirty();
    }
}
