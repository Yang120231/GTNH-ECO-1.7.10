package cn.dancingsnow.neoecoae.platform.ae2.storage;

import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;

@FunctionalInterface
public interface ISaveProvider {

    void saveChanges();

    default void notifyPersistence() {
        saveChanges();
    }

    /** Kept for existing integrations; the cell no longer defers quantity writes. */
    @Deprecated
    default boolean defersCellPersistence() {
        return false;
    }

    /** A mounted handler must not accept transfers after its stack or chunk has left the host. */
    default boolean isCellActive(IECOStorageCell cell) {
        return true;
    }

    /** True for an exclusive host that takes detached disk/packet snapshots and freezes the cell on handoff. */
    default boolean snapshotsCellStack() {
        return false;
    }
}
