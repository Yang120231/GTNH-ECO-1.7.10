package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.BlockOrientation;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>>
    extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>> implements ManagedHolder {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Persisted
    @DescSynced
    private ECOStorageInterfaceMode storageInterfaceMode = ECOStorageInterfaceMode.STORAGE;

    @Persisted
    @DescSynced
    private boolean ignoreCreativeStorageInput = true;

    @Persisted
    @DescSynced
    private boolean includeStorageBusContainers = false;

    @Persisted
    private CompoundTag storageResourceFilters = new CompoundTag();

    private transient CompoundTag cachedStorageResourceFilters;

    private transient StorageInterfaceFilter storageResourceFilter;

    private long transferredLastTick;

    public ECOMachineInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState, calculator);
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() {
        return storageInterfaceMode == null ? ECOStorageInterfaceMode.STORAGE : storageInterfaceMode;
    }

    public long getTransferredLastTick() {
        return transferredLastTick;
    }

    public boolean isIgnoringCreativeStorageInput() {
        return ignoreCreativeStorageInput;
    }

    public boolean isExcludingStorageBusContainers() {
        return !includeStorageBusContainers;
    }

    public void toggleExcludeStorageBusContainers() {
        if (level == null || level.isClientSide || !supportsStorageInterfaceUi()) return;
        includeStorageBusContainers = !includeStorageBusContainers;
        transferredLastTick = 0L;
        setChanged();
        markForUpdate();
    }

    public CompoundTag getStorageResourceFilters() {
        return storageResourceFilters.copy();
    }

    public boolean acceptsStorageResource(AEKey key) {
        if (storageResourceFilter == null || cachedStorageResourceFilters != storageResourceFilters) {
            var registries = level == null ? cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY
                : level.registryAccess();
            storageResourceFilter = new StorageInterfaceFilter(storageResourceFilters, registries);
            cachedStorageResourceFilters = storageResourceFilters;
        }
        return storageResourceFilter.test(key);
    }

    public void setStorageResourceFilter(int slot, @Nullable AEKey key) {
        if (level == null || level.isClientSide
            || !supportsStorageInterfaceUi()
            || slot < 0
            || slot >= StorageInterfaceFilter.SLOTS) return;
        CompoundTag updated = StorageInterfaceFilter.with(storageResourceFilters, slot, key, level.registryAccess());
        if (updated.equals(storageResourceFilters)) return;
        storageResourceFilters = updated;
        transferredLastTick = 0;
        setChanged();
    }

    public void toggleIgnoreCreativeStorageInput() {
        if (level == null || level.isClientSide || !supportsStorageInterfaceUi()) return;
        ignoreCreativeStorageInput = !ignoreCreativeStorageInput;
        setChanged();
        markForUpdate();
    }

    public boolean isStorageInputMode() {
        return getStorageInterfaceMode() == ECOStorageInterfaceMode.INPUT;
    }

    public boolean isStorageTransferMode() {
        return getStorageInterfaceMode() != ECOStorageInterfaceMode.STORAGE;
    }

    public boolean isInfiniteTransferAvailable() {
        return formed && cluster instanceof NEStorageCluster storage
            && storage.getController() != null
            && storage.getController()
                .isFormedInfiniteMode();
    }

    public boolean isTargetOnline() {
        return getMainNode().isOnline() && getMainNode().getGrid() != null;
    }

    public boolean supportsStorageInterfaceUi() {
        return cluster instanceof NEStorageCluster || calculator instanceof NEStorageClusterCalculator;
    }

    public void setStorageInterfaceMode(ECOStorageInterfaceMode mode) {
        ECOStorageInterfaceMode next = mode == null ? ECOStorageInterfaceMode.STORAGE : mode;
        if (getStorageInterfaceMode() == next && storageInterfaceMode != null) return;
        storageInterfaceMode = next;
        if (level != null && !level.isClientSide
            && getBlockState().getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeInterfaceBlock) {
            level.setBlockAndUpdate(
                worldPosition,
                getBlockState()
                    .setValue(cn.dancingsnow.neoecoae.platform.registry.NativeInterfaceBlock.STORAGE_MODE, next));
        }
        transferredLastTick = 0L;
        setChanged();
        markForUpdate();
        if (cluster instanceof NEStorageCluster storage && storage.getController() != null) {
            storage.getController()
                .onStorageInterfaceModeChanged();
        }
    }

    public void recordStorageInterfaceTransfer(long amount) {
        transferredLastTick = Math.max(0L, amount);
    }

    public boolean isClientSide() {
        return level != null && level.isClientSide;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.allOf(Direction.class);
    }

    public boolean supportsInterfaceUi() {
        return supportsStorageInterfaceUi();
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }
}
