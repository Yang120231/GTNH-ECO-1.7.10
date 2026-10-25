package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.MENetworkPowerStorage;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IExternalPowerSink;
import cn.dancingsnow.neoecoae.platform.ae2.grid.StoredEnergyAmount;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerTaskUtil;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.registry.NativeEnergyCellBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

public class ECOEnergyCellBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster, ECOEnergyCellBlockEntity>
    implements IExternalPowerSink, IGridTickable, ManagedHolder {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    private final StorageTierDescriptor tier;
    private byte currentDisplayLevel;

    @Persisted
    private final StoredEnergyAmount energyStored;

    @Persisted
    private boolean neighborChangePending = false;

    public ECOEnergyCellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        StorageTierDescriptor tier) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator::new);
        getMainNode().addService(IAEPowerStorage.class, this)
            .addService(IGridTickable.class, this);
        this.energyStored = new StoredEnergyAmount(0, tier.getPowerStorageSize(), this::emitPowerEvent);
        this.tier = tier;
    }

    private void emitPowerEvent(MENetworkPowerStorage.PowerEventType type) {
        if (isServerStopping()) {
            return;
        }
        getMainNode().ifPresent(grid -> grid.postEvent(new MENetworkPowerStorage(this, type)));
    }

    @Override
    public final double injectAEPower(double amt, Actionable mode) {
        var inserted = this.energyStored.insert(amt, mode == appeng.api.config.Actionable.MODULATE);
        if (mode == Actionable.MODULATE && inserted > 0) {
            this.onEnergyChanged();
        }
        return amt - inserted;
    }

    @Override
    public final double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        double extracted = pm.divide(this.extractAEPower(pm.multiply(amt), mode));
        if (mode == Actionable.MODULATE && extracted > 0) {
            this.onEnergyChanged();
        }
        return extracted;
    }

    private double extractAEPower(double amt, Actionable mode) {
        return this.energyStored.extract(amt, mode == appeng.api.config.Actionable.MODULATE);
    }

    @Override
    public double getAEMaxPower() {
        return this.energyStored.getMaximum();
    }

    @Override
    public double getAECurrentPower() {
        return this.energyStored.getAmount();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return formed ? AccessRestriction.READ_WRITE : AccessRestriction.NO_ACCESS;
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerWorldView serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    private void onEnergyChanged() {
        if (isServerStopping()) {
            return;
        }
        setChangedNoTicketUpdate();

        if (!neighborChangePending) {
            neighborChangePending = true;
            getMainNode().ifPresent(
                (grid, node) -> grid.getTickManager()
                    .alertDevice(node));
        }
    }

    public FieldManagedStorage getRootStorage() {
        return syncStorage;
    }

    @Override
    public void onReady() {
        super.onReady();
        final int value = this.level.getBlockState(this.worldPosition)
            .getValue(NativeEnergyCellBlock.LEVEL);
        this.currentDisplayLevel = (byte) value;
        this.updateStateForPowerLevel();
        applyIdlePower(ECOPowerRole.ENERGY_CELL, 0, 0);
    }

    @Override
    public double injectExternalPower(PowerUnits externalUnit, double amount, Actionable mode) {
        return PowerUnits.AE
            .convertTo(externalUnit, injectAEPower(PowerUnits.AE.convertTo(externalUnit, amount), mode));
    }

    @Override
    public double getExternalPowerDemand(PowerUnits externalUnit, double maxPowerRequired) {
        return PowerUnits.AE.convertTo(externalUnit, Math.max(0.0, getAEMaxPower() - getAECurrentPower()));
    }

    private void setChangedNoTicketUpdate() {
        if (!isServerStopping()) getLevel().markLoadedChunkDirty(getBlockPos());
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, !neighborChangePending, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (isServerStopping()) {
            return TickRateModulation.SLEEP;
        }
        if (getLevel().hasChunkAt(getBlockPos())) {
            if (neighborChangePending) {
                neighborChangePending = false;
                setChanged(); // update comparators
                updateStateForPowerLevel(); // and update block state
            }
            return TickRateModulation.SLEEP;
        } else {
            return TickRateModulation.IDLE;
        }
    }

    public static int getStorageLevelFromFillFactor(double fillFactor) {
        return (int) Math.floor(4 * cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.clamp(fillFactor + 0.01, 0, 1));
    }

    private void updateStateForPowerLevel() {
        if (this.isRemoved() || isServerStopping()) {
            return;
        }

        int storageLevel = getStorageLevelFromFillFactor(
            this.energyStored.getAmount() / this.energyStored.getMaximum());

        if (this.currentDisplayLevel != storageLevel) {
            this.currentDisplayLevel = (byte) storageLevel;
            this.level.setBlockAndUpdate(
                this.worldPosition,
                this.level.getBlockState(this.worldPosition)
                    .setValue(NativeEnergyCellBlock.LEVEL, storageLevel));
        }
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }
}
