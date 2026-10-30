package cn.dancingsnow.neoecoae.compat.nei;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import appeng.container.sync.handlers.BooleanSyncHandler;

class WorkstationPatternOverlayTest {

    @Test
    void processingTransferKeepsMixedOutputTypesAndLargeCountsWithoutChangingTheRecipe() throws Exception {
        var terminal = terminal(9, 3);
        IAEStack<?> item = stack(IAEItemStack.class, 256);
        IAEStack<?> water = stack(IAEFluidStack.class, 144);
        IAEStack<?> product = stack(IAEItemStack.class, 128);
        IAEStack<?> lava = stack(IAEFluidStack.class, 72);
        assertTrue(
            WorkstationPatternOverlay.transfer(terminal, Arrays.asList(item, water), Arrays.asList(product, lava), 3));
        var inputs = org.mockito.ArgumentCaptor.forClass(IAEStack.class);
        verify(terminal, times(2)).updateVirtualSlot(eq(StorageName.CRAFTING_INPUT), anyInt(), inputs.capture());
        assertInstanceOf(
            IAEItemStack.class,
            inputs.getAllValues()
                .get(0));
        assertEquals(
            768,
            inputs.getAllValues()
                .get(0)
                .getStackSize());
        assertInstanceOf(
            IAEFluidStack.class,
            inputs.getAllValues()
                .get(1));
        assertEquals(
            432,
            inputs.getAllValues()
                .get(1)
                .getStackSize());
        var outputs = org.mockito.ArgumentCaptor.forClass(IAEStack.class);
        verify(terminal, times(2)).updateVirtualSlot(eq(StorageName.CRAFTING_OUTPUT), anyInt(), outputs.capture());
        assertInstanceOf(
            IAEItemStack.class,
            outputs.getAllValues()
                .get(0));
        assertEquals(
            384,
            outputs.getAllValues()
                .get(0)
                .getStackSize());
        assertInstanceOf(
            IAEFluidStack.class,
            outputs.getAllValues()
                .get(1));
        assertEquals(
            216,
            outputs.getAllValues()
                .get(1)
                .getStackSize());
        assertEquals(256, item.getStackSize());
        assertEquals(144, water.getStackSize());
        assertEquals(128, product.getStackSize());
        assertEquals(72, lava.getStackSize());
        // AE2's crafting callback rejects fluids, so the server must receive processing mode before IO.
        var order = inOrder(terminal, terminal.craftingModeSync, terminal.getSyncManager());
        order.verify(terminal.craftingModeSync)
            .set(false);
        order.verify(terminal.getSyncManager())
            .flushSync();
        order.verify(terminal)
            .clear();
        order.verify(terminal, times(4))
            .updateVirtualSlot(any(), anyInt(), any());
        order.verify(terminal.getSyncManager())
            .flushSync();
    }

    @Test
    void fluidOnlyOutputRetainsItsOwnSlotAndMillibucketCount() throws Exception {
        var terminal = terminal(9, 3);
        assertTrue(
            WorkstationPatternOverlay.transfer(
                terminal,
                Collections.singletonList(stack(IAEFluidStack.class, 144)),
                Collections.singletonList(stack(IAEFluidStack.class, 72)),
                1));
        verify(terminal).updateVirtualSlot(
            eq(StorageName.CRAFTING_OUTPUT),
            eq(0),
            argThat(value -> value instanceof IAEFluidStack && value.getStackSize() == 72));
    }

    @Test
    void insufficientInputOrOutputCapacityPreservesTheExistingPattern() throws Exception {
        List<IAEStack<?>> pair = Arrays.asList(stack(IAEItemStack.class, 128), stack(IAEFluidStack.class, 144));
        for (boolean inputLimited : new boolean[] { true, false }) {
            var terminal = terminal(inputLimited ? 1 : 9, inputLimited ? 3 : 1);
            assertFalse(WorkstationPatternOverlay.transfer(terminal, pair, pair, 1));
            verify(terminal, never()).clear();
            verify(terminal, never()).updateVirtualSlot(any(), anyInt(), any());
            verifyNoInteractions(terminal.craftingModeSync);
        }
    }

    private static ContainerPatternTerm terminal(int inputSize, int outputSize) throws Exception {
        var terminal = mock(ContainerPatternTerm.class, RETURNS_DEEP_STUBS);
        var inputs = mock(AEStackInventorySyncHandler.class, RETURNS_DEEP_STUBS);
        var outputs = mock(AEStackInventorySyncHandler.class, RETURNS_DEEP_STUBS);
        when(
            inputs.get()
                .getSizeInventory()).thenReturn(inputSize);
        when(
            outputs.get()
                .getSizeInventory()).thenReturn(outputSize);
        set(terminal, "inputsSync", inputs);
        set(terminal, "outputsSync", outputs);
        set(terminal, "craftingModeSync", mock(BooleanSyncHandler.class));
        return terminal;
    }

    private static void set(ContainerPatternTerm terminal, String name, Object value) throws Exception {
        var field = ContainerPatternTerm.class.getField(name);
        field.setAccessible(true);
        field.set(terminal, value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends IAEStack> T stack(Class<T> type, long amount) {
        T stack = mock(type);
        AtomicLong count = new AtomicLong(amount);
        when(stack.getStackSize()).thenAnswer(call -> count.get());
        when(stack.copy()).thenAnswer(call -> stack(type, count.get()));
        when(stack.setStackSize(anyLong())).thenAnswer(call -> {
            count.set(call.getArgument(0));
            return stack;
        });
        return stack;
    }
}
