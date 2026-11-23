package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu;

@Pseudo
@Mixin(targets = "reobf.proghatches.ae.CircuitHoistTally", remap = false)
public abstract class ProgrammableCircuitHoistMixin {

    @Redirect(
        method = "deliverInstantly",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;addStorage(Lappeng/api/storage/data/IAEStack;)V"),
        require = 0)
    private static void neoecoae$recordGeneratedCircuits(CraftingCPUCluster cpu, IAEStack<?> stack) {
        NativeECOCpu.addInstantCraftingStorage(cpu, stack);
    }
}
