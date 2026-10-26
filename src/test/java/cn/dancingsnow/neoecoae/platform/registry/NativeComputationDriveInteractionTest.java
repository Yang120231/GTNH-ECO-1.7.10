package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.api.IGuiHolder;

import cn.dancingsnow.neoecoae.api.computation.ECOComputationCells;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

class NativeComputationDriveInteractionTest {

    private static final ResourceLocation ADAPTER = ResourceLocation.parse("test:drive_interaction");
    private NativeExecutionMachineBlock block;
    private ECOComputationDriveBlockEntity drive;
    private World world;
    private EntityPlayer player;

    @BeforeEach
    void setup() {
        ECOComputationCells.register(
            ADAPTER,
            0,
            stack -> stack.getItem() == TestItems.DIAMOND ? new ECOComputationCells.Memory(StorageTiers.L4, 1024)
                : null);
        var type = new BlockEntityType<ECOComputationDriveBlockEntity>("test_drive", () -> null);
        block = new NativeExecutionMachineBlock(type, Role.COMPUTATION_DRIVE, "computation_drive");
        drive = new ECOComputationDriveBlockEntity(type, BlockPos.ZERO, block.defaultBlockState());
        world = mock(World.class);
        when(world.getTileEntity(0, 0, 0)).thenReturn(drive);
        player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        when(player.getHeldItem()).thenAnswer(ignored -> player.inventory.getCurrentItem());
    }

    @AfterEach
    void unregisterAdapter() {
        ECOComputationCells.unregister(ADAPTER);
    }

    private boolean click() {
        return block.onBlockActivated(world, 0, 0, 0, player, 2, 0.5F, 0.5F, 0.5F);
    }

    @Test
    void rightClickInsertsOneCellAndCopiesItsData() {
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
    void occupiedDrivesAndUnrelatedItemsDoNotOpenAMenuOrReplaceCells() {
        player.inventory.setInventorySlotContents(0, new ItemStack(TestItems.STONE));
        assertFalse(click());
        assertNull(drive.getCellStack());
        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        ItemStack installed = drive.getCellStack();
        ItemStack held = new ItemStack(TestItems.DIAMOND, 3);
        player.inventory.setInventorySlotContents(0, held);
        assertFalse(click());
        assertSame(installed, drive.getCellStack());
        assertEquals(3, held.stackSize);
        assertFalse(IGuiHolder.class.isAssignableFrom(CraftingMachines.ComputationDrive.class));
    }

    @Test
    void emptyHandShiftRightClickReturnsTheCellAndRecalculatesCapacity() {
        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        ItemStack installed = drive.getCellStack();
        NEComputationCluster cluster = mock(NEComputationCluster.class);
        when(cluster.getOwnUsedStorage()).thenReturn(1024L);
        drive.updateCluster(cluster);
        clearInvocations(cluster);
        when(player.isSneaking()).thenReturn(true);

        assertTrue(click());
        assertNull(drive.getCellStack());
        assertSame(installed, player.getHeldItem());
        verify(cluster).recalculateRemainingStorage();
    }

    @Test
    void extractionRequiresSneakingAndAnEmptyHand() {
        drive.setCellStack(new ItemStack(TestItems.DIAMOND));
        assertFalse(click());
        when(player.isSneaking()).thenReturn(true);
        ItemStack held = new ItemStack(TestItems.STONE);
        player.inventory.setInventorySlotContents(0, held);
        assertFalse(click());
        assertSame(held, player.getHeldItem());
        assertNotNull(drive.getCellStack());
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
