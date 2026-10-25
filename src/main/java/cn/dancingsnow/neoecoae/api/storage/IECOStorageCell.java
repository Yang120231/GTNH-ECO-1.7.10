package cn.dancingsnow.neoecoae.api.storage;

import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCell;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public interface IECOStorageCell extends StorageCell {

    StorageTierDescriptor getTier();

    /** Marked bulk cells receive inserts before ordinary cells in the same host. */
    default boolean prioritizesMarkedInserts() {
        return false;
    }

    /**
     * @return cellType for display in gui
     */
    ECOCellType getCellType();

    long getStoredItemTypes();

    long getTotalItemTypes();

    default boolean hasInfiniteTypeCapacity() {
        return false;
    }

    /**
     * Whether this storage cell may be migrated into the ECO infinite storage domain.
     * Specialized finite cells can opt out while remaining usable in normal drives.
     */
    default boolean isInfiniteStorageEligible() {
        return true;
    }

    long getUsedBytes();

    long getTotalBytes();
}
