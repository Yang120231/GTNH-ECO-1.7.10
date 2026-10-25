package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTier;

class FiniteStorageContractTest {

    @Test
    void addonTierDescriptorsRemainUsableByTheImportedMigrationContract() {
        cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor addon = new cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor() {

            @Override
            public int getTier() {
                return 4;
            }

            @Override
            public long getStorageTotalBytes() {
                return 1L << 29;
            }

            @Override
            public long getPowerStorageSize() {
                return 2_000_000_000L;
            }

            @Override
            public long getCPUTotalBytes() {
                return 1L << 32;
            }

            @Override
            public boolean supportsFeature(cn.dancingsnow.neoecoae.api.capability.ECOFeature feature) {
                return feature == cn.dancingsnow.neoecoae.api.capability.ECOFeature.INFINITE_STORAGE_CELL;
            }
        };
        ItemStack stack = new ItemStack(
            new NativeStorageCellItem(
                addon,
                AEKeyType.items(),
                new ECOCellType(Component.literal("addon"), 315),
                false));
        ECOStorageCell cell = new ECOStorageCell(stack, null);
        assertSame(addon, cell.getTier());
        assertTrue(cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer.isEligible(cell));
        assertEquals(
            2048,
            cell.insert(
                new TestResourceKey("addon-resource", false),
                2048,
                Actionable.MODULATE,
                IActionSource.empty()));
    }

    private static ItemStack item(AEKeyType type) {
        return new ItemStack(
            new NativeStorageCellItem(
                StorageTier.L4,
                type,
                new ECOCellType(Component.literal("test"), type == AEKeyType.items() ? 315 : 25),
                false));
    }

    @Test
    void simulationChangesNeitherContentsNbtNorSaveNotifications() {
        ItemStack stack = item(AEKeyType.items());
        AtomicInteger saved = new AtomicInteger();
        ECOStorageCell cell = new ECOStorageCell(stack, saved::incrementAndGet);
        NBTTagCompound before = stack.getTagCompound() == null ? null
            : (NBTTagCompound) stack.getTagCompound()
                .copy();
        assertEquals(
            128,
            cell.insert(new TestResourceKey("iron", false), 128, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(0, cell.getStoredItemCount());
        assertEquals(0, cell.contentRevision());
        assertEquals(before, stack.getTagCompound());
        assertEquals(0, saved.get());
    }

    @Test
    void capacityAccountingAndReloadConserveEveryAcceptedItem() {
        ItemStack stack = item(AEKeyType.items());
        ECOStorageCell live = new ECOStorageCell(stack, null);
        AEKey key = new TestResourceKey("iron", false);
        long inserted = live.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        assertEquals((StorageTier.L4.getStorageTotalBytes() - (1L << 13)) * 8, inserted);
        assertEquals(0, live.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
        ECOStorageCell loaded = new ECOStorageCell(stack, null);
        assertEquals(
            inserted,
            loaded.getAvailableStacks()
                .get(key));
        assertEquals(inserted, loaded.extract(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()));
        assertTrue(
            new ECOStorageCell(stack, null).getAvailableStacks()
                .isEmpty());
    }

    @Test
    void fluidUnitsAndNativeChannelIdentityStayDistinct() {
        ECOStorageCell cell = new ECOStorageCell(item(AEKeyType.fluids()), null);
        AEKey water = new TestResourceKey("water", true), item = new TestResourceKey("water", false);
        assertEquals(0, cell.insert(item, 1000, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(1000, cell.insert(water, 1000, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(250, cell.extract(water, 250, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(
            750,
            cell.getAvailableStacks()
                .get(water));
    }

    @Test
    void sealHidesBothChannelsButRetainsAuthoritativeMigrationContents() {
        ItemStack stack = item(AEKeyType.items());
        ECOStorageCell cell = new ECOStorageCell(stack, null);
        AEKey key = new TestResourceKey("iron", false);
        cell.insert(key, 64, Actionable.MODULATE, IActionSource.empty());
        ECOInfiniteStorageMember.beginMigration(stack, UUID.randomUUID());
        assertTrue(
            cell.getAvailableStacks()
                .isEmpty());
        assertEquals(64, cell.getMigrationAmount(key));
        assertEquals(0, cell.extract(key, 1, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(0, cell.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
        assertFalse(cell.canFitInsideCell());
    }

    @Test
    void batchSerializationNotifiesTheNativeHostOnce() {
        AtomicInteger saved = new AtomicInteger();
        ItemStack stack = item(AEKeyType.items());
        ECOStorageCell cell = new ECOStorageCell(stack, saved::incrementAndGet);
        try (ECOCellMutationBatch ignored = ECOCellMutationBatch.open()) {
            cell.insert(new TestResourceKey("iron", false), 32, Actionable.MODULATE, IActionSource.empty());
            cell.insert(new TestResourceKey("copper", false), 64, Actionable.MODULATE, IActionSource.empty());
            assertEquals(0, saved.get());
        }
        assertEquals(1, saved.get());
        assertEquals(96, new ECOStorageCell(stack, null).getStoredItemCount());
    }

    @Test
    void invalidInventoryNbtFailsWithoutReplacingTheOriginalTag() {
        ItemStack stack = item(AEKeyType.items());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("neoecoae_inventory", "damaged");
        stack.setTagCompound(tag);
        NBTTagCompound original = (NBTTagCompound) tag.copy();
        assertThrows(IllegalStateException.class, () -> new ECOStorageCell(stack, null));
        assertEquals(original, stack.getTagCompound());
    }

    @Test
    void migrationPreflightUsesACallerOwnedSnapshot() {
        ItemStack stack = item(AEKeyType.items());
        ECOStorageCell cell = new ECOStorageCell(stack, null);
        AEKey key = new TestResourceKey("iron", false);
        KeyCounter projected = new KeyCounter();
        projected.set(key, 37);
        assertTrue(cell.simulateInsertForMigration(key, 1000, projected) > 0);
        assertTrue(
            cell.getAvailableStacks()
                .isEmpty());
        assertEquals(37, projected.get(key));
        assertEquals(0, cell.contentRevision());
    }
}
