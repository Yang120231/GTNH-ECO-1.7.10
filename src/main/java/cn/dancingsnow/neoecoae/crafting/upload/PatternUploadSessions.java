package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/** Server-thread registry: at most one active session, one NEI hint and one undo receipt per player. */
public final class PatternUploadSessions {

    private static final Map<UUID, PatternUploadSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, RouteHint> HINTS = new HashMap<>();
    private static final Map<UUID, UploadReceipt> RECEIPTS = new HashMap<>();

    private PatternUploadSessions() {}

    public static PatternUploadSession open(PatternUploadSession session) {
        PatternUploadSession previous = SESSIONS.put(
            session.player()
                .getUniqueID(),
            session);
        if (previous != null && previous != session) previous.close();
        return session;
    }

    /** The player's session if it is still bound to the window the player has open; a stale one is released. */
    public static PatternUploadSession active(EntityPlayer player) {
        PatternUploadSession session = SESSIONS.get(player.getUniqueID());
        if (session != null && !session.isOpen()) {
            session.close();
            SESSIONS.remove(player.getUniqueID());
            return null;
        }
        return session;
    }

    /** The open session for exactly {@code container}, or null (another window's session stays untouched). */
    public static PatternUploadSession active(EntityPlayer player, Container container) {
        PatternUploadSession session = active(player);
        return session != null && session.boundTo(container) ? session : null;
    }

    public static void setHint(EntityPlayer player, RouteHint hint) {
        if (hint == null || hint.isEmpty()) HINTS.remove(player.getUniqueID());
        else HINTS.put(player.getUniqueID(), hint);
    }

    /** The terminal just encoded {@code pattern}; a pending hint now belongs to it, an older one is dropped. */
    public static void onEncoded(EntityPlayer player, int windowId, ItemStack pattern) {
        RouteHint hint = HINTS.get(player.getUniqueID());
        if (hint == null) return;
        if (hint.isPending() && !hint.expired(windowId) && pattern != null) hint.bind(pattern);
        else HINTS.remove(player.getUniqueID());
    }

    /** Any hint bound to {@code pattern} in this window, including one that only names the NEI category. */
    public static RouteHint hintFor(EntityPlayer player, int windowId, ItemStack pattern) {
        RouteHint hint = HINTS.get(player.getUniqueID());
        return hint != null && hint.appliesTo(windowId, pattern) ? hint : null;
    }

    /** Like {@link #hintFor} but only a hint that names a recipe map; this is the one route matching may use. */
    public static RouteHint routeHintFor(EntityPlayer player, int windowId, ItemStack pattern) {
        RouteHint hint = hintFor(player, windowId, pattern);
        return hint != null && hint.hasRecipeMap() ? hint : null;
    }

    static void remember(EntityPlayer player, UploadReceipt receipt) {
        RECEIPTS.put(player.getUniqueID(), receipt);
    }

    public static UploadReceipt receipt(EntityPlayer player) {
        return RECEIPTS.get(player.getUniqueID());
    }

    static void forget(EntityPlayer player, UploadReceipt receipt) {
        RECEIPTS.remove(player.getUniqueID(), receipt);
    }

    public static void logout(EntityPlayer player) {
        UUID id = player.getUniqueID();
        PatternUploadSession session = SESSIONS.remove(id);
        if (session != null) session.close();
        HINTS.remove(id);
        RECEIPTS.remove(id);
    }

    /** Periodic sweep; closed, idle or replaced windows release player, inventory and world references. */
    public static void sweep() {
        Iterator<PatternUploadSession> iterator = SESSIONS.values()
            .iterator();
        while (iterator.hasNext()) {
            PatternUploadSession session = iterator.next();
            if (!session.isOpen()) {
                session.close();
                iterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, UploadReceipt>> receipts = RECEIPTS.entrySet()
            .iterator();
        while (receipts.hasNext()) {
            UploadReceipt receipt = receipts.next()
                .getValue();
            if (!receipt.target.access.isLive()) receipts.remove();
        }
    }

    public static void clear() {
        SESSIONS.values()
            .forEach(PatternUploadSession::close);
        SESSIONS.clear();
        HINTS.clear();
        RECEIPTS.clear();
    }
}
