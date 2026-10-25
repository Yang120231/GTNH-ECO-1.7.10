package cn.dancingsnow.neoecoae.platform.ae2.grid;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;

/** Services stay attached to the owning native host; AE2 owns the node, connections, security and power. */
public final class ManagedNode {

    private final NativeNetworkBlockEntity owner;
    private final NodeListener listener;
    private final Map<Class<?>, Object> services = new HashMap<>();
    private boolean ready;

    ManagedNode(NativeNetworkBlockEntity owner, NodeListener listener) {
        this.owner = owner;
        this.listener = listener;
    }

    public ManagedNode setFlags(GridFlags... flags) {
        owner.getProxy()
            .setFlags(flags);
        return this;
    }

    public <S> ManagedNode addService(Class<S> type, S service) {
        services.put(type, type.cast(service));
        return this;
    }

    public <S> S service(Class<S> type) {
        return type.cast(services.get(type));
    }

    public void setIdlePowerUsage(double power) {
        owner.getProxy()
            .setIdlePowerUsage(power);
    }

    public void setExposedOnSides(java.util.Set<Direction> sides) {
        EnumSet<ForgeDirection> nativeSides = EnumSet.noneOf(ForgeDirection.class);
        sides.forEach(side -> nativeSides.add(side.nativeSide()));
        owner.getProxy()
            .setValidSides(nativeSides);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isOnline() {
        IGridNode node = getNode();
        return ready && node != null && node.isActive();
    }

    public IGridNode getNode() {
        return owner.getProxy()
            .getNode();
    }

    public GridView getGrid() {
        IGridNode node = getNode();
        return node == null ? null : GridView.of(node.getGrid());
    }

    public void ifPresent(Consumer<GridView> action) {
        GridView grid = getGrid();
        if (grid != null) action.accept(grid);
    }

    public void ifPresent(BiConsumer<GridView, IGridNode> action) {
        GridView grid = getGrid();
        if (grid != null) action.accept(grid, getNode());
    }

    public void storageChanged() {
        owner.refreshNativeStorage();
    }

    void ready(boolean value) {
        ready = value;
    }

    void gridChanged() {
        listener.onGridChanged(owner, getNode());
    }

    void stateChanged(NodeListener.State state) {
        listener.onStateChanged(owner, state);
    }
}
