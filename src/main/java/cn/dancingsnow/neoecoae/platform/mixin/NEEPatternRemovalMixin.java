package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.vfyjxf.nee.network.packet.PacketCraftingRequest;

import appeng.api.networking.IGrid;
import appeng.tile.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.compat.nei.NEETemporaryCrafting;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

@Mixin(value = PacketCraftingRequest.Handler.class, remap = false)
public abstract class NEEPatternRemovalMixin {

    @Inject(
        method = "onMessage(Lcom/github/vfyjxf/nee/network/packet/PacketCraftingRequest;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Lcpw/mods/fml/common/network/simpleimpl/IMessage;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void neoecoae$serverThread(PacketCraftingRequest message, MessageContext context,
        CallbackInfoReturnable<IMessage> cir) {
        if (ServerTasks.isServerThread()) return;
        ServerTasks.captureSession()
            .execute(() -> ((PacketCraftingRequest.Handler) (Object) this).onMessage(message, context));
        cir.setReturnValue(null);
    }

    @Group(name = "neoecoae$patternRemoval", min = 1, max = 1)
    @Redirect(
        method = "removeRecipePattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/tile/inventory/AppEngInternalInventory;setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V"),
        require = 0)
    private void neoecoae$keepRunningPattern(AppEngInternalInventory inventory, int slot, ItemStack replacement,
        IGrid grid, ItemStack result, NBTTagCompound pattern, EntityPlayer player) {
        neoecoae$remove(inventory, slot, replacement, grid);
    }

    // Minecraft's inventory method has its SRG name in the installed NEE jar, even though the target mod is unmapped.
    @Group(name = "neoecoae$patternRemoval", min = 1, max = 1)
    @Redirect(
        method = "removeRecipePattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/tile/inventory/AppEngInternalInventory;func_70299_a(ILnet/minecraft/item/ItemStack;)V"),
        require = 0)
    private void neoecoae$keepRunningPatternSrg(AppEngInternalInventory inventory, int slot, ItemStack replacement,
        IGrid grid, ItemStack result, NBTTagCompound pattern, EntityPlayer player) {
        neoecoae$remove(inventory, slot, replacement, grid);
    }

    @Unique
    private static void neoecoae$remove(AppEngInternalInventory inventory, int slot, ItemStack replacement,
        IGrid grid) {
        if (replacement == null && NEETemporaryCrafting.deferPatternRemoval(grid, inventory, slot)) return;
        inventory.setInventorySlotContents(slot, replacement);
    }
}
