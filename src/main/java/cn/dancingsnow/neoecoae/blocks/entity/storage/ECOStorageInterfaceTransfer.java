package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferQuarantine;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Moves storage contents in full-key batches using AE2 ME IO Port transfer semantics. */
final class ECOStorageInterfaceTransfer {

    private static final String REMAINDER_TAG = "neoecoae_transfer_remainder";
    private final ECOStorageSystemBlockEntity host;
    private final ECOIOPortTransfer ioPortTransfer = new ECOIOPortTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private CombinedStorage cachedCombinedStorage;
    private final List<MEStorage> candidateCells = new ArrayList<>();
    private CompoundTag unresolvedRemainder;

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

    boolean hasRemainder() {
        return ioPortTransfer.remainder() != null || unresolvedRemainder != null;
    }

    boolean hasTransferState() {
        return hasRemainder() || ioPortTransfer.hasUncertainTransfers();
    }

    void saveRemainder(CompoundTag tag, HolderLookup.Provider registries) {
        ioPortTransfer.saveReceipts(tag, registries);
        if (unresolvedRemainder != null) tag.put(REMAINDER_TAG, unresolvedRemainder.copy());
        else if (ioPortTransfer.remainder() != null)
            tag.put(REMAINDER_TAG, GenericStack.writeTag(registries, ioPortTransfer.remainder()));
        else tag.remove(REMAINDER_TAG);
    }

    void readRemainder(CompoundTag tag, HolderLookup.Provider registries) {
        ioPortTransfer.loadReceipts(tag, registries);
        CompoundTag saved = tag.getCompound(REMAINDER_TAG);
        GenericStack stack = GenericStack.readTag(registries, saved);
        ioPortTransfer.restoreRemainder(stack);
        unresolvedRemainder = stack == null && !saved.isEmpty() ? saved.copy() : null;
    }

    void settleRemainder() {
        GenericStack remainder = ioPortTransfer.remainder();
        if (remainder == null || !host.isFormed()) return;
        CombinedStorage storage = getStorageInterfaceHostStorage();
        if (storage == null) return;
        var source = IActionSource.ofMachine(host);
        ioPortTransfer.settleRemainder(stack -> storage.recover(stack, source), host::setChanged);
    }

    long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (hasRemainder() || !host.isFormed()
            || !storageInterface.isStorageTransferMode()
            || !storageInterface.isTargetOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode()
            .getGrid();
        if (grid == null) return 0L;

        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) return 0L;

        long moved;
        try {
            moved = ioPortTransfer.transfer(
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
                PlanningSettings.Storage.storageOutputFlushThreshold,
                host::setChanged);
        } finally {
            if (hasRemainder()) host.setChanged();
        }
        if (moved > 0L) {
            host.setChanged();
            host.markForUpdate();
        }
        return moved;
    }

    @Nullable
    CombinedStorage getStorageInterfaceHostStorage() {
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
                long offered = amount - inserted;
                try {
                    inserted += ECOTransferQuarantine.check(inventory.insert(key, offered, mode, source), offered);
                } catch (RuntimeException failure) {
                    if (mode == Actionable.MODULATE && inserted > 0L)
                        throw new ECOTransferQuarantine.ConfirmedPrefix(inserted, failure);
                    throw failure;
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
                long offered = amount - extracted;
                try {
                    extracted += ECOTransferQuarantine.check(inventory.extract(key, offered, mode, source), offered);
                } catch (RuntimeException failure) {
                    if (mode == Actionable.MODULATE && extracted > 0L)
                        throw new ECOTransferQuarantine.ConfirmedPrefix(extracted, failure);
                    throw failure;
                }
            }
            return extracted;
        }

        long recover(GenericStack stack, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            for (MEStorage inventory : inventories) {
                long accepted = inventory instanceof IECOStorageMigrationCell cell && cell.isInfiniteStorageEligible()
                    ? cell.insertForMigration(stack.what(), stack.amount(), Actionable.MODULATE, source)
                    : inventory instanceof ECOInfiniteStorage
                        ? inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE, source)
                        : 0L;
                ECOTransferQuarantine.check(accepted, stack.amount());
                if (accepted > 0L) return accepted;
            }
            return 0L;
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
