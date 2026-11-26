package cn.dancingsnow.neoecoae.impl.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ConfigInventory;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ECOPartitionList;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IUpgradeInventory;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;

public class ECOStorageCell implements IECOStorageMigrationCell, StorageListingVersion {

    private static final Logger LOGGER = LogManager.getLogger(ECOStorageCell.class);
    // Only notifications are retried. The corresponding inventory changes already have a receipt and a snapshot.
    private static final Set<ECOStorageCell> FAILED_NOTIFICATIONS = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final long WRITE_REFUSED = -1;

    @Override
    public long neoecoae$listingVersion() {
        // Custom cell subclasses may expose dynamic resources independently of this quantity table.
        if (getClass() != ECOStorageCell.class) return UNKNOWN;
        return cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack) ? -1L
            : contentRevision();
    }

    @Nullable
    private final ISaveProvider container;
    private final IBasicECOCellItem cellType;
    private final AEKeyType keyType;

    public AEKeyType getKeyType() {
        return keyType;
    }

    // 过滤器
    private final ECOPartitionList partitionList;

    public ECOPartitionList getPartitionList() {
        return partitionList;
    }

    private final IncludeExclude partitionListMode;

    public IncludeExclude getPartitionListMode() {
        return partitionListMode;
    }

    private final boolean hasVoidUpgrade;

    /** Protected so specialised cells (for example the infinite resource cell) can read the cell stack. */
    protected final ItemStack cellStack;

    private final int maxItemTypes;
    private int storedItems;
    private long storedItemCount;

    public long getStoredItemCount() {
        refreshContents();
        return storedItemCount;
    }

    private Object2LongMap<AEKey> storedAmounts;
    private long contentRevision;

    public long contentRevision() {
        refreshContents();
        return contentRevision;
    }

    private CellComponents.Contents contents;
    private boolean reportedEncodingFailure;
    private boolean notificationFailed;
    private final StorageTierDescriptor tier;

    public StorageTierDescriptor getTier() {
        return tier;
    }

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this.container = container;
        this.cellStack = cellStack;

        if (cellStack.getItem() instanceof IBasicECOCellItem c) {
            keyType = c.getKeyType();
            maxItemTypes = c.getTotalTypes();
            readContents();
            this.cellType = c;
            this.tier = c.getTier();

            // 根据已安装的升级和配置的过滤器更新分区列表和模式。
            var builder = ECOPartitionList.builder();

            var upgrades = getUpgradesInventory();
            var config = getConfigInventory();

            boolean hasInverter = upgrades.isInstalled(appeng.api.config.Upgrades.INVERTER);
            boolean isFuzzy = upgrades.isInstalled(appeng.api.config.Upgrades.FUZZY);
            if (isFuzzy) {
                builder.fuzzyMode(c.getFuzzyMode(cellStack));
            }

            builder.addAll(config.keySet());

            partitionListMode = (hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            partitionList = builder.build();

            this.hasVoidUpgrade = upgrades.isInstalled(appeng.api.config.Upgrades.VOID_OVERFLOW);
        } else {
            throw new IllegalArgumentException("itemStack must be an NativeStorageCellItem");
        }
    }

    @Override
    public CellState getStatus() {
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    public long getRemainingItemCount() {
        return NEMath.saturatingAdd(
            NEMath.saturatingMultiply(getFreeBytes(), Math.max(1L, keyType.getAmountPerByte())),
            getUnusedItemCount());
    }

    public long getFreeBytes() {
        return Math.max(0L, this.getTotalBytes() - this.getUsedBytes());
    }

    public int getUnusedItemCount() {
        int amountPerByte = Math.max(1, keyType.getAmountPerByte());
        final int div = (int) (this.getStoredItemCount() % amountPerByte);

        if (div == 0) {
            return 0;
        }

        return amountPerByte - div;
    }

    public long getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        long storedAmount = Math.max(0L, getStoredItemCount());
        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long bytesForItems = ceilDivide(storedAmount, amountPerByte);
        return NEMath.saturatingAdd(NEMath.saturatingMultiply(getStoredItemTypes(), getBytesPerType()), bytesForItems);
    }

    public long getTotalBytes() {
        return cellType.getBytes();
    }

    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getRemainingItemTypes() {
        var basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        var baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    private boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
            || bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0)
            && this.getRemainingItemTypes() > 0;
    }

    public long getStoredItemTypes() {
        refreshContents();
        return storedItems;
    }

    public static boolean canStoreKeyInsideStorageCell(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            // Only probed, never mutated: skip the per-insert ItemStack copy.
            var cellInv = StorageCells.getCellInventory(itemKey.getReadOnlyStack(), null);
            return cellInv == null || cellInv.canFitInsideCell();
        }
        return true;
    }

    protected Object2LongMap<AEKey> getCellItems() {
        refreshContents();
        return storedAmounts;
    }

    private void refreshContents() {
        if (!contents.isCurrent(cellStack)) {
            readContents();
            markContentChanged();
        }
    }

    private void readContents() {
        contents = new CellComponents.Contents(cellStack);
        storedAmounts = contents.amounts();
        storedItems = storedAmounts.size();
        storedItemCount = totalAmount();
    }

    private long totalAmount() {
        return storedAmounts.values()
            .longStream()
            .reduce(0L, NEMath::saturatingAdd);
    }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    /** The NBT is already current. A consumer can now retain this version while the drive keeps running. */
    @Override
    public void persist() {
        freezeSnapshot();
    }

    public final void freezeSnapshot() {
        contents.freeze();
    }

    private boolean canChange() {
        return container == null || container.isCellActive(this);
    }

    /** Bulk edit hook for specialised inventories which modify the protected quantity table themselves. */
    protected void saveChanges() {
        if (!canChange()) throw new IllegalStateException("Cannot change a detached ECO cell inventory");
        var stacks = new ArrayList<GenericStack>(storedAmounts.size());
        storedAmounts.forEach((key, amount) -> { if (amount > 0) stacks.add(new GenericStack(key, amount)); });
        try {
            if (stacks.isEmpty()) CellComponents.clearStacks(cellStack);
            else CellComponents.writeStacks(cellStack, stacks);
        } finally {
            // A failed bulk encoding leaves the original stack untouched; restore the live view from that version.
            readContents();
        }
        committed();
    }

    private boolean setAmount(AEKey key, long previous, long next) {
        if (!canChange() || !contents.isCurrent(cellStack)) return false;
        CompoundTag encoded = null;
        if (previous == 0) {
            long revision = contentRevision;
            try {
                encoded = CellComponents.encodeKey(key);
            } catch (RuntimeException failure) {
                if (!reportedEncodingFailure)
                    LOGGER.error("Refusing unencodable ECO cell key before acceptance", failure);
                reportedEncodingFailure = true;
                return false;
            }
            // A third-party codec can reenter storage or detach the drive. Its result cannot commit a stale offer.
            if (revision != contentRevision || !contents.isCurrent(cellStack) || !canChange()) return false;
        }
        contents.set(cellStack, key, next, encoded, container != null && container.snapshotsCellStack());
        if (next == 0) storedAmounts.removeLong(key);
        else storedAmounts.put(key, next);
        storedItems = storedAmounts.size();
        storedItemCount = next >= previous ? NEMath.saturatingAdd(storedItemCount, next - previous)
            : storedItemCount == Long.MAX_VALUE ? totalAmount() : storedItemCount - (previous - next);
        committed();
        return true;
    }

    private void committed() {
        markContentChanged();
        notifyHost();
    }

    private void notifyHost() {
        if (container == null) return;
        try {
            if (canChange()) container.notifyPersistence();
        } catch (RuntimeException failure) {
            if (!notificationFailed) LOGGER.error("ECO cell committed; retrying host notification only", failure);
            notificationFailed = true;
            FAILED_NOTIFICATIONS.add(this);
            return;
        }
        if (notificationFailed) {
            notificationFailed = false;
            FAILED_NOTIFICATIONS.remove(this);
        }
    }

    public static void retryHostNotifications() {
        retryHostNotifications(16);
    }

    private static void retryHostNotifications(int limit) {
        ECOStorageCell[] pending;
        synchronized (FAILED_NOTIFICATIONS) {
            if (FAILED_NOTIFICATIONS.isEmpty()) return;
            pending = new ECOStorageCell[Math.min(limit, FAILED_NOTIFICATIONS.size())];
            var it = FAILED_NOTIFICATIONS.iterator();
            for (int i = 0; i < pending.length; i++) {
                pending[i] = it.next();
                it.remove();
            }
        }
        // Persistent failures go to the back. Never run foreign callbacks while holding the queue lock.
        for (ECOStorageCell cell : pending) if (cell.notificationFailed) cell.notifyHost();
    }

    public static void stopHostNotifications() {
        retryHostNotifications(Integer.MAX_VALUE);
        if (!FAILED_NOTIFICATIONS.isEmpty())
            LOGGER.error("{} ECO cell host notifications failed at shutdown", FAILED_NOTIFICATIONS.size());
        FAILED_NOTIFICATIONS.clear();
    }

    /** Specialized cells may accept multiple channels with the same amount-per-byte accounting. */
    protected boolean acceptsKey(AEKey key) {
        return keyType.contains(key);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        what = cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory.canonicalize(what);
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        if (!canChange() || amount <= 0 || !acceptsKey(what)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(cellStack, what)) {
            return 0;
        }

        // 执行常规插入逻辑，然后对返回值应用虚空升级效果。
        long inserted = innerInsert(what, amount, mode);
        if (inserted == WRITE_REFUSED) return 0; // A codec failure is never void-overflow acceptance.

        // 当虚空卡用于（已满的）未格式化元件时，确保不会虚空掉元件未存储且无法存储的物品。
        if (partitionList.isEmpty() && hasVoidUpgrade && !canHoldNewItem()) {
            return getCellItems().containsKey(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    /** Inserts for a lossless migration without applying the void upgrade's reported acceptance. */
    public long insertForMigration(AEKey what, long amount, Actionable mode) {
        what = cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory.canonicalize(what);
        if (!canChange() || amount <= 0 || !acceptsKey(what)) {
            return 0;
        }
        if (!partitionList.matchesFilter(what, partitionListMode) || cellType.isBlackListed(cellStack, what)) {
            return 0;
        }
        return Math.max(0, innerInsert(what, amount, mode));
    }

    /** Marks an in-memory mutation from a specialised cell implementation. */
    protected final void markContentChanged() {
        contentRevision = contentRevision == Long.MAX_VALUE ? 0L : contentRevision + 1L;
    }

    /** Legacy extension hook: inventory commits are no longer deferred. */
    @Deprecated
    protected final boolean deferMutationBatch() {
        return false;
    }

    @Override
    public long insertForMigration(AEKey what, long amount, Actionable mode, IActionSource source) {
        what = cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory.canonicalize(what);
        return insertForMigration(what, amount, mode);
    }

    /**
     * Computes migration capacity against a caller-owned inventory snapshot. A restore preflight must not mutate a
     * real cell because some cell implementations back copied ItemStacks with world-level storage.
     */
    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents) {
        if (simulatedContents == null) return 0L;

        long currentAmount = simulatedContents.get(what);
        long storedTypes = 0L;
        long storedItemCount = 0L;
        for (Object2LongMap.Entry<AEKey> entry : simulatedContents) {
            if (entry.getLongValue() > 0L) {
                storedTypes = NEMath.saturatingAdd(storedTypes, 1L);
                storedItemCount = NEMath.saturatingAdd(storedItemCount, entry.getLongValue());
            }
        }

        return simulateInsertForMigration(what, amount, currentAmount, storedTypes, storedItemCount);
    }

    public long simulateInsertForMigration(AEKey what, long amount, long currentAmount, long storedTypes,
        long storedItemCount) {
        if (amount <= 0L || !acceptsKey(what)
            || !partitionList.matchesFilter(what, partitionListMode)
            || cellType.isBlackListed(cellStack, what)) return 0L;

        // A key already present in the simulated contents was accepted when it was inserted.
        // Avoid probing nested-cell handlers again during migration preflight.
        if (currentAmount <= 0L && !canStoreKeyInsideStorageCell(what)) return 0L;

        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long unusedItemCount = storedItemCount % amountPerByte == 0L ? 0L
            : amountPerByte - storedItemCount % amountPerByte;
        long bytesForItems = ceilDivide(storedItemCount, amountPerByte);
        long typeBytes = NEMath.saturatingMultiply(storedTypes, getBytesPerType());
        long usedBytes = NEMath.saturatingAdd(typeBytes, bytesForItems);
        long freeBytes = Math.max(0L, getTotalBytes() - usedBytes);
        long remainingItemCount = NEMath
            .saturatingAdd(NEMath.saturatingMultiply(freeBytes, amountPerByte), unusedItemCount);
        long remainingTypes = Math.min(
            Math.max(0L, getTotalItemTypes() - Math.min(getTotalItemTypes(), storedTypes)),
            getBytesPerType() <= 0 ? 0L : freeBytes / getBytesPerType());

        if (currentAmount <= 0L) {
            boolean canHoldNewType = (freeBytes > getBytesPerType()
                || freeBytes == getBytesPerType() && unusedItemCount > 0L) && remainingTypes > 0L;
            if (!canHoldNewType) {
                return 0L;
            }
            remainingItemCount = Math
                .max(0L, remainingItemCount - NEMath.saturatingMultiply(getBytesPerType(), amountPerByte));
        }

        return Math.min(amount, remainingItemCount);
    }

    @Override
    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents, long simulatedTypes,
        long simulatedAmount) {
        return simulateInsertForMigration(what, amount, simulatedContents.get(what), simulatedTypes, simulatedAmount);
    }

    @Override
    public long getUsedBytesForMigration(KeyCounter simulatedContents) {
        if (simulatedContents == null) {
            return 0L;
        }
        long storedTypes = 0L;
        long storedItemCount = 0L;
        for (Object2LongMap.Entry<AEKey> entry : simulatedContents) {
            if (entry.getLongValue() > 0L) {
                storedTypes = NEMath.saturatingAdd(storedTypes, 1L);
                storedItemCount = NEMath.saturatingAdd(storedItemCount, entry.getLongValue());
            }
        }
        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long unusedItemCount = storedItemCount % amountPerByte == 0L ? 0L
            : amountPerByte - storedItemCount % amountPerByte;
        long bytesForItems = ceilDivide(storedItemCount, amountPerByte);
        return NEMath.saturatingAdd(NEMath.saturatingMultiply(storedTypes, getBytesPerType()), bytesForItems);
    }

    private static long ceilDivide(long amount, long divisor) {
        long quotient = amount / divisor;
        return amount % divisor == 0L ? quotient : quotient + 1L;
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        var currentAmount = this.getCellItems()
            .getLong(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!canStoreKeyInsideStorageCell(what)) {
                return 0;
            }
            if (!canHoldNewItem()) {
                // 无更多类型空间
                return 0;
            }

            remainingItemCount = Math.max(
                0L,
                remainingItemCount - NEMath.saturatingMultiply(this.getBytesPerType(), keyType.getAmountPerByte()));
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        remainingItemCount = Math.max(0, Math.min(Long.MAX_VALUE - currentAmount, remainingItemCount));

        if (amount > remainingItemCount) {
            amount = remainingItemCount;
        }
        if (amount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE && !setAmount(what, currentAmount, currentAmount + amount))
            return WRITE_REFUSED;

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        what = cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory.canonicalize(what);
        if (!canChange() || amount <= 0L
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        long current = getCellItems().getLong(what);
        long extracted = Math.min(current, amount);
        if (extracted > 0 && mode == Actionable.MODULATE && !setAmount(what, current, current - extracted)) return 0;
        return extracted;
    }

    @Override
    public boolean canFitInsideCell() {
        return !cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
            && getAvailableStacks().isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        getMigrationStacks(out);
    }

    @Override
    public long getAvailableAmount(AEKey key) {
        if (key == null || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack))
            return 0;
        return getCellItems().getLong(key);
    }

    @Override
    public void visitAvailableStacks(java.util.function.ObjLongConsumer<AEKey> visitor) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        for (var entry : Object2LongMaps.fastIterable(this.getCellItems()))
            visitor.accept(entry.getKey(), entry.getLongValue());
    }

    @Override
    public void getMigrationStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(this.getCellItems())) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public java.util.Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        return getCellItems().object2LongEntrySet()
            .iterator();
    }

    @Override
    public long getMigrationAmount(AEKey key) {
        return getCellItems().getLong(key);
    }

    @Override
    public Component getDescription() {
        return Component.literal(cellStack.getDisplayName());
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        boolean used = !this.getCellItems()
            .isEmpty() && this.insert(what, 1, Actionable.SIMULATE, source) == 1;
        boolean sameItem = this.extract(what, 1, Actionable.SIMULATE, source) > 0;
        return used || sameItem;
    }

    @Override
    public ECOCellType getCellType() {
        return ((NativeStorageCellItem) cellStack.getItem()).getCellType();
    }

    public IUpgradeInventory getUpgradesInventory() {
        return ((NativeStorageCellItem) cellStack.getItem()).getBusinessUpgrades(cellStack);
    }

    public ConfigInventory getConfigInventory() {
        return ((NativeStorageCellItem) cellStack.getItem()).getBusinessConfigInventory(cellStack);
    }

    public void clearAllStoredStacks() {
        if (!canChange()) throw new IllegalStateException("Cannot clear a detached ECO cell inventory");
        CellComponents.clearStacks(cellStack);
        readContents();
        committed();
    }

    @Override
    public void clearMigrationStacks() {
        clearAllStoredStacks();
    }

}
