package cn.dancingsnow.neoecoae.platform.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    /** Actions sent from the client-side target screen while the terminal window stays open on the server. */
    public static final int ACTION_UPLOAD = 0, ACTION_AUTO = 1, ACTION_UNDO = 2, ACTION_REFRESH = 3, ACTION_LOCATE = 4;
    /** What the action behind a {@link TargetList} did; the screen returns to the terminal on UPLOADED. */
    public static final int EVENT_NONE = 0, EVENT_UPLOADED = 1, EVENT_UNDONE = 2;
    /** Rows per network part, so one payload stays far below the 1.7.10 custom payload limit. */
    public static final int ROWS_PER_PART = 64;
    public static final int MAX_ROWS = 4096;

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("neoecoae_upload");
    private static boolean registered;
    private static int sequence, listSerial;

    private PatternUploadPackets() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(HintHandler.class, Hint.class, 1, Side.SERVER);
        CHANNEL.registerMessage(AutoTargetHandler.class, AutoTarget.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(HighlightHandler.class, Highlight.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(ActionHandler.class, Action.class, 4, Side.SERVER);
        CHANNEL.registerMessage(TargetListHandler.class, TargetList.class, 5, Side.CLIENT);
    }

    public static void request(int windowId, int mode) {
        CHANNEL.sendToServer(new Request(windowId, mode, ++sequence));
    }

    /** Client: one action from the target screen; shares the request sequence so a replay is never applied twice. */
    public static void action(int windowId, int action, int argument) {
        CHANNEL.sendToServer(new Action(windowId, action, argument, ++sequence));
    }

    /** Server: sends a complete list split into parts; the client shows it only once every part arrived. */
    public static void targetList(EntityPlayerMP player, TargetList list) {
        List<Row> rows = list.rows.size() > MAX_ROWS ? list.rows.subList(0, MAX_ROWS) : list.rows;
        int parts = Math.max(1, (rows.size() + ROWS_PER_PART - 1) / ROWS_PER_PART);
        int id = ++listSerial;
        for (int part = 0; part < parts; part++) {
            TargetList message = part == 0 ? list.header() : new TargetList();
            message.windowId = list.windowId;
            message.listId = id;
            message.part = part;
            message.parts = parts;
            message.rows = new ArrayList<>(
                rows.subList(part * ROWS_PER_PART, Math.min(rows.size(), (part + 1) * ROWS_PER_PART)));
            CHANNEL.sendTo(message, player);
        }
    }

    public static void hint(int windowId, String recipeMapId, ItemStack circuit, String category) {
        CHANNEL.sendToServer(new Hint(windowId, recipeMapId, circuit, category));
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
        /** Display name of the NEI recipe category; empty for crafting-table recipes. */
        String category = "";

        public Hint() {}

        Hint(int windowId, String recipeMapId, ItemStack circuit, String category) {
            this.windowId = windowId;
            this.recipeMapId = recipeMapId == null ? "" : recipeMapId;
            this.circuit = circuit == null ? null : circuit.copy();
            this.category = category == null ? "" : category;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            recipeMapId = readString(buf, 128);
            circuit = ByteBufUtils.readItemStack(buf);
            category = readString(buf, 128);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            ByteBufUtils.writeUTF8String(buf, recipeMapId);
            ByteBufUtils.writeItemStack(buf, circuit);
            ByteBufUtils.writeUTF8String(buf, category);
        }
    }

    public static final class HintHandler implements IMessageHandler<Hint, IMessage> {

        @Override
        public IMessage onMessage(Hint message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(
                    () -> PatternUploadService
                        .hint(player, message.windowId, message.recipeMapId, message.circuit, message.category));
            return null;
        }
    }

    /** Server-chosen auto-upload target for one terminal window; an empty name means none. */
    public static final class AutoTarget implements IMessage {

        public int windowId, kind = -1;
        public String name = "", reason = "";
        /** Literal text after the translated name; see {@code PatternUploadTarget#nameSuffix()}. */
        public String nameSuffix = "";
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
            return name + nameSuffix
                + "|"
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
            nameSuffix = readString(buf, 256);
            kind = buf.readByte();
            circuit = ByteBufUtils.readItemStack(buf);
            programmingCover = buf.readBoolean();
            reason = readString(buf, 256);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            ByteBufUtils.writeUTF8String(buf, name);
            ByteBufUtils.writeUTF8String(buf, nameSuffix);
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

    public static final class Action implements IMessage {

        int windowId, action, argument, sequence;

        public Action() {}

        Action(int windowId, int action, int argument, int sequence) {
            this.windowId = windowId;
            this.action = action;
            this.argument = argument;
            this.sequence = sequence;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            action = buf.readByte();
            argument = buf.readInt();
            sequence = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeByte(action);
            buf.writeInt(argument);
            buf.writeInt(sequence);
        }
    }

    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {

        @Override
        public IMessage onMessage(Action message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.captureSession()
                .execute(
                    () -> PatternUploadService
                        .action(player, message.windowId, message.action, message.argument, message.sequence));
            return null;
        }
    }

    /**
     * One destination group: hosts with the same name, kind, verified route and circuit share one row. The token is
     * server-issued; the server picks which member to write at submit time.
     */
    public static final class Row {

        public int token;
        /** Translation key or literal host name. */
        public String name = "";
        /** Literal text after the translated name, e.g. GT's ghost circuit " [1]"; may be empty. */
        public String nameSuffix = "";
        /** Translation key or literal machine route name; empty when the host has no readable route. */
        public String route = "";
        /** Ordinals of {@code PatternUploadTarget.Kind}, {@code MatchResult.Level} and {@code SlotState}. */
        public int kind, level, state;
        /** Translation key explaining the match level; may be empty. */
        public String reason = "";
        public ItemStack icon;
        /** Hosts in the group, how many can take the pattern now, and how many already hold it. */
        public int members, freeMembers, duplicates;
        /** Summed over all members. */
        public int emptySlots, totalSlots;
        /** Virtual circuit number, or -1. */
        public int circuit = -1;
        public boolean programmingCover, manual;
        /** Position of the member the next upload would write to (or the first member). */
        public int dimension, x, y, z;

        void write(ByteBuf buf) {
            buf.writeInt(token);
            ByteBufUtils.writeUTF8String(buf, name);
            ByteBufUtils.writeUTF8String(buf, nameSuffix);
            ByteBufUtils.writeUTF8String(buf, route);
            buf.writeByte(kind);
            buf.writeByte(level);
            buf.writeByte(state);
            ByteBufUtils.writeUTF8String(buf, reason);
            ByteBufUtils.writeItemStack(buf, icon);
            buf.writeInt(members);
            buf.writeInt(freeMembers);
            buf.writeInt(duplicates);
            buf.writeInt(emptySlots);
            buf.writeInt(totalSlots);
            buf.writeShort(circuit);
            buf.writeBoolean(programmingCover);
            buf.writeBoolean(manual);
            buf.writeInt(dimension);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
        }

        static Row read(ByteBuf buf) {
            Row row = new Row();
            row.token = buf.readInt();
            row.name = readString(buf, 256);
            row.nameSuffix = readString(buf, 256);
            row.route = readString(buf, 256);
            row.kind = buf.readByte();
            row.level = buf.readByte();
            row.state = buf.readByte();
            row.reason = readString(buf, 256);
            row.icon = ByteBufUtils.readItemStack(buf);
            row.members = buf.readInt();
            row.freeMembers = buf.readInt();
            row.duplicates = buf.readInt();
            row.emptySlots = buf.readInt();
            row.totalSlots = buf.readInt();
            row.circuit = buf.readShort();
            row.programmingCover = buf.readBoolean();
            row.manual = buf.readBoolean();
            row.dimension = buf.readInt();
            row.x = buf.readInt();
            row.y = buf.readInt();
            row.z = buf.readInt();
            return row;
        }
    }

    /**
     * Server to client: the target list of one terminal window, sorted best first. Part 0 carries the header; every
     * part carries a slice of rows, and {@link TargetListHandler} hands only complete lists to the client.
     */
    public static final class TargetList implements IMessage {

        public int windowId, listId, part, parts = 1;
        /** Primary output of the encoded pattern, or null when the terminal holds no encoded pattern. */
        public ItemStack pattern;
        public boolean processing, hinted;
        /** Display name of the NEI recipe category the pattern was filled from; prefills the search. */
        public String category = "";
        /** Translation key of the last action's result; empty for none. */
        public String message = "";
        public boolean error;
        public int event = EVENT_NONE;
        public boolean receipt;
        public String receiptName = "", receiptNameSuffix = "";
        /** Token of the row auto-upload would use, or 0 with {@link #autoReason}. */
        public int autoToken;
        public String autoReason = "";
        public List<Row> rows = new ArrayList<>();

        public TargetList() {}

        TargetList header() {
            TargetList copy = new TargetList();
            copy.pattern = pattern;
            copy.processing = processing;
            copy.hinted = hinted;
            copy.category = category;
            copy.message = message;
            copy.error = error;
            copy.event = event;
            copy.receipt = receipt;
            copy.receiptName = receiptName;
            copy.receiptNameSuffix = receiptNameSuffix;
            copy.autoToken = autoToken;
            copy.autoReason = autoReason;
            return copy;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            windowId = buf.readInt();
            listId = buf.readInt();
            part = buf.readShort();
            parts = buf.readShort();
            if (part < 0 || parts < 1 || part >= parts || parts > MAX_ROWS / ROWS_PER_PART + 1)
                throw new IllegalArgumentException("Invalid target list part " + part + "/" + parts);
            if (part == 0) {
                pattern = ByteBufUtils.readItemStack(buf);
                processing = buf.readBoolean();
                hinted = buf.readBoolean();
                category = readString(buf, 128);
                message = readString(buf, 256);
                error = buf.readBoolean();
                event = buf.readByte();
                receipt = buf.readBoolean();
                receiptName = readString(buf, 256);
                receiptNameSuffix = readString(buf, 256);
                autoToken = buf.readInt();
                autoReason = readString(buf, 256);
            }
            int count = buf.readShort();
            if (count < 0 || count > ROWS_PER_PART) throw new IllegalArgumentException("Invalid row count " + count);
            rows = new ArrayList<>(count);
            for (int index = 0; index < count; index++) rows.add(Row.read(buf));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(windowId);
            buf.writeInt(listId);
            buf.writeShort(part);
            buf.writeShort(parts);
            if (part == 0) {
                ByteBufUtils.writeItemStack(buf, pattern);
                buf.writeBoolean(processing);
                buf.writeBoolean(hinted);
                ByteBufUtils.writeUTF8String(buf, category);
                ByteBufUtils.writeUTF8String(buf, message);
                buf.writeBoolean(error);
                buf.writeByte(event);
                buf.writeBoolean(receipt);
                ByteBufUtils.writeUTF8String(buf, receiptName);
                ByteBufUtils.writeUTF8String(buf, receiptNameSuffix);
                buf.writeInt(autoToken);
                ByteBufUtils.writeUTF8String(buf, autoReason);
            }
            buf.writeShort(rows.size());
            for (Row row : rows) row.write(buf);
        }
    }

    /** Reassembles parts on the network thread; a newer list discards an unfinished older one. */
    public static final class TargetListHandler implements IMessageHandler<TargetList, IMessage> {

        private static TargetList pending;

        @Override
        public IMessage onMessage(TargetList message, MessageContext context) {
            TargetList complete;
            synchronized (TargetListHandler.class) {
                if (message.part == 0) pending = message;
                else if (pending == null || pending.listId != message.listId
                    || pending.windowId != message.windowId
                    || pending.parts != message.parts) return null;
                else pending.rows.addAll(message.rows);
                if (message.part != message.parts - 1) return null;
                complete = pending;
                pending = null;
            }
            complete.rows = Collections.unmodifiableList(complete.rows);
            cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient.targetList(complete);
            return null;
        }
    }
}
