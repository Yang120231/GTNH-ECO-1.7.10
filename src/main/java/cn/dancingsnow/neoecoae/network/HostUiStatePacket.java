package cn.dancingsnow.neoecoae.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class HostUiStatePacket implements IMessage {

    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private int windowId;
    private long revision;
    private byte[] payload;

    public HostUiStatePacket() {}

    public HostUiStatePacket(int windowId, long revision, byte[] payload) {
        this.windowId = windowId;
        this.revision = revision;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int version = buffer.readUnsignedByte();
        if (version != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported host UI state protocol version: " + version);
        }
        this.windowId = buffer.readInt();
        this.revision = buffer.readLong();
        int length = buffer.readInt();
        if (length < 0 || length > MAX_PAYLOAD_BYTES || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid host UI state payload length: " + length);
        }
        this.payload = new byte[length];
        buffer.readBytes(this.payload);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        byte[] safePayload = this.payload == null ? new byte[0] : this.payload;
        buffer.writeByte(PROTOCOL_VERSION);
        buffer.writeInt(this.windowId);
        buffer.writeLong(this.revision);
        buffer.writeInt(safePayload.length);
        buffer.writeBytes(safePayload);
    }

    public int windowId() {
        return this.windowId;
    }

    public long revision() {
        return this.revision;
    }

    public byte[] payload() {
        return this.payload == null ? new byte[0] : this.payload.clone();
    }

    public static final class Handler implements IMessageHandler<HostUiStatePacket, IMessage> {

        @Override
        public IMessage onMessage(HostUiStatePacket message, MessageContext ctx) {
            ClientHostUiPacketBridge.handle(message);
            return null;
        }
    }
}
