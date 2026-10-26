package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.storage.data.IAEItemStack;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

class NativeECOPlanningSessionTest {

    @Test
    void completedPlanCannotCrossWorldGridOrServerSession() {
        World world = mock(World.class), otherWorld = mock(World.class);
        IGrid grid = mock(IGrid.class), otherGrid = mock(IGrid.class);
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.copy()).thenReturn(stack);
        ServerTasks.started();
        try {
            var job = new NativeECOCraftingJob(null, stack, null, "", world, grid, ServerTasks.captureSession());
            assertTrue(job.belongsTo(world, grid));
            assertFalse(job.belongsTo(otherWorld, grid));
            assertFalse(job.belongsTo(world, otherGrid));
            ServerTasks.stopped();
            ServerTasks.started();
            assertFalse(job.belongsTo(world, grid));
        } finally {
            ServerTasks.stopped();
        }
    }
}
