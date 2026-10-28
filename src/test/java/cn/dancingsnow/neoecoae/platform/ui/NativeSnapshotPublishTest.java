package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** An accepted action is reflected in the snapshot at once instead of after the next 5-tick capture. */
class NativeSnapshotPublishTest {

    private static final class Session extends NativeSnapshotSession {

        int captures, value;

        Session() {
            super(mock(TileEntity.class));
        }

        @Override
        protected CompoundTag capture() {
            captures++;
            CompoundTag tag = new CompoundTag();
            tag.putInt("value", value);
            return tag;
        }

        @Override
        protected void apply(int action, int argument) {
            value = argument;
        }
    }

    @Test
    void actionsRecaptureImmediatelyAndOnlySendChangedSnapshots() {
        var session = new Session();
        var manager = mock(PanelSyncManager.class);
        when(manager.getPanelName()).thenReturn("test_machine");
        session.init("snapshot", manager);
        AtomicInteger sent = new AtomicInteger();
        try (var network = mockStatic(SyncHandler.class, CALLS_REAL_METHODS)) {
            network.when(() -> SyncHandler.sendToClient(eq("test_machine"), any(PacketBuffer.class), same(session)))
                .thenAnswer(invocation -> {
                    sent.incrementAndGet();
                    ((PacketBuffer) invocation.getArgument(1)).release();
                    return null;
                });
            session.applyAndPublish(0, 7);
            assertEquals(1, session.captures);
            assertEquals(1, sent.get());
            assertEquals(
                7,
                session.state()
                    .getInt("value"));
            session.applyAndPublish(0, 7);
            assertEquals(2, session.captures);
            assertEquals(1, sent.get(), "An unchanged snapshot is not resent");
        } finally {
            session.dispose();
        }
    }
}
