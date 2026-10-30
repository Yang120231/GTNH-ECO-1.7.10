package cn.dancingsnow.neoecoae.platform.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmReport;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmState;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmTracker;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * ECO crafting report protocol. The server mirrors the confirmation container's ECO state to the client; the client
 * names the confirmation window for every start request, and the server ignores it unless that window is still open.
 */
public final class ECOCraftConfirmPackets {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("neoecoae_confirm");
    private static boolean registered;

    private ECOCraftConfirmPackets() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(SyncHandler.class, Sync.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(StartHandler.class, Start.class, 1, Side.SERVER);
        CHANNEL.registerMessage(ClearErrorHandler.class, ClearError.class, 2, Side.SERVER);
        CHANNEL.registerMessage(BigOrderStartHandler.class, BigOrderStart.class, 3, Side.SERVER);
    }

    public static void sync(EntityPlayerMP player, int windowId, ECOCraftConfirmState state,
        ECOCraftConfirmReport report) {
        CHANNEL.sendTo(new Sync(windowId, state, report), player);
    }

    public static void start(int windowId, boolean forced, boolean follow) {
        CHANNEL.sendToServer(new Start(windowId, forced, follow));
    }

    public static void clearError(int windowId) {
        CHANNEL.sendToServer(new ClearError(windowId));
    }

    public static void startBigOrder(int windowId, boolean forced) {
        CHANNEL.sendToServer(new BigOrderStart(windowId, forced));
    }

    /** Server to client: the ECO header, plus the material/cycle report when it changed. */
    public static final class Sync implements IMessage {

        public int windowId;
        public ECOCraftConfirmState state = ECOCraftConfirmState.NONE;
        /** {@code null} when the client already holds the report of {@link ECOCraftConfirmState#reportVersion}. */
        public ECOCraftConfirmReport report;

        public Sync() {}

        Sync(int windowId, ECOCraftConfirmState state, ECOCraftConfirmReport report) {
            this.windowId = windowId;
            this.state = state;
            this.report = report;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            windowId = data.readVarInt();
            state = ECOCraftConfirmState.read(data);
            report = data.readBoolean() ? ECOCraftConfirmReport.read(data) : null;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            data.writeVarInt(windowId);
            state.write(data);
            data.writeBoolean(report != null);
            if (report != null) report.write(data);
        }
    }

    public static final class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftConfirmClient
                .accept(message.windowId, message.state, message.report);
            return null;
        }
    }

    /** Client to server: start the displayed plan, optionally forcing a simulation plan. */
    public static final class Start implements IMessage {

        int windowId;
        boolean forced;
        /** Subscribe the player to the completion message, like GTNH's "Start and follow". */
        boolean follow;

        public Start() {}

        Start(int windowId, boolean forced, boolean follow) {
            this.windowId = windowId;
            this.forced = forced;
            this.follow = follow;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            forced = buf.readBoolean();
            follow = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeBoolean(forced);
            buf.writeBoolean(follow);
        }
    }

    public static final class StartHandler implements IMessageHandler<Start, IMessage> {

        @Override
        public IMessage onMessage(Start message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(() -> ECOCraftConfirmTracker.start(player, message.windowId, message.forced, message.follow));
            return null;
        }
    }

    /** Client to server: the player dismissed the error page. */
    public static final class ClearError implements IMessage {

        int windowId;

        public ClearError() {}

        ClearError(int windowId) {
            this.windowId = windowId;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
        }
    }

    public static final class ClearErrorHandler implements IMessageHandler<ClearError, IMessage> {

        @Override
        public IMessage onMessage(ClearError message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(() -> ECOCraftConfirmTracker.clearError(player, message.windowId));
            return null;
        }
    }

    /**
     * Client to server: hand the displayed plan to an ECO CPU as a big order (1.21's ECOBigOrderStartC2SPacket).
     * It carries intent only; the server resolves the result, admission and CPU from the open confirmation.
     */
    public static final class BigOrderStart implements IMessage {

        int windowId;
        boolean forced;

        public BigOrderStart() {}

        BigOrderStart(int windowId, boolean forced) {
            this.windowId = windowId;
            this.forced = forced;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            forced = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeBoolean(forced);
        }
    }

    public static final class BigOrderStartHandler implements IMessageHandler<BigOrderStart, IMessage> {

        @Override
        public IMessage onMessage(BigOrderStart message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(() -> ECOCraftConfirmTracker.startBigOrder(player, message.windowId, message.forced));
            return null;
        }
    }
}
