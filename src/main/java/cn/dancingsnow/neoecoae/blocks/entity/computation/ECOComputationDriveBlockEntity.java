package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.inventory.InternalInventoryHost;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

public class ECOComputationDriveBlockEntity extends NEBlockEntity<NEComputationCluster, ECOComputationDriveBlockEntity>
    implements ManagedHolder, InternalInventoryHost {

    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager
        .getLogger(cn.dancingsnow.neoecoae.NeoECOAE.MODID);
    private static final String BETA_CELL = "Cell";
    private static final String UNRESOLVED_BETA_CELL = "neoecoae_beta_cell";
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Persisted
    @DescSynced
    private final AppEngInternalInventory inventory = new AppEngInternalInventory(this, 1, 1) {

        @Override
        public boolean isItemValid(int slot, net.minecraft.item.ItemStack stack) {
            return slot == 0 && isSupportedCell(stack);
        }

        @Override
        public net.minecraft.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtractCell() ? super.extractItem(slot, amount, simulate) : null;
        }
    };
    @DescSynced
    private boolean lowerDrive;
    @DescSynced
    private int renderTier;
    /** A 7.3.0-beta cell whose item no longer resolves; kept verbatim instead of loading as an empty drive. */
    private net.minecraft.nbt.NBTTagCompound unresolvedBetaCell;

    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NEComputationClusterCalculator::new);
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public net.minecraft.item.ItemStack getCellStack() {
        return inventory.getStackInSlot(0);
    }

    public void setCellStack(net.minecraft.item.ItemStack stack) {
        if (stack != null && !inventory.isItemValid(0, stack)) return;
        inventory.setStackInSlot(0, ItemStacks.copyWithCount(stack, 1));
    }

    public AppEngInternalInventory getInternalInventory() {
        return inventory;
    }

    public static boolean isSupportedCell(net.minecraft.item.ItemStack stack) {
        var memory = cn.dancingsnow.neoecoae.api.computation.ECOComputationCells.describe(stack);
        if (memory == null) return false;
        var tier = memory.tier();
        return tier == cn.dancingsnow.neoecoae.platform.registry.StorageTier.L4
            || tier == cn.dancingsnow.neoecoae.platform.registry.StorageTier.L6
            || tier == cn.dancingsnow.neoecoae.platform.registry.StorageTier.L9;
    }

    public boolean isLowerDrive() {
        return lowerDrive;
    }

    public void setLowerDrive(boolean lower) {
        if (lowerDrive == lower) return;
        lowerDrive = lower;
        markForClientUpdate();
    }

    public int getRenderTier() {
        return renderTier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (level == null || level.isClientSide() || notLoaded() || isServerStopping() || isRemoved()) return;
        var controller = cluster == null ? null : cluster.getController();
        int tier = controller == null ? 0
            : controller.getTier()
                .getTier();
        if (renderTier != tier) {
            renderTier = tier;
            markForClientUpdate();
        }
    }

    public void saveChangedInventory(AppEngInternalInventory inv) {
        setChanged();
        if (cluster != null) cluster.recalculateRemainingStorage();
        markForClientUpdate();
    }

    @Override
    public void addAdditionalDrops(WorldView world, BlockPos pos, java.util.List<net.minecraft.item.ItemStack> drops) {
        if (getCellStack() != null) drops.add(getCellStack().copy());
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        unresolvedBetaCell = data.contains(UNRESOLVED_BETA_CELL, 10) ? copy(data.getCompound(UNRESOLVED_BETA_CELL))
            : null;
        // Beta's TileComputationDrive wrote no managed fields and kept its cell under "Cell". The cell is restored
        // without the tier check: an unsupported cell still drops when the drive is broken instead of vanishing.
        if (!data.contains("neoecoae_fields") && data.contains(BETA_CELL, 10)) {
            var raw = copy(data.getCompound(BETA_CELL));
            net.minecraft.item.ItemStack stack = loadBetaStack(raw);
            if (stack != null) inventory.loadStack(0, stack);
            else {
                unresolvedBetaCell = raw;
                LOGGER.warn(
                    "Beta computation drive at {},{},{} kept a cell whose item no longer resolves",
                    xCoord,
                    yCoord,
                    zCoord);
            }
        }
    }

    /** Resolves the beta cell. Package-private so tests can resolve items without the FML registry. */
    net.minecraft.item.ItemStack loadBetaStack(net.minecraft.nbt.NBTTagCompound tag) {
        net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.loadItemStackFromNBT(tag);
        return ItemStacks.isEmpty(stack) ? null : stack;
    }

    boolean hasUnresolvedBetaCell() {
        return unresolvedBetaCell != null;
    }

    private static net.minecraft.nbt.NBTTagCompound copy(CompoundTag tag) {
        return (net.minecraft.nbt.NBTTagCompound) tag.nativeTag()
            .copy();
    }

    @Override
    protected void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (unresolvedBetaCell != null) data
            .put(UNRESOLVED_BETA_CELL, new CompoundTag((net.minecraft.nbt.NBTTagCompound) unresolvedBetaCell.copy()));
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
