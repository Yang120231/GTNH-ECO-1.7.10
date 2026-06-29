package cn.dancingsnow.neoecoae.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.NEGuiIds;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public class PacketStorageHostAction implements IMessage {

    private int x;
    private int y;
    private int z;
    private int action;

    public PacketStorageHostAction() {}

    public PacketStorageHostAction(TileECOController controller, Action action) {
        this.x = controller.xCoord;
        this.y = controller.yCoord;
        this.z = controller.zCoord;
        this.action = action.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeByte(this.action);
    }

    public enum Action {
        OPEN_PRIORITY,
        OPEN_STORAGE
    }

    public static class Handler implements IMessageHandler<PacketStorageHostAction, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageHostAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            TileEntity tile = world.getTileEntity(message.x, message.y, message.z);
            if (!(tile instanceof TileECOController)) {
                return null;
            }
            TileECOController controller = (TileECOController) tile;
            if (controller.getSubsystem() != ECOControllerSubsystem.STORAGE || !controller.isUseableByPlayer(player)) {
                return null;
            }
            if (message.action == Action.OPEN_PRIORITY.ordinal()) {
                if (controller.isFormed()) {
                    player.openGui(
                        NeoECOAE.instance,
                        NEGuiIds.ECO_STORAGE_PRIORITY,
                        world,
                        controller.xCoord,
                        controller.yCoord,
                        controller.zCoord);
                }
            } else if (message.action == Action.OPEN_STORAGE.ordinal()) {
                player.openGui(
                    NeoECOAE.instance,
                    NEGuiIds.ECO_STORAGE_CONTROLLER,
                    world,
                    controller.xCoord,
                    controller.yCoord,
                    controller.zCoord);
            }
            return null;
        }
    }
}
