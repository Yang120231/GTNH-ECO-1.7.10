package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.networking.storage.IStorageGrid;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

public final class StorageServiceView {

    private final NativeNetworkStorage inventory;

    public StorageServiceView(IStorageGrid grid) {
        inventory = new NativeNetworkStorage(grid);
    }

    public MEStorage getInventory() {
        return inventory;
    }

    public KeyCounter getCachedInventory() {
        return inventory.getAvailableStacks();
    }
}
