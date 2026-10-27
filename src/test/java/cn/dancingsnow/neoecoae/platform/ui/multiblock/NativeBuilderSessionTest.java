package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import cn.dancingsnow.neoecoae.multiblock.placement.WorldPlannedBlock;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestBlocks;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

class NativeBuilderSessionTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void inventoryChangesUpdateReadinessAndMaterialAmounts() {
        var player = player();
        var config = config(player, plan(Collections.emptyList()), false, false);
        var missing = capture(config);
        assertEquals("not_enough_items", missing.getString("status"));
        assertFalse(missing.getBoolean("canBuild"));
        assertTrue(missing.getBoolean("editable"));
        assertEquals(
            0,
            missing.getList("materials", 10)
                .getCompound(0)
                .getInt("available"));
        player.inventory.setInventorySlotContents(0, new ItemStack(TestItems.COBBLESTONE, 3));
        var enough = capture(config);
        assertEquals("ready_to_build", enough.getString("status"));
        assertTrue(enough.getBoolean("canBuild"));
        var material = enough.getList("materials", 10)
            .getCompound(0);
        assertEquals(3, material.getInt("available"));
        assertEquals(2, material.getInt("required"));
        assertTrue(material.getBoolean("enough"));
        assertTrue(
            ItemStacks.isSameItemSameComponents(
                new ItemStack(TestItems.COBBLESTONE),
                ItemStacks.parseOptional(
                    cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT,
                    material.getCompound("stack"))));
        assertEquals(3, player.inventory.getStackInSlot(0).stackSize);
    }

    @Test
    void creativeBypassesMaterialsButNeverConflicts() {
        var player = player();
        player.capabilities.isCreativeMode = true;
        assertTrue(capture(config(player, plan(Collections.emptyList()), false, false)).getBoolean("canBuild"));
        var blocked = capture(config(player, plan(Collections.singletonList(BlockPos.ZERO)), false, false));
        assertEquals("conflicts_detected", blocked.getString("status"));
        assertFalse(blocked.getBoolean("canBuild"));
        assertTrue(blocked.getBoolean("editable"));
        assertEquals("0, 0, 0", blocked.getString("conflictPositions"));
    }

    @Test
    void formedAndRunningStatesTakePrecedenceOverPreviewReadiness() {
        var player = player();
        var formed = capture(config(player, null, true, true));
        assertEquals("controller_formed", formed.getString("status"));
        assertFalse(formed.getBoolean("canBuild"));
        assertFalse(formed.getBoolean("editable"));
        var running = capture(config(player, plan(Collections.emptyList()), false, true));
        assertEquals("build_in_progress", running.getString("status"));
        assertTrue(running.getBoolean("building"));
        assertFalse(running.getBoolean("canBuild"));
        assertFalse(running.getBoolean("editable"));
        assertEquals("no_definition", capture(config(player, null, false, false)).getString("status"));
        assertFalse(capture(config(player, null, false, false)).getBoolean("editable"));
    }

    @Test
    void conflictPreviewIsBoundedWhileTotalCountRemainsExact() {
        var positions = new java.util.ArrayList<BlockPos>();
        for (int index = 0; index < 11; index++) positions.add(new BlockPos(index, 64, -index));
        var state = capture(config(player(), plan(positions), false, false));
        assertEquals(11, state.getInt("conflicts"));
        assertEquals(3, state.getInt("otherConflicts"));
        assertEquals(
            8,
            state.getString("conflictPositions")
                .split("\n").length);
    }

    private static cn.dancingsnow.neoecoae.platform.nbt.CompoundTag capture(NativeBuilderConfig config) {
        return NativeBuilderSession.capture(config, cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT);
    }

    private static EntityPlayerMP player() {
        var player = mock(EntityPlayerMP.class);
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        return player;
    }

    private static MultiBlockPlacementPlan plan(java.util.List<BlockPos> conflicts) {
        var block = new WorldPlannedBlock(
            BlockPos.ZERO,
            BlockState.defaultState(TestBlocks.STONE),
            new ItemStack(TestItems.COBBLESTONE));
        return new MultiBlockPlacementPlan(
            Collections.singletonList(block),
            Collections.singletonList(block),
            conflicts,
            Collections.singletonList(new RequiredItem(new ItemStack(TestItems.COBBLESTONE), 2)),
            4);
    }

    private static NativeBuilderConfig config(EntityPlayerMP player, MultiBlockPlacementPlan plan, boolean formed,
        boolean building) {
        return new NativeBuilderConfig(
            mock(TileEntity.class),
            player,
            () -> 5,
            1,
            64,
            value -> {},
            delta -> {},
            () -> true,
            value -> {},
            () -> {},
            () -> formed,
            () -> building,
            () -> plan);
    }
}
