package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/**
 * Grid-wide planner switches read from the computation hosts. Deliberately independent of the recipe view: a UI
 * that only shows or toggles settings must never pay for a pattern snapshot or substitute validation.
 */
public final class NativeCraftingNetworkSettings implements ECOCraftingNetworkSettings {

    private final IGrid nativeGrid;
    private final NativePatternTrackingCache tracking;

    NativeCraftingNetworkSettings(IGrid grid, NativePatternTrackingCache tracking) {
        nativeGrid = grid;
        this.tracking = tracking;
    }

    private ECOComputationSystemBlockEntity settings() {
        return NativeECOPlanning.settings(nativeGrid, null);
    }

    @Override
    public boolean neoecoae$isIgnoringPatternSubstitutions() {
        var host = settings();
        return host != null && host.isIgnoringPatternSubstitutions();
    }

    @Override
    public void neoecoae$setIgnoringPatternSubstitutions(boolean value) {
        ServerTasks.assertServerThread();
        for (var host : NativeECOPlanning.allHosts(nativeGrid)) host.setIgnoringPatternSubstitutions(value);
    }

    @Override
    public int neoecoae$getSubstitutionPatternCount() {
        return tracking.substitutionPatternCount();
    }

    @Override
    public boolean neoecoae$isFastPlannerEnabled() {
        var host = settings();
        return host != null && host.isFastPlannerEnabled();
    }

    @Override
    public void neoecoae$setFastPlannerEnabled(boolean value) {
        ServerTasks.assertServerThread();
        for (var host : NativeECOPlanning.allHosts(nativeGrid)) host.setFastPlannerEnabled(value);
    }

    @Override
    public boolean neoecoae$isCyclePlanningEnabled() {
        var host = settings();
        return host != null && host.isCyclePlanningEnabled();
    }

    @Override
    public void neoecoae$setCyclePlanningEnabled(boolean value) {
        ServerTasks.assertServerThread();
        for (var host : NativeECOPlanning.allHosts(nativeGrid)) host.setCyclePlanningEnabled(value);
    }

    @Override
    public boolean neoecoae$isSmartDoublingEnabled() {
        var host = settings();
        return host == null || host.isSmartDoublingEnabled();
    }

    @Override
    public void neoecoae$setSmartDoublingEnabled(boolean value) {
        ServerTasks.assertServerThread();
        for (var host : NativeECOPlanning.allHosts(nativeGrid)) host.setSmartDoublingEnabled(value);
    }

    @Override
    public boolean neoecoae$hasComputationHost() {
        return settings() != null;
    }

    @Override
    public boolean neoecoae$hasComputationHost(IActionSource source) {
        return NativeECOPlanning.settings(nativeGrid, source) != null;
    }

    @Override
    public Set<ResourceLocation> neoecoae$getFuzzyPlanningItemIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (var host : NativeECOPlanning.hosts(nativeGrid)) ids.addAll(host.neoecoae$getFuzzyPlanningItemIds());
        return Collections.unmodifiableSet(ids);
    }
}
