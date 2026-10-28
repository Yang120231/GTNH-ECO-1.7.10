package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyGrid;

/** Virtual crafting power is all-or-nothing; a partial extraction must be refunded in raw grid energy. */
class CraftingVirtualPowerRefundTest {

    @AfterEach
    void restoreMultiplier() {
        PowerMultiplier.CONFIG.multiplier = 1.0;
    }

    @Test
    void partialExtractionIsRefundedWithTheConfigMultiplierApplied() {
        PowerMultiplier.CONFIG.multiplier = 2.0;
        IEnergyGrid energy = mock(IEnergyGrid.class);
        when(energy.extractAEPower(100.0, Actionable.SIMULATE, PowerMultiplier.CONFIG)).thenReturn(100.0);
        when(energy.extractAEPower(100.0, Actionable.MODULATE, PowerMultiplier.CONFIG)).thenReturn(40.0);
        assertFalse(ECOCraftingSystemBlockEntity.extractExactly(energy, 100.0));
        // 40 AE was reported after dividing by the multiplier, so 80 raw energy left the grid.
        verify(energy).injectPower(80.0, Actionable.MODULATE);
    }

    @Test
    void exactExtractionNeedsNoRefund() {
        IEnergyGrid energy = mock(IEnergyGrid.class);
        when(energy.extractAEPower(anyDouble(), any(Actionable.class), any(PowerMultiplier.class))).thenReturn(100.0);
        assertTrue(ECOCraftingSystemBlockEntity.extractExactly(energy, 100.0));
        verify(energy, never()).injectPower(anyDouble(), any(Actionable.class));
    }
}
