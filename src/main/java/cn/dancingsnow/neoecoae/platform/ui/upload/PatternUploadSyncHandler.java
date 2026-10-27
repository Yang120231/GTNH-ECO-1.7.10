package cn.dancingsnow.neoecoae.platform.ui.upload;

import java.io.IOException;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTransfer;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSession;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.UploadReceipt;
import cn.dancingsnow.neoecoae.platform.ae2.upload.PatternUploadService;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.ui.NativeActionSyncHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Server-authored page snapshot. The client only receives the current page and server-issued target tokens; every
 * action carries a sequence number and is replayed at most once on the server tick.
 */
public final class PatternUploadSyncHandler extends NativeActionSyncHandler {

    public static final int PAGE_SIZE = 6;
    static final int ACTION_PAGE = 0, ACTION_UPLOAD = 1, ACTION_LOCATE = 2, ACTION_UNDO = 3, ACTION_REFRESH = 4,
        ACTION_AUTO = 5;

    private final ServerTasks.Session serverSession = ServerTasks.captureSession();
    private PatternUploadSession session;
    private CompoundTag state = new CompoundTag(), previous;
    private long nextCapture, nextRefresh;
    private int page, clientSequence;

    public CompoundTag state() {
        return state;
    }

    public CompoundTag row(int index) {
        ListTag rows = state.getList("rows", 10);
        return index < rows.size() ? rows.getCompound(index) : new CompoundTag();
    }

    private boolean bind() {
        if (session != null) return session.isOpen();
        if (getSyncManager().isClient()) return false;
        session = PatternUploadSessions.active(getSyncManager().getPlayer());
        if (session == null || session.state() != PatternUploadSession.State.TERMINAL) {
            session = null;
            return false;
        }
        session.bindUploadPage(getSyncManager().getContainer());
        return true;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!bind()) {
            if (init) send(closedState());
            return;
        }
        long tick = session.player().worldObj.getTotalWorldTime();
        if (!init && tick < nextCapture) return;
        nextCapture = tick + 5;
        if (tick >= nextRefresh) {
            // Machines can change circuit or mode while the page is open; re-read them periodically.
            session.refresh();
            nextRefresh = tick + 40;
        }
        send(capture());
    }

    private void send(CompoundTag snapshot) {
        state = snapshot;
        if (previous != null && snapshot.equals(previous)) return;
        previous = snapshot.copy();
        syncToClient(0, buffer -> new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).writeNbt(snapshot));
    }

    private static CompoundTag closedState() {
        CompoundTag tag = new CompoundTag();
        tag.putString("message", "gui.neoecoae.pattern_upload.reason.session_closed");
        tag.putBoolean("error", true);
        return tag;
    }

    private CompoundTag capture() {
        CompoundTag tag = new CompoundTag();
        PatternContext context = session.context();
        if (context != null) {
            tag.put("pattern", ItemStacks.save(RegistryAccess.EMPTY, context.primaryOutput()));
            tag.putBoolean("processing", context.processing);
            tag.putBoolean("hinted", context.hint != null);
        }
        List<PatternTargetSelector.Evaluation> all = session.evaluations();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        tag.putInt("page", page);
        tag.putInt("pages", pages);
        tag.putInt("total", all.size());
        tag.putString("message", session.message());
        tag.putBoolean("error", session.messageError());
        UploadReceipt receipt = session.hasReceipt() ? PatternUploadSessions.receipt(session.player()) : null;
        if (receipt != null) {
            tag.putBoolean("receipt", true);
            tag.putString("receiptName", receipt.targetName);
            tag.put("receiptPattern", ItemStacks.save(RegistryAccess.EMPTY, receipt.pattern));
        }
        PatternTargetSelector.AutoChoice choice = PatternTargetSelector.choose(all);
        tag.putInt("auto", choice.selected == null ? 0 : session.token(choice.selected.target));
        tag.putString("autoReason", choice.reason == null ? "" : choice.reason);
        ListTag rows = new ListTag();
        for (PatternTargetSelector.Evaluation evaluation : session.page(page, PAGE_SIZE)) {
            PatternUploadTarget target = evaluation.target;
            CompoundTag row = new CompoundTag();
            row.putInt("token", session.token(target));
            row.putString("name", target.name());
            row.putInt(
                "kind",
                target.kind()
                    .ordinal());
            var icon = evaluation.match.routeIcon != null ? evaluation.match.routeIcon : target.icon();
            if (icon != null) row.put("icon", ItemStacks.save(RegistryAccess.EMPTY, icon));
            if (evaluation.match.routeName != null) row.putString("route", evaluation.match.routeName);
            row.putString("level", evaluation.match.level.translationKey());
            row.putInt("levelOrdinal", evaluation.match.level.ordinal());
            row.putString("reason", evaluation.match.reason == null ? "" : evaluation.match.reason);
            row.putString("state", evaluation.state.translationKey());
            row.putInt("stateOrdinal", evaluation.state.ordinal());
            row.putInt("empty", evaluation.emptySlots);
            row.putInt("slots", evaluation.totalSlots);
            row.putInt("dim", target.dimension());
            row.putInt("x", target.x());
            row.putInt("y", target.y());
            row.putInt("z", target.z());
            row.putInt(
                "circuit",
                evaluation.match.circuit == null ? -1 : evaluation.match.circuit.getItemDamage() & 0xFF);
            row.putBoolean("cover", evaluation.match.programmingCover);
            row.putBoolean("manual", evaluation.canUploadManually());
            rows.add(row);
        }
        tag.put("rows", rows);
        return tag;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown upload snapshot: " + id);
        CompoundTag next = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
        if (next == null || buffer.isReadable()) throw new IOException("Invalid upload snapshot");
        state = next;
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 0 || buffer.readableBytes() != 12) throw new IOException("Invalid upload action");
        int action = buffer.readInt(), argument = buffer.readInt(), sequence = buffer.readInt();
        serverSession.execute(() -> {
            if (!bind() || !session.acceptSequence(sequence)) return;
            apply(action, argument);
            nextCapture = 0;
        });
    }

    private void apply(int action, int argument) {
        EntityPlayerMP player = session.player();
        switch (action) {
            case ACTION_PAGE:
                page = Math.max(0, Math.min(65535, argument));
                return;
            case ACTION_UPLOAD:
                if (session.upload(argument, false)
                    .succeeded()) session.refresh();
                return;
            case ACTION_AUTO: {
                PatternTargetSelector.AutoChoice choice = PatternTargetSelector.choose(session.refresh());
                if (choice.selected == null) session.message(choice.reason, true);
                else if (session.upload(choice.selected.target, true)
                    .succeeded()) session.refresh();
                return;
            }
            case ACTION_LOCATE: {
                PatternUploadTarget target = session.target(argument);
                if (target != null) PatternUploadService.highlight(player, target);
                return;
            }
            case ACTION_UNDO: {
                PatternTransfer.Result result = session.undo();
                if (result.succeeded()) session.refresh();
                return;
            }
            case ACTION_REFRESH:
                session.refresh();
                return;
            default:
                throw new IllegalArgumentException("Unknown upload action " + action);
        }
    }

    @SideOnly(Side.CLIENT)
    public void action(int action, int argument) {
        int sequence = ++clientSequence;
        syncToServer(0, buffer -> {
            buffer.writeInt(action);
            buffer.writeInt(argument);
            buffer.writeInt(sequence);
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        if (session != null) session.close();
        session = null;
    }
}
