package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

public final class PatternUploadSessions {

    private static final Map<UUID, PatternUploadSession> SESSIONS = new HashMap<>();

    private PatternUploadSessions() {}

    public static synchronized PatternUploadSession create(EntityPlayerMP player, IGrid grid, IGridNode sourceNode,
        ItemStack pattern, IInventory sourceInventory, int sourceSlot, boolean processing) {
        PatternUploadSession session = PatternUploadSession
            .create(player, grid, sourceNode, pattern, sourceInventory, sourceSlot, processing);
        SESSIONS.put(session.getId(), session);
        return session;
    }

    public static synchronized PatternUploadSession get(UUID id) {
        PatternUploadSession session = SESSIONS.get(id);
        if (session == null || session.isExpired()) {
            if (session != null) SESSIONS.remove(id);
            return null;
        }
        return session;
    }

    public static synchronized void remove(UUID id) {
        SESSIONS.remove(id);
    }
}
