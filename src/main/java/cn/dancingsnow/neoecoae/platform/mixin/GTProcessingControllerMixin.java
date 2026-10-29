package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import cn.dancingsnow.neoecoae.platform.gt.NativeProcessingMachines;

/** Installed only with GregTech; the native structure check remains the sole authority on membership. */
@Mixin(targets = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase", remap = false)
public abstract class GTProcessingControllerMixin {

    @Inject(
        method = "checkStructure(ZLgregtech/api/interfaces/tileentity/IGregTechTileEntity;)Z",
        at = @At("RETURN"),
        require = 0)
    private void neoecoae$observeInputs(boolean force, IGregTechTileEntity tile, CallbackInfoReturnable<Boolean> callback) {
        if (tile instanceof TileEntity) NativeProcessingMachines.observeController(this, (TileEntity) tile, callback.getReturnValueZ());
    }
}
