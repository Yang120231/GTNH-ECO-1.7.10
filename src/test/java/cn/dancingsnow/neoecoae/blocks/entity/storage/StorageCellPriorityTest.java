package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class StorageCellPriorityTest {

    @Test
    void cellOffsetSurvivesCopiesWithoutChangingContentsOrOtherSettings() {
        var stack = new ItemStack(TestItems.DIAMOND);
        var data = new CompoundTag();
        data.putString("keep", "partition and upgrades");
        CellComponents.writeCustomData(stack, CustomData.of(data));
        assertEquals(0, ECOStorageCellSettings.priority(stack));
        ECOStorageCellSettings.setPriority(stack, -123);
        assertEquals(-123, ECOStorageCellSettings.priority(stack.copy()));
        assertEquals("partition and upgrades", CellComponents.readCustomData(stack).copyTag().getString("keep"));
        assertEquals(77, StoragePriority.mountPriority(200, ECOStorageCellSettings.priority(stack)));
        assertEquals(Integer.MAX_VALUE, StoragePriority.mountPriority(Integer.MAX_VALUE, 100));
        assertEquals(Integer.MIN_VALUE, StoragePriority.mountPriority(Integer.MIN_VALUE, -100));
        ECOInfiniteStorageMember.markMember(stack, UUID.randomUUID());
        assertFalse(ECOStorageCellSettings.canConfigure(stack));
        ECOStorageCellSettings.setPriority(stack, 999);
        assertEquals(-123, ECOStorageCellSettings.priority(stack));
    }

    @Test
    void actionsResolveOnlyTheSameLoadedInstallationOfTheFormedHost() {
        var host = mock(ECOStorageSystemBlockEntity.class);
        var cluster = mock(NEStorageCluster.class);
        var drive = mock(ECODriveBlockEntity.class);
        var world = mock(WorldView.class);
        var position = new BlockPos(3, 10, 5);
        UUID installation = UUID.randomUUID();
        when(host.isFormed()).thenReturn(true);
        when(host.getCluster()).thenReturn(cluster);
        when(host.getLevel()).thenReturn(world);
        when(world.hasChunkAt(position)).thenReturn(true);
        when(world.getBlockEntity(position)).thenReturn(drive);
        when(cluster.getDrives()).thenReturn(Collections.singletonList(drive));
        when(drive.getStorageController()).thenReturn(host);
        when(drive.getBlockPos()).thenReturn(position);
        when(drive.getCellInteractionId()).thenReturn(installation);
        when(drive.getCellStack()).thenReturn(new ItemStack(TestItems.DIAMOND));
        when(drive.getCellInventory()).thenReturn(mock(IECOStorageCell.class));
        assertTrue(StorageCellPriority.set(host, position, installation, 42));
        verify(drive).setConfiguredCellPriority(42);
        when(drive.getCellInteractionId()).thenReturn(UUID.randomUUID());
        assertFalse(StorageCellPriority.set(host, position, installation, 43));
        when(drive.getCellInteractionId()).thenReturn(installation);
        when(world.hasChunkAt(position)).thenReturn(false);
        assertFalse(StorageCellPriority.set(host, position, installation, 44));
        when(world.hasChunkAt(position)).thenReturn(true);
        when(world.getBlockEntity(position)).thenReturn(mock(ECODriveBlockEntity.class));
        assertFalse(StorageCellPriority.set(host, position, installation, 45));
        when(world.getBlockEntity(position)).thenReturn(drive);
        when(host.isFormed()).thenReturn(false);
        assertFalse(StorageCellPriority.set(host, position, installation, 46));
        verify(drive, times(1)).setConfiguredCellPriority(anyInt());
    }
}
