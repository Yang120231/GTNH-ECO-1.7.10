package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;

/** Modern menu broadcast includes the carried stack; 1.7 change detection omits it for custom RPCs. */
public final class NativePlayerInventory {

    private NativePlayerInventory() {}

    public static void syncMenu(EntityPlayer player) {
        player.openContainer.detectAndSendChanges();
        if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).playerNetServerHandler
            .sendPacket(new S2FPacketSetSlot(-1, -1, ItemStack.copyItemStack(player.inventory.getItemStack())));
    }
}
