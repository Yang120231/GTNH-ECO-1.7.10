package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.function.LongSupplier;

import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;

/** Limits storage-bus exclusion to the current ECO interface transfer, including nested networks. */
public final class StorageBusTransferFilter {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private StorageBusTransferFilter() {}

    /** Implemented by AE2's mounted storage-bus wrapper, rather than the external container itself. */
    public interface StorageBusInventory {
    }

    /** Cheap guard for every network operation outside an ECO interface transfer. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static boolean excludes(MEStorage storage) {
        return Boolean.TRUE.equals(ACTIVE.get()) && storage instanceof StorageBusInventory;
    }

    public static long transfer(boolean excludeStorageBuses, LongSupplier action) {
        Boolean previous = ACTIVE.get();
        if (excludeStorageBuses) ACTIVE.set(true);
        try {
            return action.getAsLong();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }
}
