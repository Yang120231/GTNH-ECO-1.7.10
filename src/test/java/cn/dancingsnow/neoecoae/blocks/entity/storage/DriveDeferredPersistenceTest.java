package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.platform.TestResourceKey;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.network.ServerThread;
import cn.dancingsnow.neoecoae.platform.registry.NativeDriveBlock;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTier;

/** Per-tick merged cell writes in a drive never let a stale stack be read, moved, dropped or synced. */
class DriveDeferredPersistenceTest {

    private final IECOCellHandler handler = new IECOCellHandler() {

        @Override
        public boolean isCell(ItemStack stack) {
            return stack.getItem() instanceof NativeStorageCellItem;
        }

        @Override
        public IECOStorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
            return isCell(stack) ? new ECOStorageCell(stack, host) : null;
        }
    };
    private ECODriveBlockEntity drive;
    private ItemStack stack;
    private final AEKey iron = new TestResourceKey("iron", false), copper = new TestResourceKey("copper", false);

    @BeforeEach
    void setup() {
        ECOStorageCells.register(handler);
        var type = new BlockEntityType<ECODriveBlockEntity>("test_deferred_drive", () -> null);
        NativeDriveBlock block = new NativeDriveBlock(type, "eco_drive_deferred");
        drive = new ECODriveBlockEntity(type, BlockPos.ZERO, block.defaultBlockState());
        stack = new ItemStack(
            new NativeStorageCellItem(
                StorageTier.L4,
                AEKeyType.items(),
                new ECOCellType(Component.literal("test"), 315),
                false));
        drive.setCellStack(stack);
        ServerThread.capture();
    }

    @AfterEach
    void teardown() {
        try {
            ECOCellMutationBatch.flushTickDeferred();
        } finally {
            ServerThread.clear();
            ECOStorageCells.unregister(handler);
        }
    }

    private long persisted() {
        return new ECOStorageCell(stack, null).getStoredItemCount();
    }

    private void mutate() {
        IECOStorageCell cell = drive.getCellInventory();
        assertEquals(32, cell.insert(iron, 32, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(64, cell.insert(copper, 64, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(6, cell.extract(iron, 6, Actionable.MODULATE, IActionSource.empty()));
        // Merged: the stack NBT still holds the previous write until a flush point.
        assertEquals(0, persisted());
    }

    @Test
    void readingTheStackFlushesFirstAndConservesEveryAcceptedItem() {
        mutate();
        assertSame(stack, drive.getCellStack());
        assertEquals(90, persisted());
        ECOCellMutationBatch.flushTickDeferred();
        assertEquals(90, persisted());
    }

    @Test
    void tickEndFlushWritesOnce() {
        mutate();
        ECOCellMutationBatch.flushTickDeferred();
        assertEquals(90, persisted());
    }

    @Test
    void removingTheCellFlushesBeforeTheStackLeaves() {
        mutate();
        ItemStack removed = drive.HANDLER.extractItem(0, 1, false);
        assertNotNull(removed);
        assertEquals(90, new ECOStorageCell(removed, null).getStoredItemCount());
        assertNull(drive.getCellStack());
    }

    @Test
    void dropsDescriptionAndWorldSaveFlushFirst() {
        mutate();
        List<ItemStack> drops = new ArrayList<>();
        drive.addAdditionalDrops(null, BlockPos.ZERO, drops);
        assertEquals(90, new ECOStorageCell(drops.get(0), null).getStoredItemCount());

        IECOStorageCell cell = drive.getCellInventory();
        cell.insert(iron, 4, Actionable.MODULATE, IActionSource.empty());
        assertEquals(90, persisted());
        drive.getUpdateTag(RegistryAccess.EMPTY);
        assertEquals(94, persisted());

        cell.insert(iron, 1, Actionable.MODULATE, IActionSource.empty());
        drive.saveAdditional(new cn.dancingsnow.neoecoae.platform.nbt.CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(95, persisted());

        cell.insert(iron, 1, Actionable.MODULATE, IActionSource.empty());
        drive.onChunkUnloaded();
        assertEquals(96, persisted());
    }
}
