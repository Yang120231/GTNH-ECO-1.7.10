package cn.dancingsnow.neoecoae.platform.ae2.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.network.ServerThread;

@SuppressWarnings({ "rawtypes", "unchecked" })
class NativeIncrementalStorageListTest {

    private final IAEStackType type = mock(IAEStackType.class);
    private final IItemList list = mock(IItemList.class);

    @AfterEach
    void reset() {
        ServerThread.clear();
        PlanningSettings.Storage.incrementalNetworkStorageList = true;
    }

    @Test
    void existingEntriesMoveByTheExactDifferenceAndSaturate() {
        IAEStack stored = stack(10), more = stack(5), less = stack(-15), huge = stack(Long.MAX_VALUE);
        when(list.findPrecise(any())).thenReturn(stored);
        assertTrue(NativeIncrementalStorageList.apply(list, type, Collections.singletonList(more)));
        verify(stored).setStackSize(15);
        when(stored.getStackSize()).thenReturn(15L);
        assertTrue(NativeIncrementalStorageList.apply(list, type, Collections.singletonList(less)));
        verify(stored).setStackSize(0);
        when(stored.getStackSize()).thenReturn(1L);
        assertTrue(NativeIncrementalStorageList.apply(list, type, Collections.singletonList(huge)));
        verify(stored).setStackSize(Long.MAX_VALUE);
        verify(list, never()).add(any());
    }

    @Test
    void newEntriesAreAddedWithoutRequestFlags() {
        IAEStack change = stack(7), clean = mock(IAEStack.class);
        when(change.copy()).thenReturn(clean);
        when(clean.reset()).thenReturn(clean);
        when(clean.setStackSize(7)).thenReturn(clean);
        assertTrue(NativeIncrementalStorageList.apply(list, type, Arrays.asList(null, stack(0), change)));
        verify(clean).reset();
        verify(clean).setStackSize(7);
        verify(list).add(clean);
    }

    @Test
    void anythingThatCannotBeAppliedExactlyAsksForARebuild() {
        assertFalse(NativeIncrementalStorageList.apply(list, type, Collections.singletonList(stack(-1))));
        IAEStack stored = stack(3);
        when(list.findPrecise(any())).thenReturn(stored);
        assertFalse(NativeIncrementalStorageList.apply(list, type, Collections.singletonList(stack(-4))));
        IAEStack foreign = stack(1);
        when(foreign.getStackType()).thenReturn(mock(IAEStackType.class));
        assertFalse(NativeIncrementalStorageList.apply(list, type, Collections.singletonList(foreign)));
        verify(stored, never()).setStackSize(anyLong());
    }

    @Test
    void onlyServerThreadNetworksWithoutSubnetworkReadsAreEligible() {
        NativeNetworkReads local = mock(
            NativeNetworkReads.class,
            withSettings().extraInterfaces(IMEInventoryHandler.class));
        NativeNetworkReads subnet = mock(
            NativeNetworkReads.class,
            withSettings().extraInterfaces(IMEInventoryHandler.class));
        when(subnet.neoecoae$readsOtherNetwork()).thenReturn(true);
        assertFalse(NativeIncrementalStorageList.eligible((IMEInventoryHandler) local));
        ServerThread.capture();
        assertTrue(NativeIncrementalStorageList.eligible((IMEInventoryHandler) local));
        assertFalse(NativeIncrementalStorageList.eligible((IMEInventoryHandler) subnet));
        assertFalse(NativeIncrementalStorageList.eligible(mock(IMEInventoryHandler.class)));
        PlanningSettings.Storage.incrementalNetworkStorageList = false;
        assertFalse(NativeIncrementalStorageList.eligible((IMEInventoryHandler) local));
    }

    private IAEStack stack(long size) {
        IAEStack stack = mock(IAEStack.class);
        when(stack.getStackType()).thenReturn(type);
        when(stack.getStackSize()).thenReturn(size);
        return stack;
    }
}
