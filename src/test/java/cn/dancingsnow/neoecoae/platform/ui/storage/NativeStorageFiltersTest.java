package cn.dancingsnow.neoecoae.platform.ui.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;

class NativeStorageFiltersTest {

    @Test
    void samplingFluidContainersPreservesTheCarriedStackAndShiftSamplesTheExactItem() {
        ItemStack carried = new ItemStack(new SampleContainer(), 17, 7);
        carried.setStackDisplayName("Tagged container");
        ItemStack before = carried.copy();
        assertEquals(AEFluidKey.of(TestFluids.WATER), NativeStorageFilters.sample(carried, false));
        assertTrue(ItemStack.areItemStacksEqual(before, carried));
        assertEquals(AEItemKey.of(before), NativeStorageFilters.sample(carried, true));
        assertTrue(ItemStack.areItemStacksEqual(before, carried));
    }

    @Test
    void ordinaryItemsRetainNbtAndEmptySamplesClearTheSlot() {
        ItemStack carried = new ItemStack(TestItems.STONE, 64, 2);
        carried.setStackDisplayName("Exact identity");
        assertEquals(AEItemKey.of(carried), NativeStorageFilters.sample(carried, true));
        assertNotEquals(AEItemKey.of(new ItemStack(TestItems.STONE, 1, 2)), NativeStorageFilters.sample(carried, true));
        assertNull(NativeStorageFilters.sample(null, false));
        assertEquals(64, carried.stackSize);
    }

    private static final class SampleContainer extends Item implements IFluidContainerItem {

        @Override
        public FluidStack getFluid(ItemStack container) {
            container.stackSize = 1;
            container.setTagCompound(null);
            FluidStack sample = mock(FluidStack.class);
            sample.amount = 1000;
            when(sample.getFluid()).thenReturn(TestFluids.WATER);
            return sample;
        }

        @Override
        public int getCapacity(ItemStack container) {
            return 1000;
        }

        @Override
        public int fill(ItemStack container, FluidStack resource, boolean doFill) {
            throw new AssertionError("sampling must not fill");
        }

        @Override
        public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {
            throw new AssertionError("sampling must not drain");
        }
    }
}
