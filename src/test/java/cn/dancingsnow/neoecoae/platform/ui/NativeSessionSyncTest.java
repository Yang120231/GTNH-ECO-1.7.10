package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageSession;
import io.netty.buffer.Unpooled;

/** Runs against both the pinned API and newer MUI2 runtimes that reject C2S by default. */
class NativeSessionSyncTest {

    @Test
    void storagePagingAlsoReachesTheNetwork() {
        var session = new NativeStorageSession(null, null);
        var packets = capture(session, () -> session.scroll(1));
        assertEquals(1, packets.size());
        assertPacket(packets.get(0), 0, packet -> assertEquals(0, packet.readInt()));
    }

    private static List<byte[]> capture(NativeActionSyncHandler session, Runnable actions) {
        assertTrue(session.isAllowC2S(), "Native action channels must opt in even when compiled against older MUI2");
        var manager = mock(PanelSyncManager.class);
        when(manager.getPanelName()).thenReturn("test_machine");
        session.init("actions", manager);
        List<byte[]> packets = new ArrayList<>();
        try (var network = mockStatic(SyncHandler.class, CALLS_REAL_METHODS)) {
            network.when(() -> SyncHandler.sendToServer(eq("test_machine"), any(PacketBuffer.class), same(session)))
                .thenAnswer(invocation -> {
                    PacketBuffer packet = invocation.getArgument(1);
                    try {
                        byte[] bytes = new byte[packet.readableBytes()];
                        packet.readBytes(bytes);
                        packets.add(bytes);
                    } finally {
                        packet.release();
                    }
                    return null;
                });
            actions.run();
        } finally {
            session.dispose();
        }
        return packets;
    }

    private static void assertPacket(byte[] bytes, int id, Consumer<PacketBuffer> payload) {
        PacketBuffer packet = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
        try {
            assertEquals(id, packet.readVarIntFromBuffer());
            payload.accept(packet);
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }
}
