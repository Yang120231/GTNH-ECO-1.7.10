package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.HashSet;
import java.util.Set;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.items.storage.ItemCreativeStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.grid.NEMachineLookup;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCell;

/** Captured on the server thread, preserving unlimited-source identity alongside AE2's long counter. */
public final class ECOPlannerInventory {

    private ECOPlannerInventory() {}

    public static PlannerInventorySnapshot capture(GridView grid) {
        var inventory = grid.getStorageService()
            .getInventory()
            .getAvailableStacks();
        Set<AEKey> unbounded = collectUnboundedKeys(grid);
        return PlannerInventorySnapshot.of(inventory, unbounded);
    }

    /**
     * Returns keys supplied by an unbounded storage source in this grid. The ordinary AE2
     * aggregate only exposes a saturated long amount, so callers that move physical stock must
     * preserve this side-channel and never persist those generated resources.
     */
    public static Set<AEKey> collectUnboundedKeys(GridView grid) {
        Set<AEKey> unbounded = new HashSet<>();
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (!drive.isMounted() || !drive.isOnline()) continue;
            var cell = drive.getCellInventory();
            if (cell instanceof ECOCreativeCell creative) unbounded.addAll(creative.configuredKeys());
            if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);
        }
        for (IChestOrDrive host : NEMachineLookup.getMachines(grid, IChestOrDrive.class)) {
            if (!host.isPowered()) continue;
            for (int slot = 0; slot < host.getCellCount(); slot++) {
                StorageCell cell = cn.dancingsnow.neoecoae.platform.ae2.storage.NativeDriveReaders.cell(host, slot);
                if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);

                if (cell != null && cn.dancingsnow.neoecoae.platform.ae2.storage.NativeDriveReaders
                    .cellItem(host, slot) instanceof ItemCreativeStorageCell) {
                    // Native ME drives expose AE2 creative cells as StorageCell and report only
                    // Integer.MAX_VALUE per key. Keep their actual unlimited semantics in the
                    // planner snapshot instead of mistaking that display count for finite stock.
                    unbounded.addAll(
                        cell.getAvailableStacks()
                            .keySet());
                }
            }
        }
        return unbounded;
    }

    private static void markUnbounded(ExactAmountSource source, Set<AEKey> target) {
        source.neoecoae$visitExactAmounts((key, amount) -> { if (amount.infinite()) target.add(key); });
    }
}
