package cn.dancingsnow.neoecoae.platform.ae2.grid;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import cn.dancingsnow.neoecoae.platform.network.ServerThread;

/** Each MENetworkCellArrayUpdate costs a full rv3 network rescan; bursts must reach a grid once. */
class NativeCellArrayUpdatesTest {

    @AfterEach
    void reset() {
        NativeCellArrayUpdates.clear();
        ServerThread.clear();
    }

    @Test
    void nodeStateChangesOnTheServerThreadPostOncePerGridAtTickEnd() {
        ServerThread.capture();
        IGrid grid = mock(IGrid.class);
        NativeNetworkBlockEntity first = owner(grid), second = owner(grid);
        NativeCellArrayUpdates.nodeStateChanged(first);
        NativeCellArrayUpdates.nodeStateChanged(second);
        NativeCellArrayUpdates.nodeStateChanged(first);
        verify(grid, never()).postEvent(any());
        NativeCellArrayUpdates.flushTick();
        verify(grid, times(1)).postEvent(any(MENetworkCellArrayUpdate.class));
        NativeCellArrayUpdates.flushTick();
        verify(grid, times(1)).postEvent(any(MENetworkCellArrayUpdate.class));
    }

    @Test
    void invalidatedOwnersAreSkippedAtTickEnd() {
        ServerThread.capture();
        IGrid grid = mock(IGrid.class);
        NativeNetworkBlockEntity owner = owner(grid);
        when(owner.isInvalid()).thenReturn(true);
        NativeCellArrayUpdates.nodeStateChanged(owner);
        NativeCellArrayUpdates.flushTick();
        verify(grid, never()).postEvent(any());
    }

    @Test
    void offTheServerThreadNodeStateChangesPostImmediately() {
        IGrid grid = mock(IGrid.class);
        NativeCellArrayUpdates.nodeStateChanged(owner(grid));
        verify(grid).postEvent(any(MENetworkCellArrayUpdate.class));
    }

    @Test
    void structuralChangesPostSynchronouslyAndScopesMergeThemPerGrid() {
        IGrid grid = mock(IGrid.class), other = mock(IGrid.class);
        NativeCellArrayUpdates.structural(owner(grid));
        verify(grid, times(1)).postEvent(any(MENetworkCellArrayUpdate.class));
        try (var outer = NativeCellArrayUpdates.coalesce()) {
            NativeCellArrayUpdates.structural(owner(grid));
            try (var inner = NativeCellArrayUpdates.coalesce()) {
                NativeCellArrayUpdates.structural(owner(grid));
                NativeCellArrayUpdates.structural(owner(other));
            }
            verify(grid, times(1)).postEvent(any());
            verify(other, never()).postEvent(any());
        }
        verify(grid, times(2)).postEvent(any(MENetworkCellArrayUpdate.class));
        verify(other, times(1)).postEvent(any(MENetworkCellArrayUpdate.class));
    }

    private static NativeNetworkBlockEntity owner(IGrid grid) {
        IGridNode node = mock(IGridNode.class);
        when(node.getGrid()).thenReturn(grid);
        ManagedNode managed = mock(ManagedNode.class);
        when(managed.isReady()).thenReturn(true);
        when(managed.getNode()).thenReturn(node);
        NativeNetworkBlockEntity owner = mock(NativeNetworkBlockEntity.class);
        when(owner.getMainNode()).thenReturn(managed);
        return owner;
    }
}
