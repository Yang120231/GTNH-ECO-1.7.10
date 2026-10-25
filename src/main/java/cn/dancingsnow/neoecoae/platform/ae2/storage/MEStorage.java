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

    default boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return false;
    }

    Component getDescription();
}
