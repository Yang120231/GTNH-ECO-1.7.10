package cn.dancingsnow.neoecoae.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.ClientPatternHighlightHandler;
import cn.dancingsnow.neoecoae.client.ClientPatternUploadTooltipHandler;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTermUploadExtension;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public final class NEPatternUploadNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(NeoECOAE.MODID);
    private static boolean registered;
    private static String clientRouteMapId = "";
    private static net.minecraft.item.ItemStack clientRouteCircuit;

    private NEPatternUploadNetwork() {}

    public static void register() {
        if (registered) return;
        CHANNEL.registerMessage(PrepareHandler.class, PrepareMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(OpenHandler.class, OpenMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(ClientPatternHighlightHandler.class, HighlightMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(ClientPatternUploadTooltipHandler.class, AutoTargetMessage.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(RouteContextHandler.class, RouteContextMessage.class, 4, Side.SERVER);
        registered = true;
    }

    public static void requestPrepare() {
        CHANNEL.sendToServer(new PrepareMessage(clientRouteMapId, clientRouteCircuit));
    }

    public static void requestOpenUpload(boolean autoUpload) {
        CHANNEL.sendToServer(new OpenMessage(autoUpload, clientRouteMapId, clientRouteCircuit));
    }

    public static void requestRouteContext(String recipeMapId) {
        requestRouteContext(recipeMapId, null);
    }

    public static void requestRouteContext(String recipeMapId, net.minecraft.item.ItemStack circuit) {
        if (recipeMapId == null || recipeMapId.trim()
            .isEmpty()) return;
        String value = recipeMapId.trim();
        if (value.length() > 128) value = value.substring(0, 128);
        clientRouteMapId = value;
        clientRouteCircuit = circuit == null ? null : circuit.copy();
        CHANNEL.sendToServer(new RouteContextMessage(value, circuit));
    }

    public static void clearRouteContext() {
        clientRouteMapId = "";
        clientRouteCircuit = null;
        CHANNEL.sendToServer(new RouteContextMessage("", null));
    }

    public static void showHighlight(EntityPlayerMP player, PatternUploadTarget target) {
        if (player == null || target == null) return;
        showHighlight(player, target.getDimension(), target.getX(), target.getY(), target.getZ());
    }

    public static void showHighlight(EntityPlayerMP player, int dimension, int x, int y, int z) {
        if (player == null) return;
        CHANNEL.sendTo(new HighlightMessage(dimension, x, y, z), player);
    }

    public static void sendAutoTarget(EntityPlayerMP player, PatternUploadTarget target) {
        sendAutoTarget(player, target, target == null ? null : target.getCircuit());
    }

    public static void sendAutoTarget(EntityPlayerMP player, PatternUploadTarget target,
        net.minecraft.item.ItemStack circuit) {
        sendAutoTarget(player, target, circuit, target == null ? "" : target.getTooltipName());
    }

    public static void sendAutoTarget(EntityPlayerMP player, PatternUploadTarget target,
        net.minecraft.item.ItemStack circuit, String displayName) {
        if (player == null) return;
        CHANNEL.sendTo(
            new AutoTargetMessage(
                target == null ? "" : displayName,
                target == null ? -1
                    : target.getKind()
                        .ordinal(),
                circuit),
            player);
    }

    public static final class PrepareMessage implements IMessage {

        private String recipeMapId = "";
        private net.minecraft.item.ItemStack circuit;

        public PrepareMessage() {}

        private PrepareMessage(String recipeMapId, net.minecraft.item.ItemStack circuit) {
            this.recipeMapId = recipeMapId == null ? "" : recipeMapId;
            this.circuit = circuit == null ? null : circuit.copy();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.recipeMapId = ByteBufUtils.readUTF8String(buf);
            if (this.recipeMapId == null) this.recipeMapId = "";
            if (this.recipeMapId.length() > 128) this.recipeMapId = this.recipeMapId.substring(0, 128);
            this.circuit = ByteBufUtils.readItemStack(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.recipeMapId == null ? "" : this.recipeMapId);
            ByteBufUtils.writeItemStack(buf, this.circuit);
        }
    }

    public static final class OpenMessage implements IMessage {

        private boolean autoUpload;
        private String recipeMapId = "";
        private net.minecraft.item.ItemStack circuit;

        public OpenMessage() {}

        public OpenMessage(boolean autoUpload) {
            this(autoUpload, "", null);
        }

        private OpenMessage(boolean autoUpload, String recipeMapId, net.minecraft.item.ItemStack circuit) {
            this.autoUpload = autoUpload;
            this.recipeMapId = recipeMapId == null ? "" : recipeMapId;
            this.circuit = circuit == null ? null : circuit.copy();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.autoUpload = buf.readBoolean();
            this.recipeMapId = ByteBufUtils.readUTF8String(buf);
            if (this.recipeMapId == null) this.recipeMapId = "";
            if (this.recipeMapId.length() > 128) this.recipeMapId = this.recipeMapId.substring(0, 128);
            this.circuit = ByteBufUtils.readItemStack(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(this.autoUpload);
            ByteBufUtils.writeUTF8String(buf, this.recipeMapId == null ? "" : this.recipeMapId);
            ByteBufUtils.writeItemStack(buf, this.circuit);
        }
    }

    public static final class RouteContextMessage implements IMessage {

        private String recipeMapId = "";
        private net.minecraft.item.ItemStack circuit;

        public RouteContextMessage() {}

        private RouteContextMessage(String recipeMapId) {
            this(recipeMapId, null);
        }

        private RouteContextMessage(String recipeMapId, net.minecraft.item.ItemStack circuit) {
            this.recipeMapId = recipeMapId == null ? "" : recipeMapId;
            this.circuit = circuit == null ? null : circuit.copy();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.recipeMapId = ByteBufUtils.readUTF8String(buf);
            if (this.recipeMapId == null) this.recipeMapId = "";
            if (this.recipeMapId.length() > 128) this.recipeMapId = this.recipeMapId.substring(0, 128);
            this.circuit = ByteBufUtils.readItemStack(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.recipeMapId == null ? "" : this.recipeMapId);
            ByteBufUtils.writeItemStack(buf, this.circuit);
        }
    }

    public static final class PrepareHandler implements IMessageHandler<PrepareMessage, IMessage> {

        @Override
        public IMessage onMessage(PrepareMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final String recipeMapId = message == null ? "" : message.recipeMapId;
            final net.minecraft.item.ItemStack circuit = message == null || message.circuit == null ? null
                : message.circuit.copy();
            ServerThreadUtil.addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerPatternTerm
                    && player.openContainer instanceof PatternTermUploadExtension) {
                    PatternTermUploadExtension extension = (PatternTermUploadExtension) player.openContainer;
                    extension.neoecoae$setRouteContext(recipeMapId, circuit);
                    extension.neoecoae$encodeAndPrepareUpload();
                }
            });
            return null;
        }
    }

    public static final class HighlightMessage implements IMessage {

        private int dimension;
        private int x;
        private int y;
        private int z;

        public HighlightMessage() {}

        public HighlightMessage(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getDimension() {
            return this.dimension;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public int getZ() {
            return this.z;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.dimension = buf.readInt();
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.dimension);
            buf.writeInt(this.x);
            buf.writeInt(this.y);
            buf.writeInt(this.z);
        }
    }

    public static final class AutoTargetMessage implements IMessage {

        private String name = "";
        private int kind = -1;
        private net.minecraft.item.ItemStack circuit;

        public AutoTargetMessage() {}

        private AutoTargetMessage(String name, int kind, net.minecraft.item.ItemStack circuit) {
            this.name = name == null ? "" : name;
            this.kind = kind;
            this.circuit = circuit == null ? null : circuit.copy();
        }

        public String getName() {
            return this.name == null ? "" : this.name;
        }

        public int getKind() {
            return this.kind;
        }

        public net.minecraft.item.ItemStack getCircuit() {
            return this.circuit == null ? null : this.circuit.copy();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.name = ByteBufUtils.readUTF8String(buf);
            this.kind = buf.readInt();
            this.circuit = ByteBufUtils.readItemStack(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.name == null ? "" : this.name);
            buf.writeInt(this.kind);
            ByteBufUtils.writeItemStack(buf, this.circuit);
        }
    }

    public static final class OpenHandler implements IMessageHandler<OpenMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final String recipeMapId = message == null ? "" : message.recipeMapId;
            final net.minecraft.item.ItemStack circuit = message == null || message.circuit == null ? null
                : message.circuit.copy();
            ServerThreadUtil.addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerPatternTerm
                    && player.openContainer instanceof PatternTermUploadExtension) {
                    PatternTermUploadExtension extension = (PatternTermUploadExtension) player.openContainer;
                    extension.neoecoae$setRouteContext(recipeMapId, circuit);
                    extension.neoecoae$openUpload(message.autoUpload);
                }
            });
            return null;
        }
    }

    public static final class RouteContextHandler implements IMessageHandler<RouteContextMessage, IMessage> {

        @Override
        public IMessage onMessage(RouteContextMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            final String recipeMapId = message == null ? "" : message.recipeMapId;
            final net.minecraft.item.ItemStack circuit = message == null || message.circuit == null ? null
                : message.circuit.copy();
            ServerThreadUtil.addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerPatternTerm
                    && player.openContainer instanceof PatternTermUploadExtension) {
                    ((PatternTermUploadExtension) player.openContainer).neoecoae$setRouteContext(recipeMapId, circuit);
                }
            });
            return null;
        }
    }
}
