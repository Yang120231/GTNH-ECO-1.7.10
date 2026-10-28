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
    // New hosts start unassigned so the allocator can place them; saved hosts keep their persisted value.
    @Persisted
    private int networkFrequency = NEFrequencyAllocator.UNASSIGNED;
    @Persisted
    private int cpuSelectionMode;
    @Persisted
    private boolean ignorePatternSubstitutions;
    @Persisted
    private boolean cyclePlanningEnabled = true;
    @Persisted
    private boolean fastPlannerEnabled = true;
    @Persisted
    private boolean smartDoublingEnabled = true;
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

    public boolean isSmartDoublingEnabled() {
        return smartDoublingEnabled;
    }

    public void setSmartDoublingEnabled(boolean value) {
        smartDoublingEnabled = value;
        setChanged();
    }

    /** Largest ordinary dispatch ECO may submit at once; unlimited while smart doubling is enabled. */
    public static long smartDoublingDispatchLimit(boolean smartDoubling, long coProcessors) {
        return smartDoubling ? Long.MAX_VALUE : Math.min(16_384L, Math.max(1L, coProcessors));
    }

    /**
     * The grid's planner settings when at least one active computation host backs them; otherwise {@code null},
     * and this host's own fields are the effective values (the same fallback 1.21 uses without a grid).
     */
    @org.jetbrains.annotations.Nullable
    public ECOCraftingNetworkSettings networkSettings() {
        var node = getMainNode();
        var settings = node == null ? null : ECOCraftingNetworkSettings.of(node.getGrid());
        return settings != null && settings != this && settings.neoecoae$hasComputationHost() ? settings : null;
    }

    public boolean isNetworkIgnoringPatternSubstitutions() {
        var settings = networkSettings();
        return settings != null ? settings.neoecoae$isIgnoringPatternSubstitutions() : ignorePatternSubstitutions;
    }

    public boolean isNetworkCyclePlanningEnabled() {
        var settings = networkSettings();
        return settings != null ? settings.neoecoae$isCyclePlanningEnabled() : cyclePlanningEnabled;
    }

    public boolean isNetworkFastPlannerEnabled() {
        var settings = networkSettings();
        return settings != null ? settings.neoecoae$isFastPlannerEnabled() : fastPlannerEnabled;
    }

    public boolean isNetworkSmartDoublingEnabled() {
        var settings = networkSettings();
        return settings != null ? settings.neoecoae$isSmartDoublingEnabled() : smartDoublingEnabled;
    }

    /** Toggles the grid-wide value: every computation host on the grid receives it, including this one. */
    public void toggleNetworkIgnoringPatternSubstitutions() {
        boolean value = !isNetworkIgnoringPatternSubstitutions();
        var settings = networkSettings();
        if (settings != null) settings.neoecoae$setIgnoringPatternSubstitutions(value);
        setIgnoringPatternSubstitutions(value);
    }

    public void toggleNetworkCyclePlanning() {
        boolean value = !isNetworkCyclePlanningEnabled();
        var settings = networkSettings();
        if (settings != null) settings.neoecoae$setCyclePlanningEnabled(value);
        setCyclePlanningEnabled(value);
    }

    public void toggleNetworkFastPlanner() {
        boolean value = !isNetworkFastPlannerEnabled();
        var settings = networkSettings();
        if (settings != null) settings.neoecoae$setFastPlannerEnabled(value);
        setFastPlannerEnabled(value);
    }

    public void toggleNetworkSmartDoubling() {
        boolean value = !isNetworkSmartDoublingEnabled();
        var settings = networkSettings();
        if (settings != null) settings.neoecoae$setSmartDoublingEnabled(value);
        setSmartDoublingEnabled(value);
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
        adoptGridPlannerSettings();
        if (cluster != null) {
            cluster.notifyGridCpuChange();
            NELogicalNetworkManager.refreshAfterGridChange(cluster);
        }
    }

    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        adoptGridPlannerSettings();
        if (cluster != null) NELogicalNetworkManager.refreshAfterGridChange(cluster);
    }

    /**
     * 1.21 keeps planner settings on the grid itself. Here they live on each host and are broadcast on change, so
     * a host that comes online in a grid with other active hosts adopts their values instead of overriding the
     * grid whenever it happens to sort first.
     */
    void adoptGridPlannerSettings() {
        if (level == null || level.isClientSide() || !getMainNode().isOnline()) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        for (var other : cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning
            .hosts(grid.nativeGrid())) {
            if (other == this) continue;
            boolean changed = ignorePatternSubstitutions != other.ignorePatternSubstitutions
                || cyclePlanningEnabled != other.cyclePlanningEnabled
                || fastPlannerEnabled != other.fastPlannerEnabled
                || smartDoublingEnabled != other.smartDoublingEnabled;
            if (changed) {
                ignorePatternSubstitutions = other.ignorePatternSubstitutions;
                cyclePlanningEnabled = other.cyclePlanningEnabled;
                fastPlannerEnabled = other.fastPlannerEnabled;
                smartDoublingEnabled = other.smartDoublingEnabled;
                setChanged();
            }
            return;
        }
    }

    public void neoecoae$setIgnoringPatternSubstitutions(boolean v) {
        setIgnoringPatternSubstitutions(v);
    }

    public int neoecoae$getSubstitutionPatternCount() {
        var settings = networkSettings();
        return settings == null ? 0 : settings.neoecoae$getSubstitutionPatternCount();
    }

    @Override
    public boolean neoecoae$isSmartDoublingEnabled() {
        return smartDoublingEnabled;
    }

    @Override
    public void neoecoae$setSmartDoublingEnabled(boolean v) {
        setSmartDoublingEnabled(v);
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
