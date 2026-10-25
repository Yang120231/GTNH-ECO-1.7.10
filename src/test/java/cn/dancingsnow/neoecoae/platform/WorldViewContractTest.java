package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

class WorldViewContractTest {

    @Test
    void missingChunkProbeNeverAsksNativeWorldForTileOrBlock() {
        World world = mock(World.class);
        IChunkProvider chunks = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(chunks);
        when(chunks.chunkExists(-2, 3)).thenReturn(false);
        var view = WorldView.of(world);
        var position = new BlockPos(-17, 64, 63);
        assertFalse(view.hasChunkAt(position));
        assertNull(view.getBlockEntity(position));
        verify(world, never()).getTileEntity(anyInt(), anyInt(), anyInt());
        verify(world, never()).getBlock(anyInt(), anyInt(), anyInt());
        assertSame(view, WorldView.of(world));
    }

    @Test
    void mutableCursorDoesNotChangeRetainedPositionsAndRangesHandleReversedBounds() {
        var cursor = new BlockPos.MutableBlockPos(-17, -3, 25);
        var retained = cursor.immutable();
        cursor.set(20, 99, 101);
        assertEquals(new BlockPos(-17, -3, 25), retained);
        assertEquals(retained, BlockPos.of(retained.asLong()));
        var positions = new ArrayList<BlockPos>();
        BlockPos.betweenClosed(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0))
            .forEach(positions::add);
        assertEquals(
            java.util.Arrays.asList(new BlockPos(-1, 0, 0), new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)),
            positions);
    }
}
