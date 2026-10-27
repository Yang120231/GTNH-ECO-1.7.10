package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

public class ECOComputationSystemBlockEntity
    extends NEBlockEntity<NEComputationCluster, ECOComputationSystemBlockEntity>
    implements ManagedHolder, ECOCraftingNetworkSettings {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    private final StorageTierDescriptor tier;
    @Persisted
    private int networkFrequency = NEFrequencyAllocator.DEFAULT_FREQUENCY;
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
    @Persisted
    private boolean mirrorBuild;

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
        return networkFrequency >= 1 && networkFrequency <= NEFrequencyAllocator.FREQUENCY_COUNT;
    }

    public int getNetworkFrequency() {
        return hasNetworkFrequency() ? networkFrequency : NEFrequencyAllocator.DEFAULT_FREQUENCY;
    }

    public void assignNetworkFrequency(int value) {
        if (hasNetworkFrequency()) return;
        networkFrequency = NEFrequencyAllocator.normalize(value);
        setChanged();
        markForClientUpdate();
    }

    public void setNetworkFrequency(int value) {
        networkFrequency = NEFrequencyAllocator.normalize(value);
        setChanged();
        markForClientUpdate();
        if (cluster != null) NELogicalNetworkManager.refresh(cluster);
    }

    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    public boolean isMirrorBuild() {
        return mirrorBuild;
    }

    public void setMirrorBuild(boolean value) {
        mirrorBuild = value;
        saveChanges();
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
        var result = new java.util.LinkedHashSet<ResourceLocation>();
        if (cluster != null && !cluster.isDestroyed()) {
            var members = cluster.getBlockEntities();
            while (members.hasNext()) {
                var member = members.next();
                if (member instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity<?>)
                    result.addAll(
                        ((cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity<?>) member)
                            .getFuzzyPlanningItemIds());
            }
        }
        return result;
    }
}
