package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.recipe.StorageRecipes;

class NativeStorageCellDisassemblyTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void survivalAndCreativeDisassemblyConsumeTheDiskAndReturnEveryPart(boolean creative) {
        var item = new NativeStorageCellItem(
            StorageTier.L6,
            AEKeyType.items(),
            new ECOCellType(Component.literal("test"), 315));
        var disk = new ItemStack(item);
        var player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = mock(Container.class);
        player.capabilities = new PlayerCapabilities();
        player.capabilities.isCreativeMode = creative;
        player.inventory.currentItem = 4;
        player.inventory.setInventorySlotContents(4, disk);
        when(player.isSneaking()).thenReturn(true);
        when(player.getCurrentEquippedItem()).thenAnswer(ignored -> player.inventory.getCurrentItem());
        var world = mock(World.class);

        try (var recipes = mockStatic(StorageRecipes.class)) {
            recipes.when(() -> StorageRecipes.disassemble(item))
                .thenAnswer(
                    ignored -> new ArrayList<>(
                        Arrays.asList(new ItemStack(TestItems.IRON_INGOT, 3), new ItemStack(TestItems.DIAMOND, 1))));
            assertSame(disk, item.onItemRightClick(disk, world, player));
            assertNull(player.inventory.getCurrentItem());
            // Vanilla can restore the returned stack's creative count without restoring its inventory slot.
            assertEquals(1, disk.stackSize);
            long iron = 0, diamonds = 0, disks = 0;
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack == null) continue;
                if (stack.getItem() == TestItems.IRON_INGOT) iron += stack.stackSize;
                if (stack.getItem() == TestItems.DIAMOND) diamonds += stack.stackSize;
                if (stack.getItem() == item) disks += stack.stackSize;
            }
            assertEquals(3, iron);
            assertEquals(1, diamonds);
            assertEquals(0, disks);
            verify(player.inventoryContainer).detectAndSendChanges();
            verify(player, never()).dropPlayerItemWithRandomChoice(any(), anyBoolean());
            // A stale second click cannot disassemble the consumed disk again.
            assertSame(disk, item.onItemRightClick(disk, world, player));
            recipes.verify(() -> StorageRecipes.disassemble(item), times(1));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void clientAndNonSneakingClicksDoNotConsumeOrGrantAnything(boolean client) {
        var item = new NativeStorageCellItem(
            StorageTier.L6,
            AEKeyType.items(),
            new ECOCellType(Component.literal("test"), 315));
        var disk = new ItemStack(item);
        var player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = mock(Container.class);
        player.inventory.setInventorySlotContents(0, disk);
        when(player.isSneaking()).thenReturn(client);
        when(player.getCurrentEquippedItem()).thenReturn(disk);
        var world = mock(World.class);
        world.isRemote = client;
        try (var recipes = mockStatic(StorageRecipes.class)) {
            recipes.when(() -> StorageRecipes.disassemble(item))
                .thenAnswer(ignored -> new ArrayList<>(Arrays.asList(new ItemStack(TestItems.DIAMOND))));
            assertSame(disk, item.onItemRightClick(disk, world, player));
            assertSame(disk, player.inventory.getCurrentItem());
            assertEquals(1, disk.stackSize);
            verifyNoInteractions(player.inventoryContainer);
            verify(player, never()).dropPlayerItemWithRandomChoice(any(), anyBoolean());
        }
    }
}
