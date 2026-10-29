package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NativeUiAccessTest {

    private World world;
    private TileEntity tile;
    private EntityPlayer player;
    private Container container;

    @BeforeEach
    void openMenu() {
        world = mock(World.class);
        var chunks = mock(net.minecraft.world.chunk.IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(chunks);
        when(chunks.chunkExists(0, 0)).thenReturn(true);
        tile = new TileEntity();
        tile.setWorldObj(world);
        tile.xCoord = 2;
        tile.yCoord = 3;
        tile.zCoord = 4;
        player = mock(EntityPlayer.class);
        player.worldObj = world;
        container = mock(Container.class);
        player.openContainer = container;
        when(container.canInteractWith(player)).thenReturn(true);
        when(world.getTileEntity(2, 3, 4)).thenReturn(tile);
    }

    @Test
    void queuedEditsRequireTheSameOpenMenuAndPhysicalTile() {
        assertTrue(NativeUiAccess.canEdit(tile, player, container));
        player.openContainer = mock(Container.class);
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
        player.openContainer = container;
        when(world.getTileEntity(2, 3, 4)).thenReturn(new TileEntity());
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
    }

    @Test
    void changingWorldDistanceOrMenuValidityRejectsTheAction() {
        player.worldObj = mock(World.class);
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
        player.worldObj = world;
        when(player.getDistanceSq(2.5, 3.5, 4.5)).thenReturn(64.01);
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
        when(player.getDistanceSq(2.5, 3.5, 4.5)).thenReturn(64.0);
        assertTrue(NativeUiAccess.canEdit(tile, player, container));
        when(container.canInteractWith(player)).thenReturn(false);
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
    }

    @Test
    void unloadedChunksAndInvalidTilesRejectEditsWithoutLoadingTheWorld() {
        when(
            world.getChunkProvider()
                .chunkExists(0, 0)).thenReturn(false);
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
        verify(world, never()).getTileEntity(anyInt(), anyInt(), anyInt());
        when(
            world.getChunkProvider()
                .chunkExists(0, 0)).thenReturn(true);
        tile.invalidate();
        assertFalse(NativeUiAccess.canEdit(tile, player, container));
    }
}
