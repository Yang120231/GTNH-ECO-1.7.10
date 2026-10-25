package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStoragePriorityHost;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.OrientationStrategies;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageMounts;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.inventory.IItemHandlerModifiable;
import cn.dancingsnow.neoecoae.platform.inventory.InternalInventoryHost;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.TickClock;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageHostBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageTags;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

public class ECOStorageSystemBlockEntity extends NEBlockEntity<NEStorageCluster, ECOStorageSystemBlockEntity>
    implements ManagedHolder, InternalInventoryHost, IStorageProvider, IECOStoragePriorityHost {

    private static final Logger LOGGER = LogManager.getLogger(ECOStorageSystemBlockEntity.class);

    private static final int INFINITE_COMPONENT_REQUIRED = 64;

    private static final long STORAGE_INTERFACE_TRANSFER_NANOS_PER_TICK = 2_000_000L;

    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    private static final String INFINITE_COMPONENT_INVENTORY_PERSIST_KEY = "infiniteComponentInventory";

    private static final String LEGACY_COMPONENT_INVENTORY_PERSIST_KEY = "componentInventory";

    private static final String CONTROLLER_DOMAIN_TAG = "neoecoae_infinite_controller_domain";

    private static final String CONTROLLER_MODE_TAG = "neoecoae_infinite_controller_mode";

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    private final StorageTierDescriptor tier;

    @Persisted
    @DescSynced
    private int storagePriority;

    @Persisted
    @DescSynced
    private ECOStorageHostMode hostMode = ECOStorageHostMode.UNFORMED;

    @Persisted
    private boolean infiniteExitRequested;

    private transient boolean infiniteComponentsDirty = true;

    private transient boolean infiniteMembersDirty = true;

    @Persisted
    @DescSynced
    @Nullable
    private UUID infiniteDomainId;

    private final java.util.Set<UUID> infiniteMemberIds = new java.util.HashSet<>();

    private final java.util.Set<UUID> infiniteMigrationSourceIds = new java.util.HashSet<>();

    @Persisted(key = INFINITE_COMPONENT_INVENTORY_PERSIST_KEY)
    // Keep the component inventory persisted and exposed to the regular UI container, but do not include
    // every ItemStack in LDLib's descriptive/advanced-data synchronization packet. A populated infinite
    // component inventory can exceed the packet's fixed 2392-byte buffer before the UI is even opened.
    private final AppEngInternalInventory infiniteComponentInventory = new AppEngInternalInventory(
        this,
        1,
        INFINITE_COMPONENT_REQUIRED);

    private final ECOStorageHostStatistics statistics = new ECOStorageHostStatistics(this);

    private final ECOStorageInterfaceTransfer interfaceTransfer = new ECOStorageInterfaceTransfer(this);

    private final ECOStorageInfiniteRestore infiniteRestore = new ECOStorageInfiniteRestore(this);

    private final ECOInfiniteStorageModeController infiniteModeController = new ECOInfiniteStorageModeController(this);

    private final IItemHandlerModifiable infiniteComponentItemHandler = infiniteRestore
        .componentItemHandler((IItemHandlerModifiable) infiniteComponentInventory.toItemHandler());

    private final ECOStorageStageRunner stageRunner = new ECOStorageStageRunner(this);

    @DescSynced
    private long performanceAverageNanos = 0L;

    private long performanceWindowStartTick = Long.MIN_VALUE;

    private long performanceWindowNanos = 0L;

    private final long[] performanceSamples = new long[256];

    private int performanceSampleCount;

    private int performanceSampleCursor;

    private long performanceP95Nanos;

    private long performanceMaxNanos;

    private boolean mirrored;

    public ECOStorageSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        StorageTierDescriptor tier) {
        super(type, pos, blockState, NEStorageClusterCalculator::new);
        this.tier = tier;
        getMainNode().addService(IStorageProvider.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, StorageTiers.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, StorageTiers.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, StorageTiers.L9);
    }

    @Override
    public void onReady() {
        super.onReady();
        infiniteMembersDirty = true;
        infiniteModeController.refreshHostAccess();
        applyIdlePower(ECOPowerRole.STORAGE_CONTROLLER, 256 + (1 << (1 + 4 * tier.getTier())), 0);
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        infiniteModeController.refreshHostAccess();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(NativeStorageHostBlock.MIRRORED)) {
                var interfaceMode = getStorageInterface();
                var mode = interfaceMode == null ? cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.STORAGE
                    : interfaceMode.getStorageInterfaceMode();
                BlockState newState = state.setValue(NativeStorageHostBlock.MIRRORED, formed && mirrored)
                    .setValue(NativeStorageHostBlock.STORAGE_MODE, mode);
                if (newState != state) {
                    level.setBlock(worldPosition, newState, 2);
                }
            }
        }
    }

    public void tick(WorldView level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        try (var cellBatch = cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch.open()) {
            if (!stageRunner.run("migration", this::updateInfiniteStorageMode)) return;
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                if (!stageRunner.run("transfer", () -> {
                    storageInterface.recordStorageInterfaceTransfer(
                        interfaceTransfer.transferStorageInterfaceContents(storageInterface));
                })) return;
            }

        } finally {
            long elapsed = System.nanoTime() - startNanos;
            recordPerformanceSample(elapsed);
        }
    }

    public Collection<cn.dancingsnow.neoecoae.impl.storage.StorageFaults.Fault> storageFailures() {
        return stageRunner.failures();
    }

    public String storageDiagnosticText() {
        return storageDiagnostics().getString();
    }

    private void recordPerformanceSample(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return;
        }
        performanceSamples[performanceSampleCursor++ % performanceSamples.length] = elapsedNanos;
        performanceSampleCount = Math.min(performanceSamples.length, performanceSampleCount + 1);
        long currentTick = TickClock.currentTick();
        if (performanceWindowStartTick == Long.MIN_VALUE) {
            performanceWindowStartTick = currentTick;
        }
        performanceWindowNanos += elapsedNanos;
        long elapsedTicks = currentTick - performanceWindowStartTick;
        if (elapsedTicks < PERFORMANCE_SAMPLE_WINDOW_TICKS) {
            return;
        }
        long nextAverageNanos = performanceWindowNanos / Math.max(1L, elapsedTicks);
        long[] ordered = java.util.Arrays.copyOf(performanceSamples, performanceSampleCount);
        java.util.Arrays.sort(ordered);
        performanceP95Nanos = ordered[Math.max(0, (int) Math.ceil(ordered.length * 0.95D) - 1)];
        performanceMaxNanos = ordered[ordered.length - 1];
        performanceSampleCount = 0;
        performanceSampleCursor = 0;
        performanceWindowStartTick = currentTick;
        performanceWindowNanos = 0L;
        if (performanceAverageNanos == nextAverageNanos) {
            return;
        }
        performanceAverageNanos = nextAverageNanos;
        setChanged();
    }

    public List<ECODriveBlockEntity> getStorageUiDrives() {
        if (!isFormed() || cluster == null) return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        var orientation = OrientationStrategies.horizontalFacing();
        var expansion = orientation.getSide(getBlockState(), mirrored ? RelativeSide.RIGHT : RelativeSide.LEFT);
        var top = orientation.getSide(getBlockState(), RelativeSide.TOP);
        return StorageDriveLayout.ordered(cluster.getDrives(), getBlockPos(), expansion, top);
    }

    private cn.dancingsnow.neoecoae.platform.minecraft.Component storageDiagnostics() {
        var text = cn.dancingsnow.neoecoae.platform.minecraft.Component.empty();
        for (var fault : stageRunner.failures()) {
            text.append(fault.component() + " [" + fault.id() + "]\n" + fault.reason() + "\n");
        }
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine instanceof cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine saved) {
            text.append(saved.persistenceSummary() + "\n");
            for (String failure : saved.failures()) text.append(failure + "\n");
        }
        return text;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        infiniteComponentsDirty = true;
        infiniteRestore.invalidateExtractionCheck();
        invalidateStorageStatistics();
        saveChanges();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !canUseHostDomainStorage() || isStorageInterfaceTransferMode()) {
            return;
        }
        storageMounts.mount(createInfiniteStorageView(engine), StoragePriority.mountPriority(storagePriority, false));
    }

    private void changeStoragePriority(EntityPlayer player, int delta) {
        if (!canPlayerInteract(player)) return;
        setStoragePriority(player, StoragePriority.adjust(storagePriority, delta));
    }

    private void setStoragePriority(EntityPlayer player, int priority) {
        if (!canPlayerInteract(player)) return;
        setStoragePriority(priority);
    }

    @Override
    public void setStoragePriority(int priority) {
        if (level == null || level.isClientSide()) return;
        if (storagePriority == priority) {
            return;
        }
        storagePriority = priority;
        setChanged();
        markForUpdate();
        refreshDriveStorageProviders();
    }

    void refreshDriveStorageProviders() {
        if (cluster == null) {
            return;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IStorageProvider.requestUpdate(drive.getMainNode());
        }
        IStorageProvider.requestUpdate(getMainNode());
    }

    public KeyCounter collectLocalStorageStacksForIntegration() {
        KeyCounter result = new KeyCounter();
        MEStorage storage = interfaceTransfer.getStorageInterfaceHostStorage();
        if (storage != null) {
            storage.getAvailableStacks(result);
        }
        return result;
    }

    public List<ECODriveBlockEntity> getStorageDrivesForIntegration() {
        return cluster == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf() : cluster.getDrives();
    }

    public void notifyStorageConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        invalidateInfiniteMembers();
        invalidateStorageStatistics();
        infiniteRestore.invalidateExtractionCheck();
        setChanged();
        markForUpdate();
        refreshDriveStorageProviders();
    }

    @Override
    public void addAdditionalDrops(WorldView level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack infiniteComponent = infiniteComponentInventory.getStackInSlot(0);
        if (!cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(infiniteComponent)) {
            drops.add(infiniteComponent);
        }
    }

    public void applyInfiniteDomainToControllerDrop(ItemStack drop) {
        if (infiniteDomainId == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(drop)) {
            return;
        }
        CompoundTag tag = CellComponents.readCustomData(drop)
            .copyTag();
        rememberInfiniteMembers();
        saveInfiniteMembers(tag);
        tag.putUUID(CONTROLLER_DOMAIN_TAG, infiniteDomainId);
        tag.putString(CONTROLLER_MODE_TAG, hostMode.id());
        CellComponents.writeCustomData(drop, CustomData.of(tag));
    }

    public void restoreInfiniteDomainFromItem(ItemStack stack) {
        CompoundTag tag = CellComponents.readCustomData(stack)
            .copyTag();
        if (!tag.hasUUID(CONTROLLER_DOMAIN_TAG)) {
            return;
        }
        infiniteModeController.release();
        loadInfiniteMembers(tag);
        setInfiniteDomainId(tag.getUUID(CONTROLLER_DOMAIN_TAG));
        setStorageHostMode(ECOStorageHostMode.fromId(tag.getString(CONTROLLER_MODE_TAG)));
        setChanged();
    }

    public String getInfiniteDomainText() {
        return isInfiniteMode() && infiniteDomainId != null ? infiniteDomainId.toString() : "";
    }

    public boolean canInsertStorageCell(ItemStack stack) {
        if (!isInfiniteMode()) return !ECOInfiniteStorageMember.isSealed(stack);
        if (ECOInfiniteStorageMember.isMigrating(stack)) {
            UUID migrationId = ECOInfiniteStorageMember.getMigrationId(stack);
            return hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE && infiniteDomainId != null
                && ECOInfiniteStorageMember.getMigrationDomainId(stack)
                    .map(infiniteDomainId::equals)
                    .orElse(false)
                && migrationId != null
                && infiniteMigrationSourceIds.contains(migrationId)
                && (cluster == null || cluster.getDrives()
                    .stream()
                    .noneMatch(
                        drive -> migrationId.equals(ECOInfiniteStorageMember.getMigrationId(drive.getCellStack()))));
        }
        if (ECOInfiniteStorageMember.isMember(stack)) {
            if (infiniteDomainId == null || !ECOInfiniteStorageMember.isMemberOf(stack, infiniteDomainId)) return false;
            var identity = ECOInfiniteStorageMember.getIdentity(stack);
            return cluster == null || !identity.isPresent()
                || cluster.getDrives()
                    .stream()
                    .noneMatch(drive -> identity.equals(ECOInfiniteStorageMember.getIdentity(drive.getCellStack())));

        }
        IECOStorageCell cell = cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.getCellInventory(stack, null);
        return cell != null && !cell.isInfiniteStorageEligible();
    }

    public void rememberInfiniteMembers() {
        if (!infiniteMembersDirty) return;
        if (level == null || level.isClientSide || cluster == null || infiniteDomainId == null) return;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), infiniteDomainId)) {
                if (infiniteMemberIds.add(ECOInfiniteStorageMember.identity(drive.getCellStack()))) {
                    drive.setChanged();
                    setChanged();
                }
            }
        }
        infiniteMembersDirty = false;
    }

    public void invalidateInfiniteMembers() {
        infiniteMembersDirty = true;
        infiniteModeController.membersChanged();
    }

    public int getMissingInfiniteMembers() {
        if (!isInfiniteMode()) return 0;
        rememberInfiniteMembers();
        java.util.Set<UUID> missing = new java.util.HashSet<>(infiniteMemberIds);
        if (cluster != null && infiniteDomainId != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), infiniteDomainId)) {
                    ECOInfiniteStorageMember.getIdentity(drive.getCellStack())
                        .ifPresent(missing::remove);
                }
            }
        }
        return missing.size();
    }

    private void saveInfiniteMembers(CompoundTag tag) {
        cn.dancingsnow.neoecoae.platform.nbt.ListTag ids = new cn.dancingsnow.neoecoae.platform.nbt.ListTag();
        for (UUID id : infiniteMemberIds)
            ids.add(cn.dancingsnow.neoecoae.platform.nbt.StringTag.valueOf(id.toString()));
        tag.put("infiniteMemberIds", ids);
        cn.dancingsnow.neoecoae.platform.nbt.ListTag migrations = new cn.dancingsnow.neoecoae.platform.nbt.ListTag();
        for (UUID id : infiniteMigrationSourceIds) {
            migrations.add(cn.dancingsnow.neoecoae.platform.nbt.StringTag.valueOf(id.toString()));
        }
        tag.put("infiniteMigrationSourceIds", migrations);
    }

    private void loadInfiniteMembers(CompoundTag tag) {
        infiniteMembersDirty = true;
        infiniteMemberIds.clear();
        for (var value : tag.getList("infiniteMemberIds", cn.dancingsnow.neoecoae.platform.nbt.Tag.TAG_STRING)) {
            infiniteMemberIds.add(UUID.fromString(value.getAsString()));
        }
        infiniteMigrationSourceIds.clear();
        for (var value : tag
            .getList("infiniteMigrationSourceIds", cn.dancingsnow.neoecoae.platform.nbt.Tag.TAG_STRING)) {
            infiniteMigrationSourceIds.add(UUID.fromString(value.getAsString()));
        }
    }

    public boolean isInfiniteMode() {
        return hostMode.isInfiniteState();
    }

    public boolean isMigratingToInfinite() {
        return hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE;
    }

    public boolean isFormedInfiniteMode() {
        return hostMode == ECOStorageHostMode.FORMED_INFINITE;
    }

    private int getInfiniteMigrationProgressPercent() {
        return infiniteModeController.migrationProgressPercent();
    }

    public boolean canUseHostDomainStorage() {
        return formed && hostMode.isInfiniteState() && infiniteDomainId != null && getMissingInfiniteMembers() == 0;
    }

    public boolean canInsertIntoInfiniteDomain() {
        return infiniteModeController.canInsertIntoDomain();
    }

    void refreshInfiniteStorageAccess() {
        infiniteModeController.refreshHostAccess();
    }

    MEStorage createInfiniteStorageView(ECOInfiniteStorageEngine engine) {
        return infiniteModeController.createStorageView(engine);
    }

    public boolean canUseNormalStorage() {
        return formed && !isRemoved() && !isServerStopping() && !hostMode.isInfiniteState();
    }

    public boolean isInfiniteMemberCell(@Nullable ItemStack stack) {
        return stack != null && ECOInfiniteStorageMember.isMember(stack);
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) return;
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (level.getBlockState(worldPosition)
            .hasProperty(NativeStorageHostBlock.STORAGE_MODE)) {
            var mode = storageInterface == null ? cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.STORAGE
                : storageInterface.getStorageInterfaceMode();
            level.setBlockAndUpdate(
                worldPosition,
                level.getBlockState(worldPosition)
                    .setValue(NativeStorageHostBlock.STORAGE_MODE, mode));
        }
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
    }

    public boolean isStorageInterfaceTransferMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return formed && storageInterface != null && storageInterface.isStorageTransferMode();
    }

    @Nullable
    private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private void updateInfiniteStorageMode() {
        infiniteModeController.update();
    }

    boolean hasRequiredInfiniteComponents(ItemStack stack) {
        return isInfiniteComponent(stack)
            && cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(stack) >= INFINITE_COMPONENT_REQUIRED;
    }

    void exitInfiniteModeIfSafe() {
        infiniteModeController.exitIfSafe();
    }

    UUID migrationTransactionId(UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
        return infiniteModeController.migrationTransactionId(domainId, drive, key, amount, direction);
    }

    @Override
    public void updateCluster(@Nullable NEStorageCluster nextCluster) {
        rememberInfiniteMembers();
        if (nextCluster == null) {
            infiniteModeController.release();
        }
        super.updateCluster(nextCluster);
        invalidateInfiniteMembers();
        infiniteModeController.refreshHostAccess();
    }

    @Override
    public void onChunkUnloaded() {
        infiniteModeController.release();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        infiniteModeController.release();
        super.setRemoved();
    }

    @Nullable
    ECOInfiniteStorageEngine getInfiniteEngine() {
        return infiniteModeController.getEngine();
    }

    static boolean isInfiniteComponent(ItemStack stack) {
        return !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
            && StorageTags.isInfiniteComponent(stack);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString("infiniteHostMode", hostMode.id());
        if (infiniteDomainId != null) {
            data.putUUID("infiniteDomainId", infiniteDomainId);
        }
        saveInfiniteMembers(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        loadLegacyInfiniteComponentInventory(data, registries);
        setStorageHostMode(ECOStorageHostMode.fromId(data.getString("infiniteHostMode")));
        setInfiniteDomainId(data.hasUUID("infiniteDomainId") ? data.getUUID("infiniteDomainId") : null);
        loadInfiniteMembers(data);
    }

    private void loadLegacyInfiniteComponentInventory(CompoundTag data, HolderLookup.Provider registries) {
        if (!cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks
            .isEmpty(infiniteComponentInventory.getStackInSlot(0))) {
            return;
        }
        CompoundTag managed = data.getCompound("managed");
        if (!managed.contains(LEGACY_COMPONENT_INVENTORY_PERSIST_KEY)) {
            return;
        }
        infiniteComponentInventory
            .readFromNBT(managed.getCompound(LEGACY_COMPONENT_INVENTORY_PERSIST_KEY), "inventory", registries);
    }

    public boolean canExtractInfiniteComponents() {
        return blockedInfiniteComponentExtractionReason() == null;
    }

    @Nullable
    public String blockedInfiniteComponentExtractionReason() {
        return infiniteRestore.blockedInfiniteComponentExtractionReason();
    }

    void invalidateStorageStatistics() {
        statistics.invalidate();
    }

    cn.dancingsnow.neoecoae.impl.storage.StorageFaults storageFaults() {
        return stageRunner.faults();
    }

    Map<String, Long> storageStageRetryTicks() {
        return stageRunner.retryTicks();
    }

    public ECOStorageHostMode storageHostMode() {
        return hostMode;
    }

    void setStorageHostMode(ECOStorageHostMode mode) {
        if (hostMode == mode) return;
        hostMode = mode;
        infiniteModeController.hostModeChanged();
    }

    boolean consumeInfiniteComponentsDirty() {
        boolean dirty = infiniteComponentsDirty;
        infiniteComponentsDirty = false;
        return dirty;
    }

    boolean hasRequiredInfiniteComponents() {
        return hasRequiredInfiniteComponents(infiniteComponentInventory.getStackInSlot(0));
    }

    void clearInfiniteExitRequest() {
        infiniteExitRequested = false;
        infiniteModeController.refreshHostAccess();
    }

    boolean isInfiniteExitRequested() {
        return infiniteExitRequested;
    }

    void requestInfiniteExit() {
        infiniteExitRequested = true;
        infiniteModeController.refreshHostAccess();
    }

    @Nullable
    UUID infiniteDomainId() {
        return infiniteDomainId;
    }

    void setInfiniteDomainId(@Nullable UUID domainId) {
        if (java.util.Objects.equals(infiniteDomainId, domainId)) return;
        infiniteModeController.release();
        infiniteDomainId = domainId;
        invalidateInfiniteMembers();
        infiniteModeController.hostModeChanged();
    }

    java.util.Set<UUID> infiniteMemberIds() {
        return infiniteMemberIds;
    }

    java.util.Set<UUID> infiniteMigrationSourceIds() {
        return infiniteMigrationSourceIds;
    }

    ECOStorageInfiniteRestore infiniteRestore() {
        return infiniteRestore;
    }

    ECOStorageInterfaceTransfer storageInterfaceTransfer() {
        return interfaceTransfer;
    }

    boolean isStorageServerStopping() {
        return isServerStopping();
    }

    public AppEngInternalInventory infiniteComponentInventory() {
        return infiniteComponentInventory;
    }

    public boolean canPlayerInteract(EntityPlayer player) {
        return level != null && NativeStorageHostBlock.isPlayerCloseEnough(level, worldPosition, player);
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    public cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.Config storageUiConfig(
        net.minecraft.entity.player.EntityPlayer player) {
        return new cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.Config(
            () -> cn.dancingsnow.neoecoae.platform.minecraft.Component
                .literal(getItemFromBlockEntity().getDisplayName()),
            () -> getTier().getTier(),
            this::isFormed,
            statistics::getStoredEnergy,
            statistics::getMaxEnergy,
            statistics::getEnergyConsumePerTick,
            statistics::getTotalUsedBytesText,
            statistics::getCellEntries,
            statistics.createStorageTypeLines(),
            this::isFormedInfiniteMode,
            this::isMigratingToInfinite,
            this::canExtractInfiniteComponents,
            this::getInfiniteDomainText,
            this::getMissingInfiniteMembers,
            infiniteComponentItemHandler,
            player,
            (position, expected, shift) -> StorageCellUiExtraction.extract(this, player, position, expected, shift),
            (position, expected) -> StorageCellUiInsertion.insert(this, player, position, expected),
            () -> new ItemStack(getBlockState().getBlock()));
    }

    public void setMirrored(boolean value) {
        mirrored = value;
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }

    public int getStoragePriority() {
        return storagePriority;
    }

    public long getPerformanceAverageNanos() {
        return performanceAverageNanos;
    }

    public long getPerformanceP95Nanos() {
        return performanceP95Nanos;
    }

    public long getPerformanceMaxNanos() {
        return performanceMaxNanos;
    }
}
