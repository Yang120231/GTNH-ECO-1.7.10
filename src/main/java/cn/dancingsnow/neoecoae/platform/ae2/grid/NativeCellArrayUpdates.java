package cn.dancingsnow.neoecoae.platform.ae2.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;

/**
 * rv3's {@code GridStorageCache.cellUpdate} rebuilds every handler, lists every provider and force-resends every open
 * terminal's list, so each {@link MENetworkCellArrayUpdate} costs a full network scan.
 * <p>
 * Structural requests (a cell or mount actually changed) still post synchronously, because until the event the grid
 * keeps the old views; a {@link #coalesce()} scope only merges a burst issued from one call into one post per grid.
 * Node-state requests (power, channel, boot) leave the mounted cells unchanged, so the old views stay valid and those
 * are merged per grid until the end of the server tick: one grid power flicker no longer rescans once per ECO drive.
 */
public final class NativeCellArrayUpdates implements AutoCloseable {

    private static final ThreadLocal<NativeCellArrayUpdates> ACTIVE = new ThreadLocal<>();
    private static final Set<NativeNetworkBlockEntity> TICK_PENDING = new LinkedHashSet<>();

    private final NativeCellArrayUpdates parent;
    private final Set<IGrid> grids = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private NativeCellArrayUpdates() {
        parent = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static NativeCellArrayUpdates coalesce() {
        return new NativeCellArrayUpdates();
    }

    static void structural(NativeNetworkBlockEntity owner) {
        IGrid grid = gridOf(owner);
        if (grid == null) return;
        NativeCellArrayUpdates scope = ACTIVE.get();
        if (scope != null) scope.grids.add(grid);
        else grid.postEvent(new MENetworkCellArrayUpdate());
    }

    static void nodeStateChanged(NativeNetworkBlockEntity owner) {
        if (!cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) {
            structural(owner);
            return;
        }
        TICK_PENDING.add(owner);
    }

    /** Server tick end: one post per grid, resolved now in case nodes were re-pathed or split meanwhile. */
    public static void flushTick() {
        if (TICK_PENDING.isEmpty()) return;
        List<NativeNetworkBlockEntity> owners = new ArrayList<>(TICK_PENDING);
        TICK_PENDING.clear();
        Set<IGrid> posted = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NativeNetworkBlockEntity owner : owners) {
            if (owner.isInvalid()) continue;
            IGrid grid = gridOf(owner);
            if (grid != null && posted.add(grid)) grid.postEvent(new MENetworkCellArrayUpdate());
        }
    }

    public static void clear() {
        TICK_PENDING.clear();
        ACTIVE.remove();
    }

    private static IGrid gridOf(NativeNetworkBlockEntity owner) {
        ManagedNode node = owner.getMainNode();
        if (!node.isReady()) return null;
        IGridNode gridNode = node.getNode();
        return gridNode == null ? null : gridNode.getGrid();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (parent != null) {
            ACTIVE.set(parent);
            parent.grids.addAll(grids);
            return;
        }
        ACTIVE.remove();
        for (IGrid grid : grids) grid.postEvent(new MENetworkCellArrayUpdate());
    }
}
