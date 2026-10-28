package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/** The drive LED reads mounted/online on the client, so every change of either must reach the client. */
class ECODriveLedSyncTest {

    private ECODriveBlockEntity drive;
    private ManagedNode node;

    @BeforeEach
    void setup() throws Exception {
        drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        node = mock(ManagedNode.class);
        doReturn(node).when(drive)
            .getMainNode();
        doReturn(null).when(drive)
            .getCellInventory();
        doNothing().when(drive)
            .markForUpdate();
        doNothing().when(drive)
            .setChanged();
        var level = NativeNetworkBlockEntity.class.getDeclaredField("level");
        level.setAccessible(true);
        level.set(drive, mock(WorldView.class));
    }

    @Test
    void goingOnlineOrOfflineSyncsTheClient() {
        when(node.isOnline()).thenReturn(true);
        drive.onMainNodeStateChanged(NodeListener.State.POWER);
        assertTrue(drive.isOnline());
        verify(drive, times(1)).markForUpdate();

        drive.onMainNodeStateChanged(NodeListener.State.CHANNEL);
        verify(drive, times(1)).markForUpdate();

        when(node.isOnline()).thenReturn(false);
        drive.onMainNodeStateChanged(NodeListener.State.POWER);
        assertFalse(drive.isOnline());
        verify(drive, times(2)).markForUpdate();
    }

    /** onReady's re-read: the node's first report arrives before it is ready, and a running grid sends no other. */
    @Test
    void readyReReadRecoversAnOnlineNodeThatReportedBeforeItWasReady() throws Exception {
        when(node.isOnline()).thenReturn(false);
        drive.onMainNodeStateChanged(NodeListener.State.GRID_BOOT);
        assertFalse(drive.isOnline());
        verify(drive, never()).markForUpdate();

        when(node.isOnline()).thenReturn(true);
        Method refresh = ECODriveBlockEntity.class.getDeclaredMethod("refreshOnline");
        refresh.setAccessible(true);
        refresh.invoke(drive);
        assertTrue(drive.isOnline());
        verify(drive, times(1)).markForUpdate();
    }

    @Test
    void mountingSyncsTheClientOnlyWhenItChanges() throws Exception {
        Method update = ECODriveBlockEntity.class.getDeclaredMethod("updateMountState", boolean.class);
        update.setAccessible(true);
        update.invoke(drive, true);
        assertTrue(drive.isMounted());
        verify(drive, times(1)).markForUpdate();

        update.invoke(drive, true);
        verify(drive, times(1)).markForUpdate();

        update.invoke(drive, false);
        assertFalse(drive.isMounted());
        verify(drive, times(2)).markForUpdate();
    }
}
