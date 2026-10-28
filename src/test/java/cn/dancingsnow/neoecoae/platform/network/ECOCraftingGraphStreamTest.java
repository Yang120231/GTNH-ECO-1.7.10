package cn.dancingsnow.neoecoae.platform.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

class ECOCraftingGraphStreamTest {

    private static CraftingGraphSnapshot graph(int patterns, long seed) {
        List<CraftingGraphSnapshot.PatternNode> nodes = new ArrayList<>();
        Random random = new Random(seed);
        for (int i = 0; i < patterns; i++) nodes.add(
            new CraftingGraphSnapshot.PatternNode(
                i,
                Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()),
                JdkCompat.listOf(),
                JdkCompat.listOf(),
                5L,
                CraftingGraphSnapshot.CandidateStatus.SELECTED,
                null,
                -1));
        return new CraftingGraphSnapshot(
            0,
            JdkCompat.listOf(),
            nodes,
            JdkCompat.listOf(),
            JdkCompat.listOf(),
            JdkCompat.listOf(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 0, nodes.size(), 0, 0, 123));
    }

    private static byte[] chunk(byte[] bytes, int offset) {
        return Arrays.copyOfRange(bytes, offset, offset + ECOCraftingGraphStream.chunkLength(bytes.length, offset));
    }

    @Test
    void compressedGraphRoundTripsAcrossManyChunks() {
        var graph = graph(4000, 43);
        byte[] bytes = ECOCraftingGraphStream.encode(graph);
        assertNotNull(bytes);
        assertTrue(bytes.length > ECOCraftingGraphStream.CHUNK_BYTES);
        int chunks = ECOCraftingGraphStream.chunkCount(bytes.length);
        assertTrue(chunks > 1);
        var assembler = new ECOCraftingGraphStream.Assembler();
        byte[] completed = null;
        int sent = 0;
        for (int offset = 0; offset < bytes.length; offset += ECOCraftingGraphStream.CHUNK_BYTES) {
            byte[] part = chunk(bytes, offset);
            assertTrue(part.length <= ECOCraftingGraphStream.CHUNK_BYTES);
            assertNull(completed);
            completed = assembler.accept(7, bytes.length, offset, part);
            sent++;
        }
        assertEquals(chunks, sent);
        assertNotNull(completed);
        assertEquals(graph, ECOCraftingGraphStream.decode(completed));
    }

    @Test
    void newTransferSupersedesAnUnfinishedOneAndGapsAreRejected() {
        byte[] first = ECOCraftingGraphStream.encode(graph(4000, 1));
        byte[] second = ECOCraftingGraphStream.encode(graph(3000, 2));
        var assembler = new ECOCraftingGraphStream.Assembler();
        assertNull(assembler.accept(1, first.length, 0, chunk(first, 0)));
        byte[] completed = null;
        for (int offset = 0; offset < second.length; offset += ECOCraftingGraphStream.CHUNK_BYTES)
            completed = assembler.accept(2, second.length, offset, chunk(second, offset));
        assertEquals(graph(3000, 2), ECOCraftingGraphStream.decode(completed));

        assertNull(assembler.accept(3, first.length, 0, chunk(first, 0)));
        int skipped = 2 * ECOCraftingGraphStream.CHUNK_BYTES;
        assertThrows(
            IllegalArgumentException.class,
            () -> assembler.accept(3, first.length, skipped, chunk(first, skipped)));
        assertThrows(
            IllegalArgumentException.class,
            () -> assembler.accept(4, ECOCraftingGraphStream.MAX_BYTES + 1, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(5, 4, 2, new byte[3]));
    }

    @Test
    void emptySnapshotsAndCorruptPayloadsAreRecognised() {
        assertTrue(ECOCraftingGraphStream.isEmpty(CraftingGraphSnapshot.EMPTY));
        assertFalse(ECOCraftingGraphStream.isEmpty(graph(1, 3)));
        byte[] bytes = ECOCraftingGraphStream.encode(graph(10, 4));
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        assertThrows(RuntimeException.class, () -> ECOCraftingGraphStream.decode(trailing));
        assertThrows(RuntimeException.class, () -> ECOCraftingGraphStream.decode(Arrays.copyOf(bytes, 3)));
        assertEquals(1, ECOCraftingGraphStream.chunkCount(1));
        assertEquals(2, ECOCraftingGraphStream.chunkCount(ECOCraftingGraphStream.CHUNK_BYTES + 1));
        assertEquals(ECOCraftingGraphStream.Status.FAILED, ECOCraftingGraphStream.Status.byId(99));
    }
}
