package cn.dancingsnow.neoecoae.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageRecoveryTerminal;
import cn.dancingsnow.neoecoae.item.ItemECOStorageRecoveryTerminal;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server-authoritative UUID selection for the storage recovery terminal UI. */
public class PacketStorageRecoveryTerminalAction implements IMessage {

    private int itemSlot;
    private int action;

    public PacketStorageRecoveryTerminalAction() {}

    public PacketStorageRecoveryTerminalAction(int itemSlot, Action action) {
        this.itemSlot = itemSlot;
        this.action = action.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.itemSlot = buffer.readByte();
        this.action = buffer.readByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(this.itemSlot);
        buffer.writeByte(this.action);
    }

    public enum Action {
        PREVIOUS,
        NEXT
    }

    public static class Handler implements IMessageHandler<PacketStorageRecoveryTerminalAction, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageRecoveryTerminalAction message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerMainThreadScheduler.schedule(new Runnable() {

                @Override
                public void run() {
                    handle(player, message.itemSlot, message.action);
                }
            });
            return null;
        }

        private static void handle(EntityPlayerMP player, int itemSlot, int actionOrdinal) {
            if (player == null || !(player.openContainer instanceof ContainerECOStorageRecoveryTerminal)) {
                return;
            }
            if (itemSlot < 0 || itemSlot >= player.inventory.mainInventory.length) {
                return;
            }
            ItemStack stack = player.inventory.getStackInSlot(itemSlot);
            if (stack == null || !(stack.getItem() instanceof ItemECOStorageRecoveryTerminal)) {
                return;
            }
            Action[] actions = Action.values();
            if (actionOrdinal < 0 || actionOrdinal >= actions.length) {
                return;
            }
            int delta = actions[actionOrdinal] == Action.PREVIOUS ? -1 : 1;
            java.util.UUID selected = ItemECOStorageRecoveryTerminal.cycleSelectedDomain(stack, player.worldObj, delta);
            if (selected == null) {
                player.addChatMessage(new ChatComponentTranslation("chat.neoecoae.storage.recovery.no_domains"));
                return;
            }
            player.inventory.markDirty();
            player.openContainer.detectAndSendChanges();
        }
    }
}
