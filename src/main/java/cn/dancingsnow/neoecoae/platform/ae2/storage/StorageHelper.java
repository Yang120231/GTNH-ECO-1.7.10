package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.networking.energy.IEnergySource;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Retains GTNH's real energy debit and returned-insertion convention. */
public final class StorageHelper {

    private StorageHelper() {}

    public static long poweredInsert(IEnergySource energy, MEStorage destination, AEKey key, long amount,
        IActionSource source) {
        if (amount <= 0) return 0;
        int units = key.getAmountPerUnit();
        double available = energy.extractAEPower(
            1 + (amount - 1) / units,
            appeng.api.config.Actionable.SIMULATE,
            appeng.api.config.PowerMultiplier.CONFIG);
        long allowed = Math.min(amount, (long) (available * units + 0.9));
        if (allowed <= 0) return 0;
        long inserted = destination.insert(key, allowed, appeng.api.config.Actionable.MODULATE, source);
        if (inserted < 0 || inserted > allowed)
            throw new IllegalStateException("Invalid powered insertion result: " + inserted);
        if (inserted > 0) energy.extractAEPower(
            1 + (inserted - 1) / units,
            appeng.api.config.Actionable.MODULATE,
            appeng.api.config.PowerMultiplier.CONFIG);
        return inserted;
    }
}
