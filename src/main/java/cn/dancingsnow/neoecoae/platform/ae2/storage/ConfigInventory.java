package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import appeng.tile.inventory.IAEStackInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

public final class ConfigInventory {

    private final IAEStackInventory inventory;

    public ConfigInventory(IAEStackInventory inventory) {
        this.inventory = inventory;
    }

    public Set<AEKey> keySet() {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (int index = 0; index < inventory.getSizeInventory(); index++) {
            AEKey key = AEKey.fromNative(inventory.getAEStackInSlot(index));
            if (key != null) keys.add(key);
        }
        return Collections.unmodifiableSet(keys);
    }
}
