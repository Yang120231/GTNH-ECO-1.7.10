package cn.dancingsnow.neoecoae.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.gui.container.ContainerECOStructureTerminal;
import cn.dancingsnow.neoecoae.item.ItemECOStructureTerminal;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server-authoritative configuration actions for the held structure terminal. */
public class PacketStructureTerminalAction implements IMessage {

    private int itemSlot;
    private int action;

    public PacketStructureTerminalAction() {}

    public PacketStructureTerminalAction(int itemSlot, Action action) {
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
        SELECT_CRAFTING,
        SELECT_STORAGE,
        SELECT_COMPUTATION,
        SELECT_TIER_1,
        SELECT_TIER_2,
        SELECT_TIER_3,
        INCREASE,
        DECREASE,
        BUILD_LINKED,
        BUILD_MIRRORED_LINKED,
        DISMANTLE_LINKED
    }

    public static class Handler implements IMessageHandler<PacketStructureTerminalAction, IMessage> {

        @Override
        public IMessage onMessage(PacketStructureTerminalAction message, MessageContext context) {
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
            if (player == null || !(player.openContainer instanceof ContainerECOStructureTerminal)) {
                return;
            }
            if (itemSlot < 0 || itemSlot >= player.inventory.mainInventory.length) {
                return;
            }
            ItemStack stack = player.inventory.getStackInSlot(itemSlot);
            if (stack == null || !(stack.getItem() instanceof ItemECOStructureTerminal)) {
                return;
            }
            Action[] actions = Action.values();
            if (actionOrdinal < 0 || actionOrdinal >= actions.length) {
                return;
            }
            switch (actions[actionOrdinal]) {
                case SELECT_CRAFTING:
                    ItemECOStructureTerminal.setHostType(stack, StructureTerminalHostType.CRAFTING);
                    break;
                case SELECT_STORAGE:
                    ItemECOStructureTerminal.setHostType(stack, StructureTerminalHostType.STORAGE);
                    break;
                case SELECT_COMPUTATION:
                    ItemECOStructureTerminal.setHostType(stack, StructureTerminalHostType.COMPUTATION);
                    break;
                case SELECT_TIER_1:
                    ItemECOStructureTerminal.setHostTier(stack, "l4");
                    break;
                case SELECT_TIER_2:
                    ItemECOStructureTerminal.setHostTier(stack, "l6");
                    break;
                case SELECT_TIER_3:
                    ItemECOStructureTerminal.setHostTier(stack, "l9");
                    break;
                case INCREASE:
                    ItemECOStructureTerminal.setBuildLength(stack, ItemECOStructureTerminal.getBuildLength(stack) + 1);
                    break;
                case DECREASE:
                    ItemECOStructureTerminal.setBuildLength(stack, ItemECOStructureTerminal.getBuildLength(stack) - 1);
                    break;
                case BUILD_LINKED:
                    ItemECOStructureTerminal.setOperationMode(stack, StructureTerminalMode.BUILD);
                    break;
                case BUILD_MIRRORED_LINKED:
                    ItemECOStructureTerminal.setOperationMode(stack, StructureTerminalMode.MIRRORED_BUILD);
                    break;
                case DISMANTLE_LINKED:
                    ItemECOStructureTerminal.setOperationMode(stack, StructureTerminalMode.DISMANTLE);
                    break;
                default:
                    return;
            }
            player.inventory.markDirty();
            player.openContainer.detectAndSendChanges();
        }
    }
}
