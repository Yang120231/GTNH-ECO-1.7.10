package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Server-thread registry: at most one active session, one NEI hint and one undo receipt per player. */
public final class PatternUploadSessions {

    private static final Map<UUID, PatternUploadSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, EditorSource> HINTS = new HashMap<>();
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

    /** Registers one editor and its native successful-encode listener; old windows release their listener. */
    public static boolean bindEditor(EntityPlayer player, Container container, Object inventory, Runnable release) {
        EditorSource current = HINTS.get(player.getUniqueID());
        if (current != null && current.container == container && current.inventory == inventory) return false;
        if (current != null) current.close();
        HINTS.put(player.getUniqueID(), new EditorSource(player, container, inventory, release));
        return true;
    }

    public static void setHint(EntityPlayer player, RouteHint hint) {
        EditorSource source = editor(player);
        if (source == null) return;
        source.draft = hint == null || hint.isEmpty() ? null : hint;
        for (EditorSource other : HINTS.values()) if (other != source && other.inventory == source.inventory)
            other.draft = null;
    }

    public static void beginEncoding(EntityPlayer player) {
        EditorSource source = editor(player);
        if (source != null) {
            source.encoding = true;
            for (EditorSource other : HINTS.values()) if (other != source && other.inventory == source.inventory) {
                other.draft = null;
                other.encoded = null;
            }
        }
    }

    public static void endEncoding(EntityPlayer player) {
        EditorSource source = editor(player);
        if (source != null) source.encoding = false;
    }

    public static boolean isEncoding(EntityPlayer player) {
        EditorSource source = editor(player);
        return source != null && source.encoding;
    }

    public static RouteHint draft(EntityPlayer player) {
        EditorSource source = editor(player);
        return source == null ? null : source.draft;
    }

    /** Called by AE2 only after an encoding succeeds, including a re-encoding of the same draft. */
    public static void onEncoded(EntityPlayer player, int windowId, ItemStack pattern) {
        EditorSource source = editor(player);
        if (source == null || source.container.windowId != windowId) return;
        source.encoded = source.draft == null ? null : source.draft.copy();
        if (source.encoded != null) source.encoded.bind(pattern);
    }

    /** A user loaded another pattern; native encoding and removal of an uploaded pattern keep the draft. */
    public static void patternChanged(IInventory inventory, ItemStack pattern) {
        if (pattern == null) return;
        for (EditorSource source : HINTS.values()) {
            if (source.inventory != inventory || source.encoding || !source.isOpen()) continue;
            if (source.encoded != null && source.encoded.appliesTo(source.container.windowId, pattern))
                source.draft = source.encoded.copy();
            else {
                source.draft = null;
                source.encoded = null;
            }
        }
    }

    /** NEI's source packet follows its clear/fill packet; any subsequent clear starts an unbound draft. */
    public static void draftCleared(EntityPlayer player) {
        EditorSource source = editor(player);
        if (source == null) return;
        for (EditorSource other : HINTS.values()) if (other.inventory == source.inventory) other.draft = null;
    }

    private static EditorSource editor(EntityPlayer player) {
        EditorSource source = HINTS.get(player.getUniqueID());
        if (source != null && !source.isOpen()) {
            source.close();
            HINTS.remove(player.getUniqueID());
            return null;
        }
        return source;
    }

    /** Any hint bound to {@code pattern} in this window, including one that only names the NEI category. */
    public static RouteHint hintFor(EntityPlayer player, int windowId, ItemStack pattern) {
        EditorSource source = editor(player);
        RouteHint hint = source == null ? null : source.encoded;
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
        EditorSource source = HINTS.remove(id);
        if (source != null) source.close();
        RECEIPTS.remove(id);
    }

    /** Periodic sweep; closed, idle or replaced windows release player, inventory and world references. */
    public static void sweep() {
        Iterator<EditorSource> sources = HINTS.values().iterator();
        while (sources.hasNext()) {
            EditorSource source = sources.next();
            if (!source.isOpen()) {
                source.close();
                sources.remove();
            }
        }
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
        HINTS.values().forEach(EditorSource::close);
        HINTS.clear();
        RECEIPTS.clear();
    }

    private static final class EditorSource {

        final EntityPlayer player;
        final Container container;
        final Object inventory;
        final Runnable release;
        RouteHint draft;
        RouteHint encoded;
        boolean encoding;

        EditorSource(EntityPlayer player, Container container, Object inventory, Runnable release) {
            this.player = player;
            this.container = container;
            this.inventory = inventory;
            this.release = release;
        }

        boolean isOpen() {
            return player.openContainer == container;
        }

        void close() {
            release.run();
            draft = null;
            encoded = null;
        }
    }
}
