package cn.dancingsnow.neoecoae.platform.ae2.grid;

import appeng.api.networking.IGridNode;

public class BlockNodeListener<T extends NativeNetworkBlockEntity> implements NodeListener<T> {

    @Override
    public void onGridChanged(T owner, IGridNode node) {
        owner.onMainNodeStateChanged(State.GRID_BOOT);
    }
}
