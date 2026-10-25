package cn.dancingsnow.neoecoae.platform.ae2.grid;

import appeng.api.networking.IGridNode;

public interface NodeListener<T extends NativeNetworkBlockEntity> {

    enum State {
        GRID_BOOT,
        POWER,
        CHANNEL
    }

    void onGridChanged(T owner, IGridNode node);

    default void onStateChanged(T owner, State state) {
        owner.onMainNodeStateChanged(state);
    }
}
