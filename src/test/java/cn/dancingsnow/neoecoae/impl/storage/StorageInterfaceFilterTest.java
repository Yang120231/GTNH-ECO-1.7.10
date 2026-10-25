package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class StorageInterfaceFilterTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void savedSamplesKeepExactItemComponentsFluidsAndAddonKeys() {
        {

            var registries = cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT;
            ItemStack named = new ItemStack(TestItems.STONE, 32);
            named.setStackDisplayName("Exact sample");
            var item = AEItemKey.of(named);
            var water = AEFluidKey.of(TestFluids.WATER);
            var lightning = cn.dancingsnow.neoecoae.platform.TestAddonKey.INSTANCE;
            CompoundTag empty = new CompoundTag();
            CompoundTag saved = StorageInterfaceFilter.with(empty, 0, item, registries);
            saved = StorageInterfaceFilter.with(saved, 13, water, registries);
            saved = StorageInterfaceFilter.with(saved, 26, lightning, registries);
            assertTrue(empty.isEmpty());
            var restored = new StorageInterfaceFilter(saved.copy(), registries);
            assertTrue(restored.test(item));
            assertTrue(restored.test(water));
            assertTrue(restored.test(lightning));
            assertFalse(restored.test(AEItemKey.of(TestItems.STONE)));
            assertFalse(restored.test(AEFluidKey.of(TestFluids.LAVA)));
            assertEquals(water, StorageInterfaceFilter.get(saved, 13, registries));
            assertEquals(lightning, StorageInterfaceFilter.get(saved, 26, registries));
        }
    }

    @Test
    void clearingTheLastSampleRestoresUnrestrictedTransferAndBoundsAreChecked() {
        {

            var registries = cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT;
            var stone = AEItemKey.of(TestItems.STONE);
            CompoundTag saved = StorageInterfaceFilter.with(new CompoundTag(), 26, stone, registries);
            assertFalse(new StorageInterfaceFilter(saved, registries).test(AEItemKey.of(TestItems.DIRT)));
            saved = StorageInterfaceFilter.with(saved, 26, null, registries);
            assertTrue(new StorageInterfaceFilter(saved, registries).test(AEItemKey.of(TestItems.DIRT)));
            assertThrows(
                IllegalArgumentException.class,
                () -> StorageInterfaceFilter.with(new CompoundTag(), -1, stone, registries));
            assertThrows(
                IllegalArgumentException.class,
                () -> StorageInterfaceFilter.with(new CompoundTag(), 27, stone, registries));
        }
    }

    @Test
    void unavailableSavedResourceRemainsRestrictive() {
        {

            var registries = cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT;
            CompoundTag saved = new CompoundTag();
            saved.put("0", new CompoundTag());
            assertFalse(new StorageInterfaceFilter(saved, registries).test(AEItemKey.of(TestItems.STONE)));
        }
    }
}
