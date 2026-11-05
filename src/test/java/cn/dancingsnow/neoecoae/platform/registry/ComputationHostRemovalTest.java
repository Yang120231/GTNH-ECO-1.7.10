package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

class ComputationHostRemovalTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void oneRemovalStaysRemovedWhenDisconnectUpdatesTheOldTile(boolean formed) throws Exception {
        NativeMachineBlock host = new NativeMachineBlock(null, null, "computation_system_l4");
        Block air = new Block(Material.air) {};
        AtomicReference<BlockState> placed = new AtomicReference<>(host.defaultBlockState());
        World world = mock(World.class);
        WorldView view = mock(WorldView.class);
        BlockPos pos = new BlockPos(8, 64, 8);
        var tile = tile(view, pos, host.defaultBlockState(), formed);
        when(view.getBlockState(pos)).thenAnswer(ignored -> placed.get());
        when(world.setBlockToAir(8, 64, 8)).thenAnswer(ignored -> {
            placed.set(BlockState.defaultState(air));
            // Chunk.func_150807_a writes air before breakBlock/invalidate disconnects the old tile.
            tile.updateState(true);
            return placed.get()
                .is(air);
        });
        assertTrue(host.removedByPlayer(world, mock(EntityPlayer.class), 8, 64, 8, true));
        assertTrue(
            placed.get()
                .is(air));
        verify(world, times(1)).setBlockToAir(8, 64, 8);
        verify(view, never()).setBlock(any(), any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void staleTileCannotOverwriteAReplacementMachine(boolean formed) throws Exception {
        Block original = new Block(Material.iron) {}, replacement = new Block(Material.iron) {};
        Map<Property<?>, Object> values = new HashMap<>();
        values.put(NativeMachineBlock.MIRRORED, false);
        var state = new BlockState(original, values);
        WorldView view = mock(WorldView.class);
        var pos = new BlockPos(8, 64, 8);
        var tile = tile(view, pos, state, formed);
        when(view.getBlockState(pos)).thenReturn(new BlockState(replacement, values));
        tile.updateState(true);
        verify(view, never()).setBlock(any(), any(), anyInt());
        verify(tile, never()).refreshIdlePower();
    }

    private static ECOComputationSystemBlockEntity tile(WorldView view, BlockPos pos, BlockState state, boolean formed)
        throws Exception {
        var tile = mock(ECOComputationSystemBlockEntity.class, CALLS_REAL_METHODS);
        var level = NativeNetworkBlockEntity.class.getDeclaredField("level");
        level.setAccessible(true);
        level.set(tile, view);
        var formation = NEBlockEntity.class.getDeclaredField("formed");
        formation.setAccessible(true);
        formation.setBoolean(tile, formed);
        doReturn(pos).when(tile)
            .getBlockPos();
        doReturn(state).when(tile)
            .getBlockState();
        doReturn(false).when(tile)
            .notLoaded();
        return tile;
    }
}
