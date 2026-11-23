package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "reobf.proghatches.gt.metatileentity.BufferedDualInputHatch$DualInvBuffer", remap = false)
public abstract class ProgrammableCircuitBufferMixin {

    @Shadow
    public int i;

    @Shadow
    public int f;

    @Shadow
    abstract boolean itemCounted(int slot);

    @Shadow
    abstract boolean fluidCounted(int slot);

    @Inject(method = "full", at = @At("RETURN"), cancellable = true)
    private void neoecoae$allowBuffersWithoutCountedInputs(CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValue()) return;
        for (int slot = 0; slot < i; slot++) if (itemCounted(slot)) return;
        for (int slot = 0; slot < f; slot++) if (fluidCounted(slot)) return;
        result.setReturnValue(false);
    }
}
