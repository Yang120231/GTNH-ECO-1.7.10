package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.factory.PosGuiData;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECORemoteGuiOpener;

/** Lifts ModularUI2's 8-block reach only for a tile GUI the crafting status menu opened remotely. */
@Mixin(value = com.cleanroommc.modularui.factory.TileEntityGuiFactory.class, remap = false)
public abstract class RemoteTileGuiReachMixin {

    @Inject(
        method = "canInteractWith(Lnet/minecraft/entity/player/EntityPlayer;Lcom/cleanroommc/modularui/factory/PosGuiData;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void neoecoae$remoteReach(EntityPlayer player, PosGuiData data, CallbackInfoReturnable<Boolean> cir) {
        if (ECORemoteGuiOpener.isRemoteTarget(player, data.getWorld(), data.getX(), data.getY(), data.getZ()))
            cir.setReturnValue(true);
    }
}
