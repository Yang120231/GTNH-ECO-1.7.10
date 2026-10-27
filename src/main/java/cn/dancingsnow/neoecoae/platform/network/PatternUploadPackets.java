package cn.dancingsnow.neoecoae.platform.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.ae2.upload.PatternUploadService;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Pattern upload protocol. Every client request names the terminal window it was sent from; the server ignores it
 * unless that exact window is still open, and runs it on its own tick.
 */
public final class PatternUploadPackets {

    public static final int OPEN = 0, AUTO = 1, ENCODE_AND_OPEN = 2;

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("neoecoae_upload");
    private static boolean registered;
    private static int sequence;

    private PatternUploadPackets() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(HintHandler.class, Hint.class, 1, Side.SERVER);
        CHANNEL.registerMessage(AutoTargetHandler.class, AutoTarget.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(HighlightHandler.class, Highlight.class, 3, Side.CLIENT);
    }

    public static void request(int windowId, int mode) {
        CHANNEL.sendToServer(new Request(windowId, mode, ++sequence));
    }

    public static void hint(int windowId, String recipeMapId, ItemStack circuit) {
        CHANNEL.sendToServer(new Hint(windowId, recipeMapId, circuit));
    }

    public static void autoTarget(EntityPlayerMP player, AutoTarget message) {
        CHANNEL.sendTo(message, player);
    }

    public static void highlight(EntityPlayerMP player, int dimension, int x, int y, int z) {
        CHANNEL.sendTo(new Highlight(dimension, x, y, z), player);
    }

    private static String readString(ByteBuf buf, int limit) {
        String value = ByteBufUtils.readUTF8String(buf);
        if (value == null) return "";
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    public static final class Request implements IMessage {

        int windowId, mode, sequence;

        public Request() {}

        Request(int windowId, int mode, int sequence) {
            this.windowId = windowId;
            this.mode = mode;
            this.sequence = sequence;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            mode = buf.readByte();
            sequence = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeByte(mode);
            buf.writeInt(sequence);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(() -> PatternUploadService.request(player, message.windowId, message.mode, message.sequence));
            return null;
        }
    }

    public static final class Hint implements IMessage {

        int windowId;
        String recipeMapId = "";
        ItemStack circuit;

        public Hint() {}

        Hint(int windowId, String recipeMapId, ItemStack circuit) {
            this.windowId = windowId;
            this.recipeMapId = recipeMapId == null ? "" : recipeMapId;
            this.circuit = circuit == null ? null : circuit.copy();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            recipeMapId = readString(buf, 128);
            circuit = ByteBufUtils.readItemStack(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            ByteBufUtils.writeUTF8String(buf, recipeMapId);
            ByteBufUtils.writeItemStack(buf, circuit);
        }
    }

    public static final class HintHandler implements IMessageHandler<Hint, IMessage> {

        @Override
        public IMessage onMessage(Hint message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(
                    () -> PatternUploadService.hint(player, message.windowId, message.recipeMapId, message.circuit));
            return null;
        }
    }

    /** Server-chosen auto-upload target for one terminal window; an empty name means none. */
    public static final class AutoTarget implements IMessage {

        public int windowId, kind = -1;
        public String name = "", reason = "";
        public ItemStack circuit;
        public boolean programmingCover;

        public AutoTarget() {}

        public AutoTarget(int windowId, String name, int kind, ItemStack circuit, boolean programmingCover,
            String reason) {
            this.windowId = windowId;
            this.name = name == null ? "" : name;
            this.kind = kind;
            this.circuit = circuit == null ? null : circuit.copy();
            this.programmingCover = programmingCover;
            this.reason = reason == null ? "" : reason;
        }

        public String key() {
            return name + "|"
                + kind
                + "|"
                + (circuit == null ? "" : circuit.getItemDamage())
                + "|"
                + programmingCover
                + "|"
                + reason;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            name = readString(buf, 256);
            kind = buf.readByte();
            circuit = ByteBufUtils.readItemStack(buf);
            programmingCover = buf.readBoolean();
            reason = readString(buf, 256);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            ByteBufUtils.writeUTF8String(buf, name);
            buf.writeByte(kind);
            ByteBufUtils.writeItemStack(buf, circuit);
            buf.writeBoolean(programmingCover);
            ByteBufUtils.writeUTF8String(buf, reason);
        }
    }

    public static final class AutoTargetHandler implements IMessageHandler<AutoTarget, IMessage> {

        @Override
        public IMessage onMessage(AutoTarget message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient.autoTarget(message);
            return null;
        }
    }

    public static final class Highlight implements IMessage {

        public int dimension, x, y, z;

        public Highlight() {}

        Highlight(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            dimension = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
        }
    }

    public static final class HighlightHandler implements IMessageHandler<Highlight, IMessage> {

        @Override
        public IMessage onMessage(Highlight message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient
                .highlight(message.dimension, message.x, message.y, message.z);
            return null;
        }
    }
}
