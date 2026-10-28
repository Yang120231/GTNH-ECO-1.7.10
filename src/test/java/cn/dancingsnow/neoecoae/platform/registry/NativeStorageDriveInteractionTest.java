package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

class NativeStorageDriveInteractionTest {

    private final IECOCellHandler handler = new IECOCellHandler() {

        @Override
        public boolean isCell(ItemStack stack) {
            return stack.getItem() == TestItems.DIAMOND;
        }

        @Override
        public IECOStorageCell getCellInventory(ItemStack is, ISaveProvider host) {
            return null;
        }
    };
    private NativeDriveBlock block;
    private ECODriveBlockEntity drive;
    private World world;
    private EntityPlayer player;

    @BeforeEach
    void setup() {
        ECOStorageCells.register(handler);
        var type = new BlockEntityType<ECODriveBlockEntity>("test_storage_drive", () -> null);
        block = new NativeDriveBlock(type, "eco_drive");
        drive = new ECODriveBlockEntity(type, BlockPos.ZERO, block.defaultBlockState());
        world = mock(World.class);
        when(world.getTileEntity(0, 0, 0)).thenReturn(drive);
        player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        when(player.getHeldItem()).thenAnswer(ignored -> player.inventory.getCurrentItem());
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void unregisterHandler() throws ReflectiveOperationException {
        Field handlers = ECOStorageCells.class.getDeclaredField("handlers");
        handlers.setAccessible(true);
        ((List<IECOCellHandler>) handlers.get(null)).remove(handler);
    }

    private boolean click() {
        return block.onBlockActivated(world, 0, 0, 0, player, 2, 0.5F, 0.5F, 0.5F);
    }

    @Test
    void rightClickInsertsOneCellCopy() {
        ItemStack held = new ItemStack(TestItems.DIAMOND, 3);
        held.setTagCompound(new NBTTagCompound());
        held.getTagCompound()
            .setString("identity", "original");
        player.inventory.setInventorySlotContents(0, held);

        assertTrue(click());
        assertEquals(2, held.stackSize);
        assertEquals(1, drive.getCellStack().stackSize);
        assertNotSame(held, drive.getCellStack());
        held.getTagCompound()
            .setString("identity", "changed");
        assertEquals(
            "original",
            drive.getCellStack()
                .getTagCompound()
                .getString("identity"));
    }

    @Test
    void insertingTheLastCellClearsTheHand() {
        player.inventory.setInventorySlotContents(0, new ItemStack(TestItems.DIAMOND));
        assertTrue(click());
        assertNull(player.getHeldItem());
        assertNotNull(drive.getCellStack());
    }

    @Test
    void creativeInsertionDoesNotConsumeTheHeldCell() {
        player.capabilities.isCreativeMode = true;
        ItemStack held = new ItemStack(TestItems.DIAMOND);
        player.inventory.setInventorySlotContents(0, held);
        assertTrue(click());
        assertSame(held, player.getHeldItem());
        assertEquals(1, held.stackSize);
        assertNotSame(held, drive.getCellStack());
    }

    @Test
    void unrelatedItemsOccupiedDrivesAndSneakingFallThroughWithoutChanges() {
        player.inventory.setInventorySlotContents(0, new ItemStack(TestItems.STONE));
        assertFalse(click());
        assertNull(drive.getCellStack());

        ItemStack held = new ItemStack(TestItems.DIAMOND, 3);
        player.inventory.setInventorySlotContents(0, held);
        when(player.isSneaking()).thenReturn(true);
        assertFalse(click());
        assertNull(drive.getCellStack());
        when(player.isSneaking()).thenReturn(false);

        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        ItemStack installed = drive.getCellStack();
        assertFalse(click());
        assertSame(installed, drive.getCellStack());
        assertEquals(3, held.stackSize);
    }

    @Test
    void controllerRejectionKeepsTheHeldCell() {
        ECOStorageSystemBlockEntity controller = mock(ECOStorageSystemBlockEntity.class);
        when(controller.canInsertStorageCell(any())).thenReturn(false);
        NEStorageCluster cluster = mock(NEStorageCluster.class);
        when(cluster.getController()).thenReturn(controller);
        drive.updateCluster(cluster);
        ItemStack held = new ItemStack(TestItems.DIAMOND, 2);
        player.inventory.setInventorySlotContents(0, held);

        assertFalse(click());
        assertEquals(2, held.stackSize);
        assertNull(drive.getCellStack());
    }

    @Test
    void emptyHandShiftRightClickReturnsTheCell() {
        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        ItemStack installed = drive.getCellStack();
        when(player.isSneaking()).thenReturn(true);

        assertTrue(click());
        assertNull(drive.getCellStack());
        assertSame(installed, player.getHeldItem());
    }

    @Test
    void extractionRequiresSneakingAndAnEmptyHand() {
        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        assertFalse(click());
        assertNotNull(drive.getCellStack());
        when(player.isSneaking()).thenReturn(true);
        ItemStack held = new ItemStack(TestItems.STONE);
        player.inventory.setInventorySlotContents(0, held);
        assertFalse(click());
        assertSame(held, player.getHeldItem());
        assertNotNull(drive.getCellStack());
    }

    @Test
    void lockedCellsStayInstalledAndExplainWhy() {
        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        ItemStack installed = drive.getCellStack();
        ECOInfiniteStorageMember.beginMigration(installed, UUID.randomUUID());
        when(player.isSneaking()).thenReturn(true);

        assertTrue(click());
        assertSame(installed, drive.getCellStack());
        assertNull(player.getHeldItem());
        ArgumentCaptor<IChatComponent> message = ArgumentCaptor.forClass(IChatComponent.class);
        verify(player).addChatComponentMessage(message.capture());
        assertEquals(
            ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MIGRATION.translationKey(),
            ((ChatComponentTranslation) message.getValue()).getKey());
    }

    @Test
    void clientAcknowledgesInsertionAndExtractionWithoutChangingInventories() {
        world.isRemote = true;
        ItemStack held = new ItemStack(TestItems.DIAMOND, 3);
        player.inventory.setInventorySlotContents(0, held);
        assertTrue(click());
        assertEquals(3, held.stackSize);
        assertNull(drive.getCellStack());

        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        ItemStack installed = drive.getCellStack();
        player.inventory.setInventorySlotContents(0, null);
        when(player.isSneaking()).thenReturn(true);
        assertTrue(click());
        assertSame(installed, drive.getCellStack());
        assertNull(player.getHeldItem());
    }
}
