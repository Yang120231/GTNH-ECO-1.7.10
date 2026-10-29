package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageMounts;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.inventory.IItemHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerTaskUtil;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;
import cn.dancingsnow.neoecoae.platform.registry.NativeDriveBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;

public class ECODriveBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster, ECODriveBlockEntity>
    implements ManagedHolder, IStorageProvider, ICellHost, ISaveProvider {

    private static final String RESTORE_RECEIPTS_TAG = "neoecoae_restore_receipts";

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public final IItemHandler HANDLER = new CellHostItemHandler(this);

    @DescSynced
    @Persisted
    @Nullable
    private ItemStack cellStack = null;

    // UI requests identify this installation, independently of mutable or transient item components.
    // Recreated on load; no persistent disk data or save migration is needed.

    private UUID cellInteractionId = UUID.randomUUID();

    @DescSynced
    private boolean mounted = false;

    @DescSynced
    private boolean online = false;

    @DescSynced
    private CellState cellState = CellState.ABSENT;
    @Nullable
    private ItemStack cachedCellStack;
    @Nullable
    private IECOStorageCell cachedCellInventory;
    private boolean persistenceSyncQueued;

    public ECODriveBlockEntity(BlockEntityType<ECODriveBlockEntity> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator::new);
        getMainNode().addService(IStorageProvider.class, this);
    }

    @Override
    public void loadTag(CompoundTag tag, cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (!tag.contains("neoecoae_fields") && tag.contains("Cell", 10)) {
            cellStack = ItemStack.loadItemStackFromNBT(
                tag.getCompound("Cell")
                    .nativeTag());
            if (cellStack == null)
                throw new IllegalStateException("Cannot resolve beta drive cell; original NBT retained");
        }
        invalidateCellInventoryCache();
    }

    @Override
    public void setCellStack(@Nullable ItemStack cellStack) {
        if (!canExtractCell()) {
            return;
        }
        if (cellStack != null && !isItemValid(cellStack)) return;
        flushPendingCell();
        ECOStorageSystemBlockEntity controller = getStorageController();
        if (controller != null) controller.rememberInfiniteMembers();
        ECOStorageCells.releaseCellInventory(this.cellStack, this);
        this.cellStack = cellStack;
        cellInteractionId = UUID.randomUUID();
        invalidateCellInventoryCache();
        if (getLevel() != null && !isServerStopping()) {
            BlockState state = getBlockState();
            BlockState newState = state.setValue(NativeDriveBlock.HAS_CELL, cellStack != null);
            if (newState != state) {
                getLevel().setBlockAndUpdate(getBlockPos(), newState);
            }
        }
        updateState();
        this.cellStack = cellStack;
        setChanged();
        if (controller != null) controller.notifyStorageConfigurationChanged();
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        ECOStorageSystemBlockEntity controller = getStorageController();
        if (controller != null && !controller.canInsertStorageCell(stack)) return false;
        return ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public boolean canExtractCell() {
        return getCellExtractionBlockReason() == CellExtractionBlockReason.NONE;
    }

    @Override
    @Nullable
    public Component getCellExtractionBlockReasonText() {
        CellExtractionBlockReason reason = getCellExtractionBlockReason();
        return reason == CellExtractionBlockReason.NONE ? null : Component.translatable(reason.translationKey());
    }

    /** Shared by manual interaction and automation; querying this never releases ownership. */
    public CellExtractionBlockReason getCellExtractionBlockReason() {
        if (ECOInfiniteStorageMember.isMigrating(cellStack)) {
            return CellExtractionBlockReason.INFINITE_MIGRATION;
        }
        if (isLockedByInfiniteMode()
            && (getStorageController() == null || !getStorageController().isFormedInfiniteMode())) {
            return CellExtractionBlockReason.INFINITE_MEMBER;
        }
        return CellExtractionBlockReason.NONE;
    }

    public enum CellExtractionBlockReason {

        NONE(""),
        INFINITE_MIGRATION("tooltip.neoecoae.storage.infinite_migration_locked"),
        INFINITE_MEMBER("tooltip.neoecoae.storage.infinite_member_locked");

        private final String translationKey;

        CellExtractionBlockReason(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public boolean isLockedByInfiniteMode() {
        return (Object) cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null
            && storageCluster.getController()
                .isInfiniteMode()
            && cellStack != null
            && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)
            && ECOInfiniteStorageMember.isMember(cellStack);
    }

    private void updateState() {
        if (isServerStopping()) {
            return;
        }
        updateCellState();
        double power = 256;
        if ((Object) cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            StorageTierDescriptor mainTier = storageCluster.getController()
                .getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                power += cellInventory.getIdleDrain();
            }
        }
        applyIdlePower(ECOPowerRole.STORAGE_DRIVE, 256, power - 256);
        IStorageProvider.requestUpdate(getMainNode());
    }

    @Override
    public void scheduleRenderUpdate() {
        markForClientUpdate();
    }

    @Override
    public void addAdditionalDrops(WorldView level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        flushPendingCell();
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }

    @Nullable
    public IECOStorageCell getCellInventory() {
        if (cellStack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)) {
            invalidateCellInventoryCache();
            return null;
        }
        if (cachedCellStack != cellStack) {
            cachedCellStack = cellStack;
            cachedCellInventory = ECOStorageCells.getCellInventory(cellStack, this);
        }
        return cachedCellInventory;
    }

    private void invalidateCellInventoryCache() {
        cachedCellStack = null;
        cachedCellInventory = null;
    }

    /** The cached cell may hold this tick's AE2 mutations; write them before anything observes the stack. */
    private void flushPendingCell() {
        cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch.flushDeferred(cachedCellInventory);
    }

    @Override
    public boolean defersCellPersistence() {
        return true;
    }

    @Override
    protected void saveAdditional(cn.dancingsnow.neoecoae.platform.nbt.CompoundTag tag,
        cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries) {
        flushPendingCell();
        super.saveAdditional(tag, registries);
    }

    /** The description packet serializes the synced cell stack directly, bypassing {@link #saveAdditional}. */
    @Override
    public CompoundTag getUpdateTag(cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries) {
        flushPendingCell();
        return super.getUpdateTag(registries);
    }

    @Override
    public void onChunkUnloaded() {
        flushPendingCell();
        ECOStorageCells.releaseCellInventory(cellStack, this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        flushPendingCell();
        ECOStorageCells.releaseCellInventory(cellStack, this);
        super.setRemoved();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if ((Object) cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            StorageTierDescriptor mainTier = storageCluster.getController()
                .getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0
                && !storageCluster.getController()
                    .isStorageInterfaceTransferMode()
                && (!storageCluster.getController()
                    .isInfiniteMode() || storageCluster.getController()
                        .canUseHostDomainStorage())
                && !ECOInfiniteStorageMember.isMigrating(cellStack)
                && !ECOInfiniteStorageMember.isMember(cellStack)) {
                int priority = cn.dancingsnow.neoecoae.gui.storage.StoragePriority.mountPriority(
                    storageCluster.getController()
                        .getStoragePriority(),
                    getConfiguredCellPriority());
                storageMounts.mount(cellInventory, priority);
                updateMountState(true);
                return;
            }
        }
        updateMountState(false);
    }

    private void updateMountState(boolean nowMounted) {
        boolean wasMounted = mounted;
        CellState previousState = cellState;
        mounted = nowMounted;
        updateCellState();
        setChanged();
        if (wasMounted != mounted || previousState != cellState) markLedStateChanged();
    }

    /**
     * 1.21 relies on LDLib pushing changed {@code @DescSynced} fields every tick; here the description packet is
     * only sent on request, so the LED renderer would otherwise keep the client's stale mounted/online state.
     */
    private void markLedStateChanged() {
        if (level != null && !level.isClientSide()) markForUpdate();
    }

    @Override
    public void onReady() {
        super.onReady();
        // AE2 attaches the node and reports its first state before the managed node is marked ready, so that report
        // always reads offline. A grid that is already running posts no further event, so re-read it once here.
        if (level != null && !level.isClientSide()) refreshOnline();
        updateState();
    }

    @Override
    public void onMainNodeStateChanged(NodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        super.onMainNodeStateChanged(reason);
        refreshOnline();
    }

    private void refreshOnline() {
        boolean wasOnline = online;
        online = getMainNode().isOnline();
        setChanged();
        if (wasOnline != online) markLedStateChanged();
    }

    @Override
    public void notifyPersistence() {
        // One status/description refresh per drive per tick; repeated cell writes in between change nothing it reads.
        if (persistenceSyncQueued) return;
        if (level instanceof ServerWorldView serverLevel) {
            persistenceSyncQueued = true;
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                persistenceSyncQueued = false;
                if (isRemoved()) return;
                CellState previousState = cellState;
                updateCellState();
                setChanged();
                // Contents are server-authoritative (UI and WAILA read server snapshots); only the LED state is
                // client-visible, so a description packet carrying the whole cell is sent only when it changes.
                if (previousState != cellState) markForUpdate();
            });
        }
    }

    private void updateCellState() {
        if (level == null || level.isClientSide) {
            return;
        }
        IECOStorageCell cellInventory = getCellInventory();
        cellState = cellInventory == null ? CellState.ABSENT : cellInventory.getStatus();
    }

    @Nullable
    public ECOStorageSystemBlockEntity getStorageController() {
        return (Object) cluster instanceof NEStorageCluster storageCluster ? storageCluster.getController() : null;
    }

    public int getConfiguredCellPriority() {
        return cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings.priority(cellStack);
    }

    public void setConfiguredCellPriority(int priority) {
        if (level == null || level.isClientSide
            || !cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings.canConfigure(cellStack)) return;
        flushPendingCell();
        cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings.setPriority(cellStack, priority);
        onCellConfigurationChanged();
        var controller = getStorageController();
        if (controller != null) controller.notifyStorageConfigurationChanged();
    }

    /** Persists an in-place CellConfig mutation and refreshes this drive's AE2 storage mount. */
    public void onCellConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        updateCellState();
        setChanged();
        markForUpdate();
        IStorageProvider.requestUpdate(getMainNode());
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
    }

    @Override
    public void saveChanges() {
        if (cachedCellInventory != null) {
            cachedCellInventory.persist();
        }
        notifyPersistence();
    }

    public void convertCellToInfiniteMember(UUID domainId) {
        if (cellStack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)) {
            return;
        }
        // Use the live handler: clearing a separately constructed inventory leaves this drive's cache stale.
        var inventory = getCellInventory();
        if (!(inventory instanceof cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell migrationCell)) {
            throw new IllegalStateException("Missing transfer-capable source cell");
        }
        migrationCell.clearMigrationStacks();
        migrationCell.persistMigrationContents(((ServerWorldView) level).nativeWorld());
        ECOInfiniteStorageMember.markMember(cellStack, domainId);
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
        setChanged();
        markForUpdate();
    }

    public boolean convertInfiniteMemberToNormalStorage(UUID domainId) {
        if (cellStack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)
            || !ECOInfiniteStorageMember.isMemberOf(cellStack, domainId)) {
            return false;
        }
        ECOInfiniteStorageMember.clearMember(cellStack);
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
        clearRestoreReceipts();
        setChanged();
        markForUpdate();
        return true;
    }

    public long getRestoreReceipt(UUID transactionId) {
        if (cellStack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)) return 0L;
        CompoundTag custom = CellComponents.readCustomData(cellStack)
            .copyTag();
        return custom.getCompound(RESTORE_RECEIPTS_TAG)
            .getLong(transactionId.toString());
    }

    public void putRestoreReceipt(UUID transactionId, long amount) {
        if (cellStack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)
            || amount <= 0L) return;
        CompoundTag custom = CellComponents.readCustomData(cellStack)
            .copyTag();
        CompoundTag receipts = custom.getCompound(RESTORE_RECEIPTS_TAG);
        receipts.putLong(transactionId.toString(), amount);
        custom.put(RESTORE_RECEIPTS_TAG, receipts);
        CellComponents.writeCustomData(cellStack, CustomData.of(custom));
        setChanged();
    }

    public void clearRestoreReceipts() {
        if (cellStack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cellStack)) return;
        CompoundTag custom = CellComponents.readCustomData(cellStack)
            .copyTag();
        custom.remove(RESTORE_RECEIPTS_TAG);
        if (custom.isEmpty()) CellComponents.clearCustomData(cellStack);
        else CellComponents.writeCustomData(cellStack, CustomData.of(custom));
        setChanged();
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public ItemStack getCellStack() {
        flushPendingCell();
        return cellStack;
    }

    public UUID getCellInteractionId() {
        return cellInteractionId;
    }

    public boolean isMounted() {
        return mounted;
    }

    public boolean isOnline() {
        return online;
    }

    public CellState getCellState() {
        return cellState;
    }
}
