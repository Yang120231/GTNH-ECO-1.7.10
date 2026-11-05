package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.item.IAEStackList;

class NativeForcedCraftingJobTest {

    @Test
    void nativeShortageEnablesShiftAndTheServerAcceptsTheSameIntent() {
        var job = completed();
        when(job.isSimulation()).thenReturn(true);
        var report = NativeCraftConfirmReports.state(job, false, 1);
        assertEquals(
            ECOCraftConfirmRules.StartGate.PLAN_NOT_STARTABLE,
            ECOCraftConfirmRules.startGate(report, false, false));
        assertEquals(ECOCraftConfirmRules.StartGate.READY, ECOCraftConfirmRules.startGate(report, true, false));
        assertEquals(
            ECOCraftConfirmRules.StartGate.NO_MATCHING_CPU,
            ECOCraftConfirmRules.startGate(report, true, true));
        assertNull(ECOCraftConfirmRules.rejectStart(report, true));
        assertEquals(ECOCraftConfirmError.INCOMPLETE_PLAN, ECOCraftConfirmRules.rejectStart(report, false));
        var forced = new NativeForcedCraftingJob(job);
        assertFalse(forced.isSimulation());
        assertEquals(CraftingMode.IGNORE_MISSING, forced.getCraftingMode());
        assertTrue(job.isSimulation(), "The displayed native plan stays unchanged");
    }

    @Test
    void cancelledUnfinishedFailedAndUnknownNativeJobsCannotBeForced() {
        var job = completed();
        when(job.isCancelled()).thenReturn(true);
        assertFalse(NativeForcedCraftingJob.supports(job));
        when(job.isCancelled()).thenReturn(false);
        when(job.isDone()).thenReturn(false);
        assertFalse(NativeForcedCraftingJob.supports(job));
        when(job.isDone()).thenReturn(true);
        when(job.getErrorMessage()).thenReturn("Planning failed");
        assertFalse(NativeForcedCraftingJob.supports(job));
        assertFalse(NativeForcedCraftingJob.supports(mock(ICraftingJob.class)));
        assertThrows(IllegalArgumentException.class, () -> new NativeForcedCraftingJob(job));
    }

    @Test
    void simulatedInputsBecomeWaitingItemsAndOtherNativeTasksStillRun() {
        var job = completed();
        var context = job.getContext();
        var missing = mock(SimulateMissingItemResolver.ConjureItemTask.class);
        var craft = mock(CraftingTask.class);
        when(context.getResolvedTasks()).thenReturn(Arrays.asList(missing, craft));
        IAEItemStack stack = mock(IAEItemStack.class), copy = mock(IAEItemStack.class);
        when(stack.copy()).thenReturn(copy);
        IAEFluidStack fluid = mock(IAEFluidStack.class), fluidCopy = mock(IAEFluidStack.class);
        when(fluid.copy()).thenReturn(fluidCopy);
        var storage = mock(MECraftingInventory.class);
        var cpu = mock(CraftingCPUCluster.class);
        var source = new BaseActionSource();
        try (var lists = mockConstruction(
            IAEStackList.class,
            (list, ignored) -> when(list.iterator()).thenReturn(
                Arrays.<appeng.api.storage.data.IAEStack<?>>asList(stack, fluid)
                    .iterator()))) {
            new NativeForcedCraftingJob(job).startCrafting(storage, cpu, source);
            verify(missing).populatePlan(
                lists.constructed()
                    .get(0));
            verify(missing, never()).startOnCpu(any(), any(), any());
            verify(cpu).addEmitable(copy);
            verify(cpu).addEmitable(fluidCopy);
            verify(craft).startOnCpu(context, cpu, storage);
            verify(cpu, never()).addStorage(any());
            assertSame(source, context.actionSource);
        }
    }

    private static CraftingJobV2<?> completed() {
        var job = mock(CraftingJobV2.class);
        when(job.getErrorMessage()).thenReturn("");
        when(job.isDone()).thenReturn(true);
        when(job.getContext()).thenReturn(mock(CraftingContext.class));
        return job;
    }
}
