package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

class NativeECOPlanningSessionTest {

    @Test
    void completedPlanBelongsToItsNetworkAcrossDimensionsButCannotCrossGridOrServerSession() {
        World world = mock(World.class);
        IGrid grid = mock(IGrid.class), otherGrid = mock(IGrid.class);
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.copy()).thenReturn(stack);
        ServerTasks.started();
        try {
            var job = new NativeECOCraftingJob(null, stack, null, "", world, grid, ServerTasks.captureSession());
            assertTrue(job.belongsToGrid(grid));
            assertFalse(job.belongsToGrid(otherGrid));
            ServerTasks.stopped();
            ServerTasks.started();
            assertFalse(job.belongsToGrid(grid));
        } finally {
            ServerTasks.stopped();
        }
    }

    @Test
    void loadedCpuInAnotherDimensionAcceptsTheSameNetworkPlan() throws ReflectiveOperationException {
        World terminalWorld = mock(World.class), cpuWorld = mock(World.class);
        IGrid grid = mock(IGrid.class), otherGrid = mock(IGrid.class);
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.copy()).thenReturn(stack);
        ICraftingPlan plan = mock(ICraftingPlan.class);
        when(plan.bytes()).thenReturn(100L);
        NativeECOCpuRuntime runtime = mock(NativeECOCpuRuntime.class);
        when(runtime.world()).thenReturn(cpuWorld);
        when(runtime.isActive()).thenReturn(true);
        when(runtime.availableStorage()).thenReturn(1000L);
        BaseActionSource source = new BaseActionSource();
        ICraftingLink link = mock(ICraftingLink.class);
        when(runtime.submit(plan, source, null)).thenReturn(link);
        ServerTasks.started();
        try {
            var job = new NativeECOCraftingJob(
                plan,
                stack,
                null,
                "",
                terminalWorld,
                grid,
                ServerTasks.captureSession());
            // Exercise admission without constructing AE2's game-only inventories and item definitions.
            NativeECOCpu cpu = mock(NativeECOCpu.class, CALLS_REAL_METHODS);
            var runtimeField = NativeECOCpu.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            runtimeField.set(cpu, runtime);
            assertSame(link, cpu.submitJob(grid, job, source, null));
            assertNull(cpu.submitJob(otherGrid, job, source, null));
            when(runtime.isActive()).thenReturn(false);
            assertNull(cpu.submitJob(grid, job, source, null), "An unloaded or offline CPU remains unavailable");
            when(runtime.isActive()).thenReturn(true);
            ServerTasks.stopped();
            ServerTasks.started();
            assertNull(cpu.submitJob(grid, job, source, null), "An old server session cannot submit its plan");
            verify(runtime, times(1)).submit(plan, source, null);
        } finally {
            ServerTasks.stopped();
        }
    }
}
