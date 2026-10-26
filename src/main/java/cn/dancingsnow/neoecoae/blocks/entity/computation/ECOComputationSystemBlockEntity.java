package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.*;
import cn.dancingsnow.neoecoae.multiblock.cluster.*;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.inventory.*;
import cn.dancingsnow.neoecoae.platform.minecraft.*;
import cn.dancingsnow.neoecoae.platform.nbt.*;
import cn.dancingsnow.neoecoae.platform.registry.*;
import cn.dancingsnow.neoecoae.platform.sync.*;

public class ECOComputationSystemBlockEntity
    extends NEBlockEntity<NEComputationCluster, ECOComputationSystemBlockEntity>
    implements ManagedHolder, ECOCraftingNetworkSettings {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    private final StorageTierDescriptor tier;
    @Persisted
    private int networkFrequency = 0;
    @Persisted
    private int cpuSelectionMode;
    @Persisted
    private boolean ignorePatternSubstitutions;
    @Persisted
    private boolean cyclePlanningEnabled = true;
    @Persisted
    private boolean fastPlannerEnabled = true;
    @Persisted
    private int selectedBuildLength = 11;

    public ECOComputationSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
        StorageTierDescriptor tier) {
        super(type, pos, state, NEComputationClusterCalculator::new);
        this.tier = tier;
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }

    public CpuSelectionMode getCpuSelectionMode() {
        return CpuSelectionMode.values()[Math.floorMod(cpuSelectionMode, CpuSelectionMode.values().length)];
    }

    public void setCpuSelectionMode(CpuSelectionMode mode) {
        cpuSelectionMode = mode.ordinal();
        setChanged();
    }

    public boolean hasNormalNetworkSwitch() {
        return getBlockState().getValue(NativeMachineBlock.NETWORK_SWITCH);
    }

    public boolean hasHighEnergyNetworkSwitch() {
        return getBlockState().getValue(NativeMachineBlock.HIGH_ENERGY_NETWORK_SWITCH);
    }

    public boolean hasNetworkSwitch() {
        return hasNormalNetworkSwitch() || hasHighEnergyNetworkSwitch();
    }

    public boolean hasNetworkFrequency() {
        return networkFrequency > 0 && networkFrequency <= 4096;
    }

    public int getNetworkFrequency() {
        return networkFrequency;
    }

    public void assignNetworkFrequency(int value) {
        networkFrequency = value;
        setChanged();
    }

    public void setNetworkFrequency(int value) {
        assignNetworkFrequency(Math.max(1, Math.min(4096, value)));
        if (cluster != null) NELogicalNetworkManager.refresh(cluster);
    }

    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    public void setSelectedBuildLength(int value) {
        selectedBuildLength = Math.max(1, Math.min(getMaxBuildLength(), value));
        setChanged();
    }

    public int getMaxBuildLength() {
        return cn.dancingsnow.neoecoae.platform.forge.PlanningSettings.computationSystemMaxLength - 4;
    }

    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        return Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) + 1 <= getMaxBuildLength() + 4;
    }

    private boolean mirrored;

    public void setMirrored(boolean value) {
        mirrored = value;
    }

    @Override
    public void updateState(boolean exposed) {
        super.updateState(exposed);
        if (level != null && getBlockState().hasProperty(NativeMachineBlock.MIRRORED))
            level.setBlock(getBlockPos(), getBlockState().setValue(NativeMachineBlock.MIRRORED, formed && mirrored), 2);
    }

    public boolean isIgnoringPatternSubstitutions() {
        return ignorePatternSubstitutions;
    }

    public void setIgnoringPatternSubstitutions(boolean value) {
        ignorePatternSubstitutions = value;
        setChanged();
    }

    public boolean isCyclePlanningEnabled() {
        return cyclePlanningEnabled;
    }

    public void setCyclePlanningEnabled(boolean value) {
        cyclePlanningEnabled = value;
        setChanged();
    }

    public boolean isFastPlannerEnabled() {
        return fastPlannerEnabled;
    }

    public void setFastPlannerEnabled(boolean value) {
        fastPlannerEnabled = value;
        setChanged();
    }

    @Override
    public boolean neoecoae$isIgnoringPatternSubstitutions() {
        return ignorePatternSubstitutions;
    }

    @Override
    public boolean neoecoae$isCyclePlanningEnabled() {
        return cyclePlanningEnabled;
    }

    @Override
    public void onMainNodeStateChanged(cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (cluster != null) {
            cluster.notifyGridCpuChange();
            NELogicalNetworkManager.refreshAfterGridChange(cluster);
        }
    }

    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        if (cluster != null) NELogicalNetworkManager.refreshAfterGridChange(cluster);
    }

    public void neoecoae$setIgnoringPatternSubstitutions(boolean v) {
        setIgnoringPatternSubstitutions(v);
    }

    public int neoecoae$getSubstitutionPatternCount() {
        return 0;
    }

    public boolean neoecoae$isFastPlannerEnabled() {
        return fastPlannerEnabled;
    }

    public void neoecoae$setFastPlannerEnabled(boolean v) {
        setFastPlannerEnabled(v);
    }

    public void neoecoae$setCyclePlanningEnabled(boolean v) {
        setCyclePlanningEnabled(v);
    }

    public boolean neoecoae$hasComputationHost() {
        return cluster != null && getMainNode().isOnline();
    }

    public java.util.Set<ResourceLocation> neoecoae$getFuzzyPlanningItemIds() {
        return java.util.Collections.emptySet();
    }
}
