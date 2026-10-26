package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.*;
import cn.dancingsnow.neoecoae.multiblock.cluster.*;
import cn.dancingsnow.neoecoae.platform.inventory.*;
import cn.dancingsnow.neoecoae.platform.minecraft.*;
import cn.dancingsnow.neoecoae.platform.nbt.*;
import cn.dancingsnow.neoecoae.platform.registry.*;
import cn.dancingsnow.neoecoae.platform.sync.*;

public class ECOComputationDriveBlockEntity extends NEBlockEntity<NEComputationCluster, ECOComputationDriveBlockEntity>
    implements ManagedHolder, InternalInventoryHost {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Persisted
    private final AppEngInternalInventory inventory = new AppEngInternalInventory(this, 1, 1) {

        @Override
        public boolean isItemValid(int slot, net.minecraft.item.ItemStack stack) {
            return slot == 0 && cn.dancingsnow.neoecoae.api.computation.ECOComputationCells.describe(stack) != null;
        }

        @Override
        public net.minecraft.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtractCell() ? super.extractItem(slot, amount, simulate) : null;
        }
    };
    private boolean lowerDrive;

    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NEComputationClusterCalculator::new);
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public net.minecraft.item.ItemStack getCellStack() {
        return inventory.getStackInSlot(0);
    }

    public AppEngInternalInventory getInternalInventory() {
        return inventory;
    }

    public boolean isLowerDrive() {
        return lowerDrive;
    }

    public void setLowerDrive(boolean lower) {
        lowerDrive = lower;
    }

    public void saveChangedInventory(AppEngInternalInventory inv) {
        setChanged();
        if (cluster != null) cluster.recalculateRemainingStorage();
    }

    @Override
    public void addAdditionalDrops(WorldView world, BlockPos pos, java.util.List<net.minecraft.item.ItemStack> drops) {
        if (getCellStack() != null) drops.add(getCellStack().copy());
    }

    @Override
    public void clearContent() {
        inventory.setStackInSlot(0, null);
        super.clearContent();
    }

    public boolean canExtractCell() {
        return cluster == null || cluster.getOwnUsedStorage() == 0;
    }
}
