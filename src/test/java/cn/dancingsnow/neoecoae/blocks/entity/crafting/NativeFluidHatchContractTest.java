package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestFluids;

class NativeFluidHatchContractTest {

    private static Iterable<IFluidHandler> hatches() {
        var input = mock(ECOFluidInputHatchBlockEntity.class, CALLS_REAL_METHODS);
        input.tank = tank();
        var output = mock(ECOFluidOutputHatchBlockEntity.class, CALLS_REAL_METHODS);
        output.tank = tank();
        for (var hatch : Arrays.<IFluidHandler>asList(input, output)) {
            doReturn(tagged("owned", 500)).when(hatch)
                .drain(ForgeDirection.UNKNOWN, 500, false);
            doReturn(tagged("owned", 125)).when(hatch)
                .drain(ForgeDirection.UNKNOWN, 125, true);
        }
        return Arrays.asList(input, output);
    }

    private static cn.dancingsnow.neoecoae.platform.fluid.FluidTank tank() {
        var result = mock(cn.dancingsnow.neoecoae.platform.fluid.FluidTank.class);
        var nativeTank = mock(net.minecraftforge.fluids.FluidTank.class);
        when(result.nativeTank()).thenReturn(nativeTank);
        var stored = tagged("owned", 1000);
        when(nativeTank.getFluid()).thenReturn(stored);
        var wrapped = cn.dancingsnow.neoecoae.platform.fluid.FluidStack.fromNative(stored);
        when(result.getFluid()).thenReturn(wrapped);
        return result;
    }

    private static FluidStack tagged(String identity, int amount) {
        var stack = mock(FluidStack.class, CALLS_REAL_METHODS);
        doReturn(TestFluids.WATER).when(stack)
            .getFluid();
        stack.amount = amount;
        stack.tag = new NBTTagCompound();
        stack.tag.setString("identity", identity);
        return stack;
    }

    @Test
    void explicitDrainRequiresTheEntireFluidIdentity() {
        for (var hatch : hatches()) {
            assertNull(hatch.drain(ForgeDirection.UNKNOWN, tagged("different", 500), false));
            assertNull(hatch.drain(ForgeDirection.UNKNOWN, tagged("different", 500), true));
            verify(hatch, never()).drain(ForgeDirection.UNKNOWN, 500, false);
            verify(hatch, never()).drain(ForgeDirection.UNKNOWN, 500, true);
            var simulated = hatch.drain(ForgeDirection.UNKNOWN, tagged("owned", 500), false);
            assertEquals(500, simulated.amount);
            assertEquals("owned", simulated.tag.getString("identity"));
            assertEquals(125, hatch.drain(ForgeDirection.UNKNOWN, tagged("owned", 125), true).amount);
            verify(hatch).drain(ForgeDirection.UNKNOWN, 500, false);
            verify(hatch).drain(ForgeDirection.UNKNOWN, 125, true);
        }
    }
}
