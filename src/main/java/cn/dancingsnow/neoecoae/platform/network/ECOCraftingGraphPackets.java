package cn.dancingsnow.neoecoae.platform.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingGraphSync;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Crafting graph protocol, separate from the confirmation report channel. The client asks for the graph of the plan
 * it shows (window id plus report version); the server answers with a status and, when ready, the encoded snapshot
 * in ordered chunks tagged with the same window and version.
 */
public final class ECOCraftingGraphPackets {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("neoecoae_graph");
    private static boolean registered;

    private ECOCraftingGraphPackets() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ChunkHandler.class, Chunk.class, 1, Side.CLIENT);
    }

    public static void request(int windowId, long version) {
        CHANNEL.sendToServer(new Request(windowId, version));
    }

    public static void send(EntityPlayerMP player, Chunk chunk) {
        CHANNEL.sendTo(chunk, player);
    }

    /** Client to server: send the graph of the plan behind this confirmation window. */
    public static final class Request implements IMessage {

        int windowId;
        long version;

        public Request() {}

        Request(int windowId, long version) {
            this.windowId = windowId;
            this.version = version;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            version = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeLong(version);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(() -> ECOCraftingGraphSync.request(player, message.windowId, message.version));
            return null;
        }
    }

    /** Server to client: one part of a graph transfer, or a bodiless status answer. */
    public static final class Chunk implements IMessage {

        public int windowId;
        public long version;
        public long transfer;
        public ECOCraftingGraphStream.Status status = ECOCraftingGraphStream.Status.FAILED;
        public int total;
        public int offset;
        public byte[] bytes = new byte[0];

        public Chunk() {}

        public Chunk(int windowId, long version, long transfer, ECOCraftingGraphStream.Status status, int total,
            int offset, byte[] bytes) {
            this.windowId = windowId;
            this.version = version;
            this.transfer = transfer;
            this.status = status;
            this.total = total;
            this.offset = offset;
            this.bytes = bytes;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            version = buf.readLong();
            transfer = buf.readLong();
            status = ECOCraftingGraphStream.Status.byId(buf.readUnsignedByte());
            total = buf.readInt();
            offset = buf.readInt();
            int length = buf.readInt();
            if (length < 0 || length > ECOCraftingGraphStream.CHUNK_BYTES || length > buf.readableBytes())
                throw new IllegalArgumentException("Invalid crafting graph chunk length " + length);
            bytes = new byte[length];
            buf.readBytes(bytes);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeLong(version);
            buf.writeLong(transfer);
            buf.writeByte(status.ordinal());
            buf.writeInt(total);
            buf.writeInt(offset);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static final class ChunkHandler implements IMessageHandler<Chunk, IMessage> {

        @Override
        public IMessage onMessage(Chunk message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftingGraphClient.accept(message);
            return null;
        }
    }
}
