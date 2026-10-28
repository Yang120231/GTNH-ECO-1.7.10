package cn.dancingsnow.neoecoae.platform.ui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.fluid.FluidStack;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;

class NativeCraftingSessionTest {

    private static ECOCraftingSystemBlockEntity host() {
        var host = mock(ECOCraftingSystemBlockEntity.class);
        when(host.getTier()).thenReturn(StorageTiers.L4);
        when(host.getDisplayedCoolantFluid()).thenReturn(FluidStack.EMPTY);
        when(host.getDisplayedOutputFluid()).thenReturn(FluidStack.EMPTY);
        when(host.getMainNode()).thenReturn(mock(cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode.class));
        var capability = mock(CraftingCapabilitySnapshot.class, RETURNS_DEEP_STUBS);
        when(capability.networkMultiplier()).thenReturn(8);
        when(host.getCapabilitySnapshot()).thenReturn(capability);
        var cluster = mock(NECraftingCluster.class);
        when(cluster.getWorkers()).thenReturn(Collections.emptyList());
        when(cluster.getParallelCores()).thenReturn(Collections.emptyList());
        when(cluster.getPatternBuses()).thenReturn(Collections.emptyList());
        when(cluster.getNetworkMultiplier()).thenReturn(1);
        when(host.getCluster()).thenReturn(cluster);
        return host;
    }

    @Test
    void snapshotKeepsTheNetworkMultiplierAndExplainsRejectedModeSwitches() {
        var host = host();
        when(host.isModeSwitchBlockedNoticeActive()).thenReturn(true);
        when(host.getMaintenanceStatusLines()).thenReturn(
            Arrays.asList(
                Component.translatable("gui.neoecoae.crafting.virtual_status.title")
                    .withColor(0xD8B4FE),
                Component.translatable("gui.neoecoae.crafting.virtual_reason.host_count", 3, 8)
                    .withColor(0xFF5555)));
        var state = new NativeCraftingSession(host).capture();
        assertEquals(8, state.getInt("multiplier"), "The local cluster multiplier must not hide the network value");
        assertTrue(state.getBoolean("modeBlocked"));
        var maintenance = state.getList("maintenance", 8);
        assertEquals(2, maintenance.size());
        var reason = Component.fromJson(maintenance.getString(1));
        assertEquals(
            0xFF5555,
            reason.getStyle()
                .getColor()
                .getValue());
    }

    @Test
    void substitutionButtonFallsBackToTheHostWithoutAComputationHost() {
        var host = host();
        when(host.isLocallyIgnoringPatternSubstitutions()).thenReturn(true);
        var session = new NativeCraftingSession(host);
        var state = session.capture();
        assertTrue(state.getBoolean("substitutionsIgnored"));
        assertEquals(0, state.getInt("substitutionPatterns"));
        session.apply(5, 0);
        verify(host).applyNetworkIgnoringPatternSubstitutions(false);
        assertEquals(2, NativeCraftingPages.substitutionTooltip(true, 4)
            .size());
    }

    @Test
    void frequencyStepsInBothDirections() {
        var host = host();
        var session = new NativeCraftingSession(host);
        session.apply(4, -1);
        session.apply(4, 1);
        verify(host).adjustNetworkFrequency(-1);
        verify(host).adjustNetworkFrequency(1);
    }
}
