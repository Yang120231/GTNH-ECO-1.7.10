package cn.dancingsnow.neoecoae.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.storage.domain.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketStorageInterfaceAction implements IMessage {

    private int x;
    private int y;
    private int z;
    private int mode;

    public PacketStorageInterfaceAction() {}

    public PacketStorageInterfaceAction(TileECOInterface storageInterface, ECOStorageInterfaceMode mode) {
        this.x = storageInterface.xCoord;
        this.y = storageInterface.yCoord;
        this.z = storageInterface.zCoord;
        this.mode = mode.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.mode = buffer.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.mode);
    }

    public static class Handler implements IMessageHandler<PacketStorageInterfaceAction, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageInterfaceAction message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final int mode = message.mode;
            ServerMainThreadScheduler.schedule(new Runnable() {

                @Override
                public void run() {
                    handle(player, x, y, z, mode);
                }
            });
            return null;
        }

        private static void handle(EntityPlayerMP player, int x, int y, int z, int mode) {
            if (player == null || mode < 0 || mode >= ECOStorageInterfaceMode.values().length) {
                return;
            }
            World world = player.worldObj;
            TileEntity tile = world == null ? null : world.getTileEntity(x, y, z);
            if (!(tile instanceof TileECOInterface)) {
                return;
            }
            TileECOInterface storageInterface = (TileECOInterface) tile;
            if (storageInterface.getSubsystem() != ECOControllerSubsystem.STORAGE
                || player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D) {
                return;
            }
            storageInterface.setStorageInterfaceMode(ECOStorageInterfaceMode.byOrdinal(mode));
        }
    }
}
