package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import reobf.proghatches.eucrafting.IInputMightBeEmptyPattern;

/** Programmable Hatches emitter/circuit patterns advertise a zero-sized placeholder input. */
class NativeEmptyInputPatternTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private static IAEItemStack stack(Item item, long size) {
        var stack = mock(IAEItemStack.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getItemStack()).thenReturn(new ItemStack(item, (int) size));
        when(stack.getStackSize()).thenReturn(size);
        return stack;
    }

    private static ICraftingPatternDetails pattern(boolean marked) {
        var pattern = marked
            ? mock(ICraftingPatternDetails.class, withSettings().extraInterfaces(IInputMightBeEmptyPattern.class))
            : mock(ICraftingPatternDetails.class);
        IAEStack<?>[] inputs = { stack(TestItems.STONE, 0) };
        IAEStack<?>[] outputs = { stack(TestItems.DIRT, 1) };
        when(pattern.getPattern()).thenReturn(new ItemStack(TestItems.DIAMOND));
        when(pattern.getAEInputs()).thenReturn(inputs);
        when(pattern.getCondensedAEInputs()).thenReturn(inputs);
        when(pattern.getAEOutputs()).thenReturn(outputs);
        return pattern;
    }

    @Test
    void markedPatternDropsZeroSizedPlaceholderInput() {
        var details = NativePatternDetails.capture(pattern(true), mock(World.class), Collections.emptyList());

        assertEquals(0, details.getInputs().length);
        assertNull(details.getSparseInputs()[0]);
        assertEquals(
            AEItemKey.of(TestItems.DIRT),
            details.getOutputs()
                .get(0)
                .what());
    }

    @Test
    void unmarkedZeroSizedInputIsRejectedNotCrashing() {
        assertThrows(
            IllegalArgumentException.class,
            () -> NativePatternDetails.capture(pattern(false), mock(World.class), Collections.emptyList()));
    }
}
