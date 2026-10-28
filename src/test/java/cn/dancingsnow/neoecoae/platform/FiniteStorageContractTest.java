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
    void deferringHostsWriteTheCellOncePerTickAndOnDemand() {
        AtomicInteger saved = new AtomicInteger();
        ItemStack stack = item(AEKeyType.items());
        cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider host = new cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider() {

            @Override
            public void saveChanges() {
                saved.incrementAndGet();
            }

            @Override
            public boolean defersCellPersistence() {
                return true;
            }
        };
        ECOStorageCell cell = new ECOStorageCell(stack, host);
        cn.dancingsnow.neoecoae.platform.network.ServerThread.capture();
        try {
            cell.insert(new TestResourceKey("iron", false), 32, Actionable.MODULATE, IActionSource.empty());
            cell.insert(new TestResourceKey("copper", false), 64, Actionable.MODULATE, IActionSource.empty());
            assertEquals(0, saved.get());
            assertEquals(0, new ECOStorageCell(stack, null).getStoredItemCount());
            // A host reading or moving its stack flushes just that cell.
            ECOCellMutationBatch.flushDeferred(cell);
            assertEquals(1, saved.get());
            assertEquals(96, new ECOStorageCell(stack, null).getStoredItemCount());
            cell.extract(new TestResourceKey("iron", false), 2, Actionable.MODULATE, IActionSource.empty());
            ECOCellMutationBatch.flushTickDeferred();
            ECOCellMutationBatch.flushTickDeferred();
            assertEquals(2, saved.get());
            assertEquals(94, new ECOStorageCell(stack, null).getStoredItemCount());
        } finally {
            cn.dancingsnow.neoecoae.platform.network.ServerThread.clear();
        }
    }

    @Test
    void deferringHostsPersistImmediatelyOffTheServerThread() {
        AtomicInteger saved = new AtomicInteger();
        ItemStack stack = item(AEKeyType.items());
        ECOStorageCell cell = new ECOStorageCell(stack, new cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider() {

            @Override
            public void saveChanges() {
                saved.incrementAndGet();
            }

            @Override
            public boolean defersCellPersistence() {
                return true;
            }
        });
        cell.insert(new TestResourceKey("iron", false), 32, Actionable.MODULATE, IActionSource.empty());
        assertEquals(1, saved.get());
        assertEquals(32, new ECOStorageCell(stack, null).getStoredItemCount());
    }

    @Test
    void contentsRewriteKeepsOtherComponentsAndDropsTheOldList() {
        ItemStack stack = item(AEKeyType.items());
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        root.setString("neoecoae_marker", "kept");
        stack.setTagCompound(root);
        ECOStorageCell cell = new ECOStorageCell(stack, null);
        cell.insert(new TestResourceKey("iron", false), 5, Actionable.MODULATE, IActionSource.empty());
        cell.extract(new TestResourceKey("iron", false), 5, Actionable.MODULATE, IActionSource.empty());
        assertEquals("kept", stack.getTagCompound().getString("neoecoae_marker"));
        assertFalse(stack.getTagCompound().hasKey("neoecoae_inventory"));
        cell.insert(new TestResourceKey("gold", false), 7, Actionable.MODULATE, IActionSource.empty());
        assertEquals("kept", stack.getTagCompound().getString("neoecoae_marker"));
        assertEquals(7, new ECOStorageCell(stack, null).getStoredItemCount());
    }

    @Test
    void pointQueryAndVisitMatchTheListingAndHonourSealing() {
        ItemStack stack = item(AEKeyType.items());
        ECOStorageCell cell = new ECOStorageCell(stack, null);
        AEKey iron = new TestResourceKey("iron", false), copper = new TestResourceKey("copper", false);
        cell.insert(iron, 32, Actionable.MODULATE, IActionSource.empty());
        cell.insert(copper, 64, Actionable.MODULATE, IActionSource.empty());
        KeyCounter listed = cell.getAvailableStacks();
        java.util.Map<AEKey, Long> visited = new java.util.HashMap<>();
        cell.visitAvailableStacks(visited::put);
        assertEquals(2, visited.size());
        for (var entry : listed) {
            assertEquals(entry.getLongValue(), visited.get(entry.getKey()));
            assertEquals(entry.getLongValue(), cell.getAvailableAmount(entry.getKey()));
        }
        assertEquals(0, cell.getAvailableAmount(new TestResourceKey("tin", false)));
        cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.markMember(stack, java.util.UUID.randomUUID());
        assertTrue(cell.getAvailableStacks().isEmpty());
        assertEquals(0, cell.getAvailableAmount(iron));
        cell.visitAvailableStacks((key, amount) -> fail("sealed cell visited " + key));
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
