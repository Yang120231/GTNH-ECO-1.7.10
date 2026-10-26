package cn.dancingsnow.neoecoae.impl.storage;

import java.math.BigInteger;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Live creative supply for exact orders only. Never infer infinity from a saturated counter. */
public final class ECOCreativeSupply {

    private ECOCreativeSupply() {}

    public static BigInteger extract(GridView grid, AEKey key, BigInteger requested, IActionSource source) {
        if (requested.signum() <= 0) return BigInteger.ZERO;
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (!drive.isMounted() || !drive.isOnline()
                || !(drive.getCellInventory() instanceof ECOCreativeCell creative)
                || !creative.configuredKeys()
                    .contains(key))
                continue;
            // Keep the network's access check. A simulated probe never takes finite stock.
            if (grid.getStorageService()
                .getInventory()
                .extract(key, 1, Actionable.SIMULATE, source) != 1) return BigInteger.ZERO;
            // Creative cells generate resources without changing stored contents. One live delegate
            // probe validates the key; multiplying this supply needs no repeated long-sized calls.
            if (creative.extract(key, 1, Actionable.SIMULATE, source) == 1) return requested;
        }
        return BigInteger.ZERO;
    }
}
