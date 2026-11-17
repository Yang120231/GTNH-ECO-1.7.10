package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.crafting.v2.CraftingJobV2;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.NativeCraftingCalculationTime;

/** AE2's incremental planner can calculate over several simulation ticks. */
@Mixin(value = CraftingJobV2.class, remap = false)
public abstract class NativeCraftingCalculationTimeMixin implements NativeCraftingCalculationTime {

    @Unique
    private long neoecoae$started;
    @Unique
    private long neoecoae$elapsed;

    @Inject(method = "simulateFor", at = @At("HEAD"), require = 1)
    private void neoecoae$startTiming(int milli, CallbackInfoReturnable<Boolean> cir) {
        neoecoae$started = System.nanoTime();
    }

    @Inject(method = "simulateFor", at = @At("RETURN"), require = 1)
    private void neoecoae$finishTiming(int milli, CallbackInfoReturnable<Boolean> cir) {
        neoecoae$elapsed += System.nanoTime() - neoecoae$started;
    }

    @Override
    public long neoecoae$calculationNanos() {
        return neoecoae$elapsed;
    }
}
