package cn.dancingsnow.neoecoae.multiblock.placement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestBlocks;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

class MultiBlockPlacementServiceTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void conflictsPreventPlacementEvenInCreative() {
        ServerWorldView level = mock(ServerWorldView.class);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        var plan = new MultiBlockPlacementPlan(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(BlockPos.ZERO),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            0);

        assertFalse(MultiBlockPlacementService.buildInstant(level, plan, player));
        verifyNoInteractions(level, player);
    }

    @Test
    void insufficientMaterialsPreventAnyWorldChanges() {
        ServerWorldView level = mock(ServerWorldView.class);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        InventoryPlayer inventory = new InventoryPlayer(player);
        player.inventory = inventory;
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        var block = new WorldPlannedBlock(
            BlockPos.ZERO,
            BlockState.defaultState(TestBlocks.STONE),
            new ItemStack(TestItems.COBBLESTONE));
        var plan = new MultiBlockPlacementPlan(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(block),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(block),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new RequiredItem(new ItemStack(TestItems.COBBLESTONE), 1)),
            0);

        assertFalse(MultiBlockPlacementService.buildInstant(level, plan, player));
        verifyNoInteractions(level);
        assertTrue(
            java.util.Arrays.stream(inventory.mainInventory)
                .allMatch(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks::isEmpty));
    }

    @Test
    void blockPlacedSincePreviewIsReusedWithoutConsumingMaterials() {
        ServerWorldView level = mock(ServerWorldView.class);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        InventoryPlayer inventory = new InventoryPlayer(player);
        inventory.setInventorySlotContents(0, new ItemStack(TestItems.COBBLESTONE));
        player.inventory = inventory;
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        WorldServer world = mock(WorldServer.class);
        when(level.nativeWorld()).thenReturn(world);
        player.worldObj = world;
        when(world.canMineBlock(player, 0, 0, 0)).thenReturn(true);
        when(player.canPlayerEdit(eq(0), eq(0), eq(0), eq(1), any())).thenReturn(true);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(BlockState.defaultState(TestBlocks.STONE));
        var block = new WorldPlannedBlock(
            BlockPos.ZERO,
            BlockState.defaultState(TestBlocks.STONE),
            new ItemStack(TestItems.COBBLESTONE));
        var plan = new MultiBlockPlacementPlan(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(block),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(block),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new RequiredItem(new ItemStack(TestItems.COBBLESTONE), 1)),
            0);

        assertTrue(MultiBlockPlacementService.buildInstant(level, plan, player));
        assertEquals(1, inventory.getStackInSlot(0).stackSize);
        verify(level, never()).setBlock(any(), any(), anyInt());
    }
}
