package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.NativeCraftingCalculationTime;

/** Lite planning happens synchronously in schedule(); later simulation ticks only deliver its callback. */
@Pseudo
@Mixin(targets = "appeng.crafting.fast.CraftingJobFast", remap = false)
public abstract class NativeLiteCalculationTimeMixin implements NativeCraftingCalculationTime {

    @Unique
    private long neoecoae$started;
    @Unique
    private long neoecoae$elapsed = -1;

    @Inject(method = "calculate", at = @At("HEAD"), require = 1)
    private void neoecoae$startTiming(CallbackInfo ci) {
        if (neoecoae$elapsed < 0) neoecoae$started = System.nanoTime();
    }

    @Inject(method = "calculate", at = @At("RETURN"), require = 1)
    private void neoecoae$finishTiming(CallbackInfo ci) {
        // calculate() is called again to deliver the callback; retain the original calculation's duration.
        if (neoecoae$elapsed < 0) neoecoae$elapsed = System.nanoTime() - neoecoae$started;
    }

    @Override
    public long neoecoae$calculationNanos() {
        return neoecoae$elapsed;
    }
}
