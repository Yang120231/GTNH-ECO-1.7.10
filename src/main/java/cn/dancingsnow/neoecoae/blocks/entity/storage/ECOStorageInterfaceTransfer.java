package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;

/** Moves storage contents in full-key batches using AE2 ME IO Port transfer semantics. */
final class ECOStorageInterfaceTransfer {

    private final ECOStorageSystemBlockEntity host;
    private final ECOIOPortTransfer ioPortTransfer = new ECOIOPortTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private CombinedStorage cachedCombinedStorage;

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidateInfiniteStorageView() {
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
    }

    long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (!host.isFormed() || !storageInterface.isStorageTransferMode() || !storageInterface.isTargetOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode()
            .getGrid();
        if (grid == null) return 0L;

        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) return 0L;

        long moved = ioPortTransfer.transfer(
            grid,
            hostStorage,
            storageInterface.isStorageInputMode(),
            storageInterface.isIgnoringCreativeStorageInput(),
            storageInterface.isExcludingStorageBusContainers(),
            IActionSource.ofMachine(storageInterface),
            storageInterface::acceptsStorageResource);
        if (moved > 0L) {
            host.setChanged();
            host.markForUpdate();
        }
        return moved;
    }

    @Nullable
    MEStorage getStorageInterfaceHostStorage() {
        if (host.storageHostMode()
            .isTransitioning()
            || host.storageHostMode()
                .isInfiniteState() && (host.isInfiniteExitRequested() || !host.canUseHostDomainStorage())) {
            return null;
        }
        List<MEStorage> cells = new ArrayList<>();
        if (host.canUseHostDomainStorage()) {
            ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
            if (engine != cachedStorageEngine) {
                cachedStorageEngine = engine;
                cachedInfiniteStorage = engine == null ? null : host.createInfiniteStorageView(engine);
            }
            if (cachedInfiniteStorage != null) cells.add(cachedInfiniteStorage);
        }
        if (host.getCluster() == null) return null;

        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null && host.getTier()
                .compareTo(cell.getTier()) >= 0 && !host.isInfiniteMemberCell(drive.getCellStack())) {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) {
            cachedCombinedStorage = null;
            return null;
        }
        if (cachedCombinedStorage == null || !cachedCombinedStorage.inventories()
            .equals(cells)) {
            cachedCombinedStorage = new CombinedStorage(
                cells,
                cn.dancingsnow.neoecoae.platform.minecraft.Component.literal(
                    host.getBlockState()
                        .getBlock()
                        .getLocalizedName()),
                () -> !host.storageHostMode()
                    .isInfiniteState() || host.canInsertIntoInfiniteDomain());
        }
        return cachedCombinedStorage;
    }

    @com.github.bsideup.jabel.Desugar
    record CombinedStorage(List<MEStorage> inventories,
        cn.dancingsnow.neoecoae.platform.minecraft.Component description, BooleanSupplier allowAccess)
        implements MEStorage, CombinedExactAmountSource {

        CombinedStorage(List<MEStorage> inventories, cn.dancingsnow.neoecoae.platform.minecraft.Component description) {
            this(inventories, description, () -> true);
        }

        @Override
        public long insert(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            long inserted = 0L;
            for (int pass = 0; pass < 2 && inserted < amount; pass++) {
                for (MEStorage inventory : inventories) {
                    if (inserted >= amount) break;
                    boolean bulk = inventory instanceof IECOStorageCell cell && cell.prioritizesMarkedInserts();
                    if (bulk != (pass == 0)) continue;
                    inserted += inventory.insert(key, amount - inserted, mode, source);
                }
            }
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            long extracted = 0L;
            for (MEStorage inventory : inventories) {
                if (extracted >= amount) break;
                extracted += inventory.extract(key, amount - extracted, mode, source);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (!allowAccess.getAsBoolean()) return;
            var accumulator = new cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator();
            for (MEStorage inventory : inventories) {
                accumulator.append(inventory, out, inventory::getAvailableStacks);
            }
        }

        @Override
        public boolean neoecoae$hasSaturatingListing() {
            return true;
        }

        @Override
        public void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor) {
            if (!allowAccess.getAsBoolean()) return;
            Map<AEKey, ExactAmount> totals = new HashMap<>();
            for (MEStorage inventory : inventories) {
                KeyCounter contribution = new KeyCounter();
                if (inventory instanceof CombinedExactAmountSource source) {
                    source.neoecoae$listWithExactAmounts(
                        contribution,
                        (key, amount) -> totals.merge(key, amount, ExactAmount::add));
                } else {
                    inventory.getAvailableStacks(contribution);
                    ExactAmountCollector.visitAmounts(
                        inventory,
                        contribution,
                        (key, amount) -> totals.merge(key, amount, ExactAmount::add));
                }
                SaturatingStackAccumulator.addAll(out, contribution);
            }
            totals.forEach(visitor);
        }

        @Override
        public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
            neoecoae$listWithExactAmounts(new KeyCounter(), visitor);
        }

        @Override
        public cn.dancingsnow.neoecoae.platform.minecraft.Component getDescription() {
            return description;
        }
    }
}
