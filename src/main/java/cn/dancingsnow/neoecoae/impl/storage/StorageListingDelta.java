package cn.dancingsnow.neoecoae.impl.storage;

import java.util.function.ObjLongConsumer;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Optional bounded change history; unknown history must fall back to a complete listing. */
public interface StorageListingDelta extends StorageListingVersion {

    /** Reports current absolute amounts, including zero for removals; false must visit nothing. */
    boolean neoecoae$visitListingChanges(long since, long current, ObjLongConsumer<AEKey> visitor);
}
