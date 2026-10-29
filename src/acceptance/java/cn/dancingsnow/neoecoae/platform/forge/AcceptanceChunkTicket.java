package cn.dancingsnow.neoecoae.platform.forge;

import java.util.HashSet;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Only explicit diagnostic sessions keep their own test chunks ticking while no player is online. */
final class AcceptanceChunkTicket implements AutoCloseable {

    private final ForgeChunkManager.Ticket ticket;

    public static void configure() {
        if (enabled()) ForgeChunkManager.setForcedChunkLoadingCallback(
            NeoECOAE.instance,
            (ForgeChunkManager.LoadingCallback) (tickets, world) -> tickets.forEach(ForgeChunkManager::releaseTicket));
    }

    AcceptanceChunkTicket(WorldServer world, Iterable<BlockPos> positions) {
        if (!enabled()) throw new IllegalStateException("Diagnostic chunks require explicit opt-in");
        ticket = ForgeChunkManager.requestTicket(NeoECOAE.instance, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) throw new IllegalStateException("No Forge chunk ticket available for acceptance");
        var chunks = new HashSet<ChunkCoordIntPair>();
        for (BlockPos pos : positions) chunks.add(new ChunkCoordIntPair(pos.getX() >> 4, pos.getZ() >> 4));
        if (chunks.size() > ticket.getChunkListDepth()) {
            ForgeChunkManager.releaseTicket(ticket);
            throw new IllegalStateException("Acceptance exceeds its chunk ticket depth");
        }
        chunks.forEach(chunk -> ForgeChunkManager.forceChunk(ticket, chunk));
    }

    @Override
    public void close() {
        ForgeChunkManager.releaseTicket(ticket);
    }

    private static boolean enabled() {
        return Boolean.getBoolean("neoecoae.portingAcceptance") || Boolean.getBoolean("neoecoae.clientAcceptance")
            || Boolean.getBoolean("neoecoae.storageUiAcceptance")
            || Boolean.getBoolean("neoecoae.storageUiNetworkAcceptance");
    }
}
