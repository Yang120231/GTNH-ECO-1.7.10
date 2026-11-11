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
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Moves storage contents in full-key batches using AE2 ME IO Port transfer semantics. */
final class ECOStorageInterfaceTransfer {

    private final ECOStorageSystemBlockEntity host;
    private final ECOIOPortTransfer ioPortTransfer = new ECOIOPortTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private CombinedStorage cachedCombinedStorage;
    private final List<MEStorage> candidateCells = new ArrayList<>();

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidateInfiniteStorageView() {
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
        cachedCombinedStorage = null;
        candidateCells.clear();
        ioPortTransfer.reset();
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
            storageInterface.storageResourceFilter(),
            host.getLevel() == null ? 0L
                : host.getLevel()
                    .getGameTime(),
            PlanningSettings.Storage.storageOutputTransferIntervalTicks,
            PlanningSettings.Storage.storageOutputFlushThreshold);
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
        List<MEStorage> cells = candidateCells;
        cells.clear();
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
        if (cachedCombinedStorage == null || !cachedCombinedStorage.hasInventories(cells)) {
            cachedCombinedStorage = new CombinedStorage(
                cells,
                Component.literal(
                    host.getBlockState()
                        .getBlock()
                        .getLocalizedName()),
                () -> !host.storageHostMode()
                    .isInfiniteState() || host.canInsertIntoInfiniteDomain());
        }
        return cachedCombinedStorage;
    }

    static final class CombinedStorage implements MEStorage, CombinedExactAmountSource, NetworkInventoryCache.Mounts {

        private final List<MEStorage> inventories;
        private final Component description;
        private final BooleanSupplier allowAccess;
        private final List<MEStorage> itemInserts;
        private final List<MEStorage> fluidInserts;

        CombinedStorage(List<MEStorage> inventories, Component description, BooleanSupplier allowAccess) {
            this.inventories = java.util.Collections.unmodifiableList(new ArrayList<>(inventories));
            this.description = description;
            this.allowAccess = allowAccess;
            itemInserts = insertCandidates(AEKeyType.items());
            fluidInserts = insertCandidates(AEKeyType.fluids());
        }

        boolean hasInventories(List<MEStorage> current) {
            if (inventories.size() != current.size()) return false;
            for (int i = 0; i < inventories.size(); i++) if (inventories.get(i) != current.get(i)) return false;
            return true;
        }

        private List<MEStorage> insertCandidates(AEKeyType requested) {
            List<MEStorage> result = new ArrayList<>();
            for (MEStorage inventory : inventories) {
                if (inventory.getClass() == ECOStorageCell.class) {
                    var type = ((ECOStorageCell) inventory).getKeyType();
                    if ((type == AEKeyType.items() || type == AEKeyType.fluids()) && type != requested) continue;
                }
                result.add(inventory);
            }
            return result;
        }

        @Override
        public long neoecoae$mountGeneration() {
            return allowAccess.getAsBoolean() ? 0L : 1L;
        }

        @Override
        public boolean neoecoae$withMounts(java.util.function.Consumer<List<MEStorage>> action) {
            action.accept(allowAccess.getAsBoolean() ? inventories : java.util.Collections.emptyList());
            return true;
        }

        CombinedStorage(List<MEStorage> inventories, Component description) {
            this(inventories, description, () -> true);
        }

        @Override
        public long insert(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            long inserted = 0L;
            var candidates = key instanceof AEItemKey ? itemInserts
                : key instanceof AEFluidKey ? fluidInserts : inventories;
            for (MEStorage inventory : candidates) {
                if (inserted >= amount) break;
                inserted += inventory.insert(key, amount - inserted, mode, source);
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
        public Component getDescription() {
            return description;
        }
    }
}
