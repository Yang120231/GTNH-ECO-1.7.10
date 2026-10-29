package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class BetaStorageTileTest {

    @BeforeAll
    static void registerAliases() {
        BetaStorageTile.register();
    }

    private static NBTTagCompound old(String id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", id);
        tag.setInteger("x", 12);
        tag.setInteger("y", 64);
        tag.setInteger("z", -9);
        tag.setString("Subsystem", "storage");
        tag.setString("Tier", "l6");
        tag.setString("HostMode", "formed_normal");
        tag.setInteger("FacingMeta", 1);
        tag.setInteger("Priority", 42);
        tag.setString("untouched-data", "旧数据必须保留");
        return tag;
    }

    @Test
    void actualBetaIdsLoadAndRetainTheirPayloadBeforeTheWorldIsReady() {
        for (String id : new String[] { "neoecoae:eco_controller", "neoecoae:eco_drive", "neoecoae:eco_interface" }) {
            NBTTagCompound original = old(id);
            TileEntity tile = TileEntity.createAndLoadEntity(original);
            assertInstanceOf(BetaStorageTile.class, tile);
            NBTTagCompound saved = new NBTTagCompound();
            tile.writeToNBT(saved);
            assertEquals("旧数据必须保留", saved.getString("untouched-data"));
            assertEquals(original.getInteger("x"), saved.getInteger("x"));
            assertInstanceOf(BetaStorageTile.class, TileEntity.createAndLoadEntity(saved));
        }
    }

    @Test
    void owningBlockSelectsTheCorrectControllerTierAndKeepsPriorityAndFacing() {
        NBTTagCompound original = old("neoecoae:eco_controller");
        BetaStorageTile tile = (BetaStorageTile) TileEntity.createAndLoadEntity(original);
        World world = mock(World.class);
        when(world.getBlock(12, 64, -9)).thenReturn(StorageMachines.STORAGE_SYSTEM_L6.value());
        // Orientation updates check the chunk provider before touching the world.
        when(world.getChunkProvider()).thenReturn(mock(net.minecraft.world.chunk.IChunkProvider.class));
        tile.setWorldObj(world);
        try (var loader = mockStatic(cpw.mods.fml.common.Loader.class); var log = mockStatic(appeng.core.AELog.class)) {
            tile.updateEntity();
        }
        var replacement = org.mockito.ArgumentCaptor.forClass(TileEntity.class);
        verify(world).setTileEntity(eq(12), eq(64), eq(-9), replacement.capture());
        assertInstanceOf(StorageMachines.HostL6.class, replacement.getValue());
        ECOStorageSystemBlockEntity host = (ECOStorageSystemBlockEntity) replacement.getValue();
        CompoundTag saved = new CompoundTag();
        host.saveAdditional(saved, RegistryAccess.EMPTY);
        assertEquals(
            42,
            saved.getCompound("neoecoae_fields")
                .getCompound("storagePriority")
                .getInt("value"));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.minecraft.Direction.EAST,
            host.nativeBlockState()
                .getValue(NativeMachineBlock.FACING));
    }

    @Test
    void nonStorageBetaControllerKeepsItsSnapshotButStaysRemovable() {
        NBTTagCompound original = old("neoecoae:eco_controller");
        original.setString("Subsystem", "crafting");
        BetaStorageTile tile = (BetaStorageTile) TileEntity.createAndLoadEntity(original);
        World world = mock(World.class);
        when(world.getBlock(12, 64, -9)).thenReturn(CraftingMachines.CRAFTING_SYSTEM_L6.value());
        when(world.getTileEntity(12, 64, -9)).thenReturn(tile);
        tile.setWorldObj(world);
        tile.updateEntity();
        verify(world, never()).setTileEntity(anyInt(), anyInt(), anyInt(), any());
        NBTTagCompound saved = new NBTTagCompound();
        tile.writeToNBT(saved);
        assertEquals("旧数据必须保留", saved.getString("untouched-data"));
        assertFalse(BetaStorageTile.protects(world, 12, 64, -9));

        when(world.getBlock(12, 64, -9)).thenReturn(StorageMachines.STORAGE_SYSTEM_L6.value());
        assertTrue(BetaStorageTile.protects(world, 12, 64, -9));
    }

    @Test
    void failedConversionKeepsTheWholeOldSnapshot() {
        NBTTagCompound original = old("neoecoae:eco_drive");
        // An unresolved cell must keep its raw tag instead of becoming an empty drive.
        NBTTagCompound cell = new NBTTagCompound();
        cell.setShort("id", (short) 30999);
        cell.setByte("Count", (byte) 1);
        original.setTag("Cell", cell);
        BetaStorageTile tile = (BetaStorageTile) TileEntity.createAndLoadEntity(original);
        World world = mock(World.class);
        when(world.getBlock(12, 64, -9)).thenReturn(StorageMachines.ECO_DRIVE.value());
        tile.setWorldObj(world);
        tile.updateEntity();
        verify(world, never()).setTileEntity(anyInt(), anyInt(), anyInt(), any());
        NBTTagCompound saved = new NBTTagCompound();
        tile.writeToNBT(saved);
        assertEquals(cell, saved.getCompoundTag("Cell"));
    }
}
