package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;
import cn.dancingsnow.neoecoae.platform.mixin.ContainerCraftConfirmAccessor;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftingGraphPackets;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftingGraphStream;

/**
 * Server half of the crafting graph screen. The graph is only built and sent when the client opens it: a request names
 * the confirmation window and the report version on screen, and is answered for the plan the open container holds.
 * The encoded graph is cached per plan and streamed under a per-player byte budget so a huge plan cannot flood the
 * connection in one tick.
 */
public final class ECOCraftingGraphSync {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");
    /** Graph bytes sent to one player per server tick. */
    static final int TICK_BYTES = 4 * ECOCraftingGraphStream.CHUNK_BYTES;
    private static final Map<EntityPlayerMP, Encoded> ENCODED = new WeakHashMap<>();
    private static final Map<EntityPlayerMP, Transfer> OUTGOING = new WeakHashMap<>();
    private static long nextTransfer;

    private ECOCraftingGraphSync() {}

    /** One plan's encoded graph, valid while the same container holds the same result. */
    private static final class Encoded {

        final ContainerCraftConfirm container;
        final Object result;
        final ECOCraftingGraphStream.Status status;
        final byte[] bytes;

        Encoded(ContainerCraftConfirm container, Object result, ECOCraftingGraphStream.Status status, byte[] bytes) {
            this.container = container;
            this.result = result;
            this.status = status;
            this.bytes = bytes;
        }
    }

    private static final class Transfer {

        final ContainerCraftConfirm container;
        final long version;
        final long id;
        final byte[] bytes;
        int offset;

        Transfer(ContainerCraftConfirm container, long version, long id, byte[] bytes) {
            this.container = container;
            this.version = version;
            this.id = id;
            this.bytes = bytes;
        }
    }

    public static void request(EntityPlayerMP player, int windowId, long version) {
        if (!(player.openContainer instanceof ContainerCraftConfirm container) || container.windowId != windowId)
            return;
        OUTGOING.remove(player);
        ICraftingJob result = ((ContainerCraftConfirmAccessor) container).neoecoae$getResult();
        if (result == null) {
            answer(player, windowId, version, ECOCraftingGraphStream.Status.EMPTY);
            return;
        }
        Encoded encoded = ENCODED.get(player);
        if (encoded == null || encoded.container != container || encoded.result != result) {
            encoded = encode(container, result);
            ENCODED.put(player, encoded);
        }
        if (encoded.status != ECOCraftingGraphStream.Status.READY) {
            answer(player, windowId, version, encoded.status);
            return;
        }
        Transfer transfer = new Transfer(container, version, ++nextTransfer, encoded.bytes);
        OUTGOING.put(player, transfer);
        // The first chunk goes out at once; the rest follow under the tick budget.
        send(player, transfer, TICK_BYTES);
        if (transfer.offset >= transfer.bytes.length) OUTGOING.remove(player);
    }

    private static Encoded encode(ContainerCraftConfirm container, ICraftingJob result) {
        try {
            if (!(result instanceof NativeECOCraftingJob job) || job.sourcePlan() == null)
                return new Encoded(container, result, ECOCraftingGraphStream.Status.EMPTY, new byte[0]);
            ECOPlanningResult planning = ECOCraftConfirmReports.resolve(job.sourcePlan());
            if (planning == null)
                return new Encoded(container, result, ECOCraftingGraphStream.Status.EMPTY, new byte[0]);
            CraftingGraphSnapshot snapshot = CraftingGraphSnapshotFactory.create(planning);
            if (ECOCraftingGraphStream.isEmpty(snapshot))
                return new Encoded(container, result, ECOCraftingGraphStream.Status.EMPTY, new byte[0]);
            byte[] bytes = ECOCraftingGraphStream.encode(snapshot);
            if (bytes == null) {
                LOGGER.info(
                    "[craft-graph] Crafting graph exceeds the transfer limit: {} materials, {} patterns, {} edges",
                    snapshot.nodes()
                        .size(),
                    snapshot.patterns()
                        .size(),
                    snapshot.edges()
                        .size());
                return new Encoded(container, result, ECOCraftingGraphStream.Status.TOO_LARGE, new byte[0]);
            }
            return new Encoded(container, result, ECOCraftingGraphStream.Status.READY, bytes);
        } catch (RuntimeException | StackOverflowError failure) {
            LOGGER.warn("[craft-graph] Building the crafting graph failed", failure);
            return new Encoded(container, result, ECOCraftingGraphStream.Status.FAILED, new byte[0]);
        }
    }

    private static void answer(EntityPlayerMP player, int windowId, long version,
        ECOCraftingGraphStream.Status status) {
        ECOCraftingGraphPackets.send(
            player,
            new ECOCraftingGraphPackets.Chunk(windowId, version, ++nextTransfer, status, 0, 0, new byte[0]));
    }

    private static void send(EntityPlayerMP player, Transfer transfer, int budget) {
        while (budget > 0 && transfer.offset < transfer.bytes.length) {
            int length = ECOCraftingGraphStream.chunkLength(transfer.bytes.length, transfer.offset);
            ECOCraftingGraphPackets.send(
                player,
                new ECOCraftingGraphPackets.Chunk(
                    transfer.container.windowId,
                    transfer.version,
                    transfer.id,
                    ECOCraftingGraphStream.Status.READY,
                    transfer.bytes.length,
                    transfer.offset,
                    Arrays.copyOfRange(transfer.bytes, transfer.offset, transfer.offset + length)));
            transfer.offset += length;
            budget -= length;
        }
    }

    public static void tick() {
        for (Iterator<Map.Entry<EntityPlayerMP, Transfer>> it = OUTGOING.entrySet()
            .iterator(); it.hasNext();) {
            var entry = it.next();
            EntityPlayerMP player = entry.getKey();
            Transfer transfer = entry.getValue();
            // A closed confirmation (or a disconnect) abandons its transfer; the client discards the partial data.
            if (player.playerNetServerHandler == null || player.openContainer != transfer.container) {
                it.remove();
                continue;
            }
            try {
                send(player, transfer, TICK_BYTES);
            } catch (RuntimeException failure) {
                LOGGER.warn("[craft-graph] Sending the crafting graph failed", failure);
                it.remove();
                continue;
            }
            if (transfer.offset >= transfer.bytes.length) it.remove();
        }
        ENCODED.entrySet()
            .removeIf(entry -> entry.getKey().openContainer != entry.getValue().container);
    }

    public static void clear() {
        OUTGOING.clear();
        ENCODED.clear();
    }
}
