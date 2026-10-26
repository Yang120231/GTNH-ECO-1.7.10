package cn.dancingsnow.neoecoae.platform.ui.workstation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.ui.NativeOwnedFluidSync;

class NativeWorkstationContractsTest {

    @Test
    void tankContainersUseActualTankLimitsAndInvalidateRecipeOnlyOnMutation() {
        var host = mock(ECOIntegratedWorkingStationBlockEntity.class);
        var input = mock(FluidTank.class);
        var output = new FluidTank(64000);
        when(host.getInputTank()).thenReturn(input);
        when(host.getOutputTank()).thenReturn(output);
        var tank = new NativeWorkstationSession(host).tank(true);
        var request = mock(FluidStack.class);
        var drained = mock(FluidStack.class);
        drained.amount = 1000;
        when(input.getCapacity()).thenReturn(64000);
        when(input.fill(request, false)).thenReturn(64000);
        when(input.fill(request, true)).thenReturn(64000);
        when(input.drain(1000, false)).thenReturn(drained);
        when(input.drain(1000, true)).thenReturn(drained);
        assertEquals(64000, tank.getCapacity());
        assertEquals(64000, tank.fill(request, false));
        verify(host, never()).onChangeTank();
        assertEquals(64000, tank.fill(request, true));
        assertSame(drained, tank.drain(1000, false));
        assertSame(drained, tank.drain(1000, true));
        assertNull(output.getFluid());
        verify(host, times(2)).onChangeTank();
    }

    @Test
    void outputGaugeFillsContainersWithoutAcceptingFluidIntoOutput() {
        var host = mock(ECOIntegratedWorkingStationBlockEntity.class);
        var output = new NativeOwnedFluidSync(host, new FluidTank(64000), false, true);
        assertFalse(output.canFillSlot());
        assertTrue(output.canDrainSlot());
        assertFalse(output.isPhantom());
        assertFalse(output.controlsAmount());
    }

    @Test
    void facingControllerSwapsVisualLeftAndRightExactlyOnce() {
        for (RelativeSide side : RelativeSide.values()) {
            RelativeSide actual = NativeWorkstationPages.internalSide(side);
            assertEquals(side, NativeWorkstationPages.internalSide(actual));
            if (side == RelativeSide.LEFT) assertEquals(RelativeSide.RIGHT, actual);
            else if (side == RelativeSide.RIGHT) assertEquals(RelativeSide.LEFT, actual);
            else assertEquals(side, actual);
        }
    }
}
