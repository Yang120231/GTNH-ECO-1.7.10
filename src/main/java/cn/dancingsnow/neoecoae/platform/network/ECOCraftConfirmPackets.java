package cn.dancingsnow.neoecoae.platform.network;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmReport;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmState;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmTracker;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingStatusTracker;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
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
        CHANNEL.registerMessage(StatusSyncHandler.class, StatusSync.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(StatusProcessingHandler.class, StatusProcessing.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(ProcessingDetailHandler.class, ProcessingDetail.class, 6, Side.SERVER);
        CHANNEL.registerMessage(ItemActionHandler.class, ItemAction.class, 7, Side.SERVER);
        CHANNEL.registerMessage(ItemActionResultHandler.class, ItemActionResult.class, 8, Side.CLIENT);
    }

    public static void sync(EntityPlayerMP player, int windowId, ECOCraftConfirmState state,
        ECOCraftConfirmReport report) {
        CHANNEL.sendTo(new Sync(windowId, state, report), player);
    }

    /** {@code cycles} is {@code null} when the client already holds the cycle list of the running plan. */
    public static void syncStatus(EntityPlayerMP player, int windowId, boolean eco, int cpuTier,
        @Nullable ECOCycleItemList cycles) {
        CHANNEL.sendTo(new StatusSync(windowId, eco, cpuTier, cycles), player);
    }

    public static void syncStatusProcessing(EntityPlayerMP player, int windowId, CompoundTag processing) {
        CHANNEL.sendTo(new StatusProcessing(windowId, processing), player);
    }

    /** Asks for the machine details of the hovered output; {@code null} stops the detail sampling. */
    public static void requestProcessingDetail(int windowId, @Nullable AEKey key, int page) {
        CHANNEL.sendToServer(new ProcessingDetail(windowId, key, page));
    }

    /** Status cell right-click menu: inspect an output, or open one of the places the inspection reported. */
    public static void requestItemAction(int windowId, long sequence, String action, AEKey key, @Nullable String id) {
        CHANNEL.sendToServer(new ItemAction(windowId, sequence, action, key, id));
    }

    public static void sendItemActionResult(EntityPlayerMP player, int windowId, CompoundTag result) {
        CHANNEL.sendTo(new ItemActionResult(windowId, result), player);
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

    /** Server to client: whether the open crafting status page is ECO's, plus the running plan's cycle members. */
    public static final class StatusSync implements IMessage {

        public int windowId;
        public boolean eco;
        public int cpuTier;
        @Nullable
        public ECOCycleItemList cycles;

        public StatusSync() {}

        StatusSync(int windowId, boolean eco, int cpuTier, @Nullable ECOCycleItemList cycles) {
            this.windowId = windowId;
            this.eco = eco;
            this.cpuTier = cpuTier;
            this.cycles = cycles;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            windowId = data.readVarInt();
            eco = data.readBoolean();
            cpuTier = data.readVarInt();
            cycles = data.readBoolean() ? new ECOCycleItemList(data) : null;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            data.writeVarInt(windowId);
            data.writeBoolean(eco);
            data.writeVarInt(cpuTier);
            data.writeBoolean(cycles != null);
            if (cycles != null) cycles.writeToPacket(data);
        }
    }

    public static final class StatusSyncHandler implements IMessageHandler<StatusSync, IMessage> {

        @Override
        public IMessage onMessage(StatusSync message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftingStatusClient
                .accept(message.windowId, message.eco, message.cpuTier, message.cycles);
            return null;
        }
    }

    /** Server to client: live machine status of the outputs the selected ECO CPU is waiting for. */
    public static final class StatusProcessing implements IMessage {

        public int windowId;
        public CompoundTag processing = new CompoundTag();

        public StatusProcessing() {}

        StatusProcessing(int windowId, CompoundTag processing) {
            this.windowId = windowId;
            this.processing = processing;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            windowId = data.readVarInt();
            CompoundTag tag = data.readNbt();
            processing = tag == null ? new CompoundTag() : tag;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            data.writeVarInt(windowId);
            data.writeNbt(processing);
        }
    }

    public static final class StatusProcessingHandler implements IMessageHandler<StatusProcessing, IMessage> {

        @Override
        public IMessage onMessage(StatusProcessing message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftingStatusClient
                .acceptProcessing(message.windowId, message.processing);
            return null;
        }
    }

    /** Client to server: the output whose machine details the status tooltip shows, and its detail page. */
    public static final class ProcessingDetail implements IMessage {

        int windowId;
        @Nullable
        AEKey key;
        int page;

        public ProcessingDetail() {}

        ProcessingDetail(int windowId, @Nullable AEKey key, int page) {
            this.windowId = windowId;
            this.key = key;
            this.page = page;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            windowId = data.readVarInt();
            key = data.readBoolean() ? AEKey.readKey(data) : null;
            page = data.readVarInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            data.writeVarInt(windowId);
            data.writeBoolean(key != null);
            if (key != null) AEKey.writeKey(data, key);
            data.writeVarInt(page);
        }
    }

    public static final class ProcessingDetailHandler implements IMessageHandler<ProcessingDetail, IMessage> {

        @Override
        public IMessage onMessage(ProcessingDetail message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(
                    () -> ECOCraftingStatusTracker.requestDetail(player, message.windowId, message.key, message.page));
            return null;
        }
    }

    /** Client to server: one status-cell menu action for an output of the selected CPU. */
    public static final class ItemAction implements IMessage {

        int windowId;
        long sequence;
        String action = "";
        @Nullable
        AEKey key;
        @Nullable
        String id;

        public ItemAction() {}

        ItemAction(int windowId, long sequence, String action, AEKey key, @Nullable String id) {
            this.windowId = windowId;
            this.sequence = sequence;
            this.action = action;
            this.key = key;
            this.id = id;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            windowId = data.readVarInt();
            sequence = data.readLong();
            action = data.readUtf();
            key = data.readBoolean() ? AEKey.readKey(data) : null;
            id = data.readBoolean() ? data.readUtf() : null;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            data.writeVarInt(windowId);
            data.writeLong(sequence);
            data.writeUtf(action);
            data.writeBoolean(key != null);
            if (key != null) AEKey.writeKey(data, key);
            data.writeBoolean(id != null);
            if (id != null) data.writeUtf(id);
        }
    }

    public static final class ItemActionHandler implements IMessageHandler<ItemAction, IMessage> {

        @Override
        public IMessage onMessage(ItemAction message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (message.action.length() > 32 || message.id != null && message.id.length() > 128) return null;
            ServerTasks.captureSession()
                .execute(
                    () -> cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingStatusItemActions
                        .handle(player, message.windowId, message.sequence, message.action, message.key, message.id));
            return null;
        }
    }

    /** Server to client: the answer to one status-cell menu action. */
    public static final class ItemActionResult implements IMessage {

        public int windowId;
        public CompoundTag result = new CompoundTag();

        public ItemActionResult() {}

        ItemActionResult(int windowId, CompoundTag result) {
            this.windowId = windowId;
            this.result = result;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            windowId = data.readVarInt();
            CompoundTag tag = data.readNbt();
            result = tag == null ? new CompoundTag() : tag;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            var data = new RegistryFriendlyByteBuf(buf, RegistryAccess.EMPTY);
            data.writeVarInt(windowId);
            data.writeNbt(result);
        }
    }

    public static final class ItemActionResultHandler implements IMessageHandler<ItemActionResult, IMessage> {

        @Override
        public IMessage onMessage(ItemActionResult message, MessageContext context) {
            cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftingStatusClient
                .acceptItemAction(message.windowId, message.result);
            return null;
        }
    }
}
