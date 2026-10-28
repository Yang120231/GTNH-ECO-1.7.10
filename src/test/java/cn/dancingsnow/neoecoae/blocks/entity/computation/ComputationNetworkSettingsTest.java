package cn.dancingsnow.neoecoae.blocks.entity.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.util.IReadOnlyCollection;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingServiceView;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Computation controller planner switches are grid-wide, as ECOCraftingNetworkSettings is in the source. */
class ComputationNetworkSettingsTest {

    private static ECOComputationSystemBlockEntity host(ECOCraftingNetworkSettings settings) {
        ECOComputationSystemBlockEntity host = mock(ECOComputationSystemBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(settings).when(host)
            .networkSettings();
        return host;
    }

    @Test
    void togglesWriteTheGridValueAndKeepTheLocalCopyInStep() {
        var settings = mock(ECOCraftingNetworkSettings.class);
        when(settings.neoecoae$isIgnoringPatternSubstitutions()).thenReturn(false);
        when(settings.neoecoae$isCyclePlanningEnabled()).thenReturn(true);
        when(settings.neoecoae$isFastPlannerEnabled()).thenReturn(true);
        when(settings.neoecoae$isSmartDoublingEnabled()).thenReturn(true);
        var host = host(settings);
        host.toggleNetworkIgnoringPatternSubstitutions();
        host.toggleNetworkCyclePlanning();
        host.toggleNetworkFastPlanner();
        host.toggleNetworkSmartDoubling();
        verify(settings).neoecoae$setIgnoringPatternSubstitutions(true);
        verify(settings).neoecoae$setCyclePlanningEnabled(false);
        verify(settings).neoecoae$setFastPlannerEnabled(false);
        verify(settings).neoecoae$setSmartDoublingEnabled(false);
        assertTrue(host.isIgnoringPatternSubstitutions());
        assertFalse(host.isCyclePlanningEnabled());
        assertFalse(host.isFastPlannerEnabled());
        assertFalse(host.isSmartDoublingEnabled());
    }

    @Test
    void displayedValuesComeFromTheGridNotTheLocalField() {
        var settings = mock(ECOCraftingNetworkSettings.class);
        when(settings.neoecoae$isIgnoringPatternSubstitutions()).thenReturn(true);
        when(settings.neoecoae$isSmartDoublingEnabled()).thenReturn(false);
        when(settings.neoecoae$getSubstitutionPatternCount()).thenReturn(7);
        var host = host(settings);
        host.setIgnoringPatternSubstitutions(false);
        host.setSmartDoublingEnabled(true);
        assertTrue(host.isNetworkIgnoringPatternSubstitutions());
        assertFalse(host.isNetworkSmartDoublingEnabled());
        assertEquals(7, host.neoecoae$getSubstitutionPatternCount());
    }

    @Test
    void withoutAnActiveGridHostTheLocalFieldIsTheEffectiveValue() {
        var host = host(null);
        host.setSmartDoublingEnabled(true);
        assertTrue(host.isNetworkSmartDoublingEnabled());
        host.toggleNetworkSmartDoubling();
        assertFalse(host.isSmartDoublingEnabled());
        assertEquals(0, host.neoecoae$getSubstitutionPatternCount());
    }

    @Test
    void smartDoublingOffCapsDispatchAtActualParallelism() {
        assertEquals(Long.MAX_VALUE, ECOComputationSystemBlockEntity.smartDoublingDispatchLimit(true, 5));
        assertEquals(5, ECOComputationSystemBlockEntity.smartDoublingDispatchLimit(false, 5));
        assertEquals(1, ECOComputationSystemBlockEntity.smartDoublingDispatchLimit(false, 0));
        assertEquals(16_384, ECOComputationSystemBlockEntity.smartDoublingDispatchLimit(false, 1_000_000));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gridSettingsAreBroadcastToEveryHostIncludingInactiveOnes() throws Exception {
        var active = mock(ECOComputationSystemBlockEntity.class);
        var offline = mock(ECOComputationSystemBlockEntity.class);
        IGridNode first = mock(IGridNode.class), second = mock(IGridNode.class);
        when(first.getMachine()).thenReturn(active);
        when(second.getMachine()).thenReturn(offline);
        IMachineSet machines = mock(IMachineSet.class);
        when(machines.iterator()).thenAnswer(
            invocation -> Arrays.asList(first, second)
                .iterator());
        IReadOnlyCollection<Class<? extends IGridHost>> classes = mock(IReadOnlyCollection.class);
        when(classes.iterator()).thenAnswer(
            invocation -> Collections.<Class<? extends IGridHost>>singletonList(ECOComputationSystemBlockEntity.class)
                .iterator());
        IGrid grid = mock(IGrid.class);
        when(grid.getMachinesClasses()).thenReturn(classes);
        when(grid.getMachines(ECOComputationSystemBlockEntity.class)).thenReturn(machines);
        assertEquals(Arrays.asList(active, offline), NativeECOPlanning.allHosts(grid));

        CraftingServiceView view = mock(CraftingServiceView.class, CALLS_REAL_METHODS);
        var field = CraftingServiceView.class.getDeclaredField("nativeGrid");
        field.setAccessible(true);
        field.set(view, grid);
        ServerTasks.started();
        try {
            view.neoecoae$setSmartDoublingEnabled(false);
            view.neoecoae$setIgnoringPatternSubstitutions(true);
            view.neoecoae$setCyclePlanningEnabled(false);
            view.neoecoae$setFastPlannerEnabled(false);
        } finally {
            ServerTasks.stopped();
        }
        for (var host : Arrays.asList(active, offline)) {
            verify(host).setSmartDoublingEnabled(false);
            verify(host).setIgnoringPatternSubstitutions(true);
            verify(host).setCyclePlanningEnabled(false);
            verify(host).setFastPlannerEnabled(false);
        }
    }
}
