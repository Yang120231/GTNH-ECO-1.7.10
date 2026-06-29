package cn.dancingsnow.neoecoae.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketComputationHostAction implements IMessage {

    private int x;
    private int y;
    private int z;
    private int action;

    public PacketComputationHostAction() {}

    public PacketComputationHostAction(TileECOController controller, Action action) {
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
        CYCLE_CPU_MODE
    }

    public static class Handler implements IMessageHandler<PacketComputationHostAction, IMessage> {

        @Override
        public IMessage onMessage(PacketComputationHostAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final int action = message.action;
            ServerMainThreadScheduler.schedule(new Runnable() {

                @Override
                public void run() {
                    handle(player, x, y, z, action);
                }
            });
            return null;
        }

        private static void handle(EntityPlayerMP player, int x, int y, int z, int action) {
            if (player == null) {
                return;
            }
            World world = player.worldObj;
            if (world == null) {
                return;
            }
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!(tile instanceof TileECOController)) {
                return;
            }
            TileECOController controller = (TileECOController) tile;
            if (controller.getSubsystem() != ECOControllerSubsystem.COMPUTATION
                || !controller.isUseableByPlayer(player)) {
                return;
            }
            if (action == Action.CYCLE_CPU_MODE.ordinal()) {
                controller.cycleComputationCpuSelectionMode();
            }
        }
    }
}
