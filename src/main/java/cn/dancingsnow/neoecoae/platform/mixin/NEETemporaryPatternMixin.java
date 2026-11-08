package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.vfyjxf.nee.block.tile.TilePatternInterface;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import cn.dancingsnow.neoecoae.compat.nei.NEETemporaryCrafting;

/** Persist NEE's existing cleanup flags, including cancellation before the first physical pattern push. */
@Mixin(value = TilePatternInterface.class, remap = false)
public abstract class NEETemporaryPatternMixin implements NEETemporaryCrafting.PatternLifecycle {

    @Shadow
    @Final
    private boolean[] workStarted;
    @Shadow
    @Final
    private AppEngInternalInventory patterns;

    @Override
    public void neoecoae$deferCleanup(int slot) {
        if (slot < 0 || slot >= workStarted.length || patterns.getStackInSlot(slot) == null) return;
        workStarted[slot] = true;
        ((TilePatternInterface) (Object) this).markDirty();
    }

    @Inject(method = "onChangeInventory", at = @At("HEAD"), require = 1)
    private void neoecoae$resetReplacedPattern(IInventory inventory, int slot, InvOperation operation,
        ItemStack removed, ItemStack added, CallbackInfo ci) {
        if (inventory == patterns && slot >= 0 && slot < workStarted.length && removed != added)
            workStarted[slot] = false;
    }

    @Inject(method = "writeToNBT_TilePatternInterface", at = @At("RETURN"), require = 1)
    private void neoecoae$saveCleanup(NBTTagCompound data, CallbackInfoReturnable<NBTTagCompound> cir) {
        byte[] flags = new byte[workStarted.length];
        for (int i = 0; i < flags.length; i++) flags[i] = (byte) (workStarted[i] ? 1 : 0);
        cir.getReturnValue()
            .setByteArray("neoecoaeCleanup", flags);
    }

    @Inject(method = "readFromNBT_TilePatternInterface", at = @At("TAIL"), require = 1)
    private void neoecoae$loadCleanup(NBTTagCompound data, CallbackInfo ci) {
        byte[] flags = data.getByteArray("neoecoaeCleanup");
        for (int i = 0; i < workStarted.length; i++)
            workStarted[i] = i < flags.length && flags[i] != 0 && patterns.getStackInSlot(i) != null;
    }
}
