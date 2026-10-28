package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Identity/amount boundary shared by native channel views and the imported inventories. */
public interface MEStorage {

    default long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    long extract(AEKey key, long amount, Actionable mode, IActionSource source);

    void getAvailableStacks(KeyCounter output);

    default KeyCounter getAvailableStacks() {
        KeyCounter output = new KeyCounter();
        getAvailableStacks(output);
        return output;
    }

    /**
     * One key's listed amount, as {@link #getAvailableStacks(KeyCounter)} would report it. rv3 asks handlers for single
     * keys (watchers, crafting, the network's point query); indexed storages override this full-listing default.
     */
    default long getAvailableAmount(AEKey key) {
        return key == null ? 0 : getAvailableStacks().get(key);
    }

    /** The listing without materialising a counter; indexed storages iterate their own table. */
    default void visitAvailableStacks(java.util.function.ObjLongConsumer<AEKey> visitor) {
        for (var entry : getAvailableStacks()) visitor.accept(entry.getKey(), entry.getLongValue());
    }

    default boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return false;
    }

    Component getDescription();
}
