package cn.dancingsnow.neoecoae.platform.gt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

class NativeProcessingMachinesTest {

    @AfterEach
    void clear() {
        NativeProcessingMachines.clear();
    }

    @Test
    void onlyNativeConfirmedLoadedMembershipResolvesToTheSameControllerTile() throws Exception {
        var world = mock(World.class);
        var provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, mock(WorldProvider.class));
        var chunks = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(chunks);
        when(chunks.chunkExists(0, 0)).thenReturn(true);
        var controller = new TileEntity();
        controller.setWorldObj(world);
        controller.xCoord = 3;
        controller.yCoord = 64;
        var input = new TileEntity();
        input.setWorldObj(world);
        input.xCoord = 4;
        input.yCoord = 64;
        when(world.getTileEntity(3, 64, 0)).thenReturn(controller);
        var position = new BlockPos(4, 64, 0);
        NativeProcessingMachines.observeController(controller, Collections.singletonList(input), true);
        assertEquals(
            1,
            NativeProcessingMachines.controllers(world, position)
                .size());
        assertTrue(NativeProcessingMachines.isCurrentController(world, new BlockPos(3, 64, 0), controller));
        when(chunks.chunkExists(0, 0)).thenReturn(false);
        assertEquals(
            1,
            NativeProcessingMachines.controllers(world, position)
                .size(),
            "Metadata lookup never reads or loads a world position");
        when(chunks.chunkExists(0, 0)).thenReturn(true);
        when(world.getTileEntity(3, 64, 0)).thenReturn(new TileEntity());
        assertFalse(NativeProcessingMachines.isCurrentController(world, new BlockPos(3, 64, 0), new TileEntity()));
        when(world.getTileEntity(3, 64, 0)).thenReturn(controller);
        NativeProcessingMachines.observeController(controller, Collections.emptyList(), false);
        assertTrue(
            NativeProcessingMachines.controllers(world, position)
                .isEmpty());
        verify(world, never()).getChunkFromChunkCoords(anyInt(), anyInt());
        verify(world, never()).getTileEntity(anyInt(), anyInt(), anyInt());
    }
}
