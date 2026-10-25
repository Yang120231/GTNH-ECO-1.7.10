package cn.dancingsnow.neoecoae.platform.ae2.storage;

import cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode;

/** Mounted through the owning tile's native ICellContainer, never a parallel simulated grid. */
public interface IStorageProvider {

    void mountInventories(IStorageMounts mounts);

    static void requestUpdate(ManagedNode node) {
        node.storageChanged();
    }
}
