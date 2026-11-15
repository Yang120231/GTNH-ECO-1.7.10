package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import cn.dancingsnow.neoecoae.platform.gt.NativeProcessingMachines;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Installed only with GregTech; the native structure check remains the sole authority on membership. */
@Mixin(targets = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase", remap = false)
public abstract class GTProcessingControllerMixin {

    @Inject(
        method = "checkStructure(ZLgregtech/api/interfaces/tileentity/IGregTechTileEntity;)Z",
        at = @At("RETURN"),
        require = 0)
    private void neoecoae$observeInputs(boolean force, IGregTechTileEntity tile,
        CallbackInfoReturnable<Boolean> callback) {
        if (tile instanceof TileEntity)
            NativeProcessingMachines.observeGregTechController(this, (TileEntity) tile, callback.getReturnValueZ());
    }

    /** TecTech controllers use this final structure check instead of checkStructure. */
    @Inject(
        method = "checkMachine_TT(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;)Z",
        at = @At("RETURN"),
        require = 0)
    private void neoecoae$observeInputsTecTech(IGregTechTileEntity tile, CallbackInfoReturnable<Boolean> callback) {
        if (tile instanceof TileEntity)
            NativeProcessingMachines.observeGregTechController(this, (TileEntity) tile, callback.getReturnValueZ());
    }

    @Inject(method = "checkRecipe()Z", at = @At("RETURN"), require = 0)
    private void neoecoae$observeRecipe(CallbackInfoReturnable<Boolean> callback) {
        NativeGregTech.recipeChecked(this, callback.getReturnValueZ());
    }

    @Inject(method = "loadNBTData(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"), require = 0)
    private void neoecoae$observeLoadedRecipe(NBTTagCompound tag, CallbackInfo callback) {
        NativeGregTech.recipeLoaded(this);
    }
}
