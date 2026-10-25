package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Upgrades;
import appeng.items.contents.CellUpgrades;

public final class IUpgradeInventory {

    private final CellUpgrades inventory;

    public IUpgradeInventory(CellUpgrades inventory) {
        this.inventory = inventory;
    }

    public boolean isInstalled(Upgrades upgrade) {
        return inventory.getInstalledUpgrades(upgrade) > 0;
    }
}
