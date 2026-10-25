package cn.dancingsnow.neoecoae.api.storage;

import java.util.Iterator;

import net.minecraft.world.WorldServer;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * Optional-neutral contract for storage cells that can participate in resumable infinite-domain migration.
 * Implementations may belong to optional integrations; common storage code must depend only on this contract.
 */
public interface IECOStorageMigrationCell extends IECOStorageCell {

    void getMigrationStacks(KeyCounter out);

    default long getMigrationAmount(AEKey key) {
        KeyCounter contents = new KeyCounter();
        getMigrationStacks(contents);
        return contents.get(key);
    }

    /** Flushes contents before the controller saves the source seal or destination receipt to its chunk. */
    default void persistMigrationContents(WorldServer level) {
        persist();
    }

    default Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        KeyCounter contents = new KeyCounter();
        getMigrationStacks(contents);
        return contents.iterator();
    }

    void clearMigrationStacks();

    long insertForMigration(AEKey what, long amount, Actionable mode, IActionSource source);

    long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents, long simulatedTypes,
        long simulatedAmount);

    long getUsedBytesForMigration(KeyCounter simulatedContents);
}
