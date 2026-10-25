package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

public interface StorageCell extends MEStorage {

    long insert(AEKey key, long amount, Actionable mode, IActionSource source);

    long extract(AEKey key, long amount, Actionable mode, IActionSource source);

    void getAvailableStacks(KeyCounter output);

    default KeyCounter getAvailableStacks() {
        KeyCounter result = new KeyCounter();
        getAvailableStacks(result);
        return result;
    }

    boolean canFitInsideCell();

    boolean isPreferredStorageFor(AEKey key, IActionSource source);

    void persist();

    double getIdleDrain();

    CellState getStatus();

    Component getDescription();
}
