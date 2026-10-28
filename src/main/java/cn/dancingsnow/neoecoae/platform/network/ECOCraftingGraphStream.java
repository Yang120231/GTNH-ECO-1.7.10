package cn.dancingsnow.neoecoae.platform.network;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import io.netty.buffer.Unpooled;

/**
 * Wire form of a crafting graph for the 1.7.10 payload limits (1.21's MenuDataTransport GRAPH channel). A snapshot is
 * encoded once (it compresses itself), then cut into chunks well below the ~32 KiB custom payload limit and
 * reassembled in order on the client.
 */
public final class ECOCraftingGraphStream {

    /** Bytes of graph data carried by one packet. */
    public static final int CHUNK_BYTES = 16 * 1024;
    /** Upper bound of one encoded graph; larger plans are reported as too large instead of being sent. */
    public static final int MAX_BYTES = 16 * 1024 * 1024 + 16;

    /** What the server could send for a requested plan. */
    public enum Status {

        /** Chunks of an encoded snapshot follow. */
        READY,
        /** The plan has no graph (no ECO result, or an empty snapshot). */
        EMPTY,
        /** The encoded graph exceeds the transfer limit. */
        TOO_LARGE,
        /** Building or encoding the graph failed on the server. */
        FAILED;

        public static Status byId(int id) {
            Status[] values = values();
            return id >= 0 && id < values.length ? values[id] : FAILED;
        }
    }

    private ECOCraftingGraphStream() {}

    /** True when a snapshot has nothing the graph screen could show. */
    public static boolean isEmpty(CraftingGraphSnapshot snapshot) {
        return snapshot.rootNodeId() < 0 && snapshot.cycleGroups()
            .isEmpty();
    }

    /**
     * Encodes a snapshot, or returns {@code null} when it does not fit the transfer limit.
     *
     * @throws RuntimeException when the snapshot itself cannot be written
     */
    public static byte @Nullable [] encode(CraftingGraphSnapshot snapshot) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_BYTES), RegistryAccess.EMPTY);
        try {
            snapshot.writeToPacket(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } catch (IndexOutOfBoundsException tooLarge) {
            return null;
        } catch (IllegalArgumentException tooLarge) {
            if (String.valueOf(tooLarge.getMessage())
                .contains("too large")) return null;
            throw tooLarge;
        } finally {
            buf.release();
        }
    }

    public static CraftingGraphSnapshot decode(byte[] bytes) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
        try {
            CraftingGraphSnapshot snapshot = new CraftingGraphSnapshot(buf);
            if (buf.isReadable()) throw new IllegalArgumentException("Trailing crafting graph data");
            return snapshot;
        } finally {
            buf.release();
        }
    }

    /** Number of chunks a payload of {@code total} bytes is sent in. */
    public static int chunkCount(int total) {
        return Math.max(1, (total + CHUNK_BYTES - 1) / CHUNK_BYTES);
    }

    /** Length of the chunk starting at {@code offset}. */
    public static int chunkLength(int total, int offset) {
        return Math.min(CHUNK_BYTES, total - offset);
    }

    /** Ordered, bounded reassembly. A new transfer supersedes an incomplete old transfer. */
    public static final class Assembler {

        private long transfer = -1;
        private byte[] bytes;
        private int offset;

        /** @return the complete payload once its last chunk arrived, otherwise {@code null} */
        public byte @Nullable [] accept(long id, int total, int position, byte[] part) {
            if (total < 1 || total > MAX_BYTES
                || position < 0
                || part.length == 0
                || part.length > total
                || position > total - part.length) {
                throw new IllegalArgumentException("Invalid crafting graph stream bounds");
            }
            if (position == 0) {
                transfer = id;
                bytes = new byte[total];
                offset = 0;
            }
            if (bytes == null || transfer != id || bytes.length != total || position != offset) {
                throw new IllegalArgumentException("Out-of-order crafting graph stream");
            }
            System.arraycopy(part, 0, bytes, offset, part.length);
            offset += part.length;
            if (offset != total) return null;
            byte[] complete = bytes;
            bytes = null;
            transfer = -1;
            return complete;
        }

        public void reset() {
            transfer = -1;
            bytes = null;
            offset = 0;
        }
    }
}
