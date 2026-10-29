package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.IOUtilities;
import cn.dancingsnow.neoecoae.platform.nbt.NbtAccounter;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;

/** Use the native registry and production key codec for both paths from the reported crash. */
class InfiniteStorageRegistryCompatibilityTest {

    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    static java.util.stream.Stream<String> registryNames() {
        return java.util.stream.Stream.of(
            "eco_registry_test:Item With Spaces",
            "eco_registry_test:item(variant)",
            "eco_registry_test:item+variant",
            "eco_registry_test:item:variant",
            "ECORegistryTest:材料@Variant",
            "eco_registry_test:ordinary_item");
    }

    @BeforeAll
    static void registerItems() throws Exception {
        // Install only native registry identities without starting the FML launcher.
        var add = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        add.setAccessible(true);
        int id = 31100;
        for (String name : (Iterable<String>) registryNames()::iterator) {
            Item item = new Item();
            add.invoke(Item.itemRegistry, id++, name, item);
            ITEMS.put(name, item);
        }
    }

    private static ItemStack stack(String name) {
        ItemStack stack = new ItemStack(ITEMS.get(name), 23, 7);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("payload", "preserve variant data");
        stack.setTagCompound(nbt);
        return stack;
    }

    @ParameterizedTest
    @MethodSource("registryNames")
    void nativeItemKeysAndUiSnapshotsRoundTrip(String name) {
        ItemStack stack = stack(name);
        AEItemKey key = AEItemKey.of(stack);
        var tag = key.toTagGeneric(RegistryAccess.EMPTY);
        assertEquals(name, tag.getString("id"));
        assertEquals(key, AEKey.fromTagGeneric(RegistryAccess.EMPTY, tag));
        var uiTag = ItemStacks.save(RegistryAccess.EMPTY, stack);
        assertEquals(name, uiTag.getString("id"));
        assertTrue(ItemStack.areItemStacksEqual(stack, ItemStacks.parseOptional(RegistryAccess.EMPTY, uiTag)));
    }

    @ParameterizedTest
    @MethodSource("registryNames")
    void asynchronousDomainSaveRetainsExactAmountsAndIdentity(String name, @TempDir Path directory) throws Exception {
        AEItemKey key = AEItemKey.of(stack(name));
        var live = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(live);
        assertEquals(Long.MAX_VALUE, engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(17, engine.insert(key, 17, Actionable.MODULATE));
        Path file = directory.resolve("domain_v3.dat");
        live.save(file.toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();

        assertEquals(ECOInfiniteStorageData.DomainStatus.HEALTHY, live.status(), live.lastFailureReason());
        assertFalse(live.isDirty());
        var data = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
            .getCompound("data");
        var restored = ECOInfiniteStorageData.load(data, RegistryAccess.EMPTY);
        assertEquals(ECOInfiniteStorageData.DomainStatus.HEALTHY, restored.status());
        assertEquals(
            HugeAmount.of(
                BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.valueOf(17))),
            restored.getAmount(key));
        var available = new KeyCounter();
        new SavedDataInfiniteStorageEngine(restored).getAvailableStacks(available);
        assertEquals(1, available.size());
        var restoredKey = available.keySet()
            .iterator()
            .next();
        assertEquals(
            name,
            restoredKey.toTagGeneric(RegistryAccess.EMPTY)
                .getString("id"));
    }

    @ParameterizedTest
    @MethodSource("registryNames")
    void migrationTransactionIdsRemainStableAfterKeySerialization(String name) {
        var host = mock(ECOStorageSystemBlockEntity.class);
        var world = mock(WorldView.class);
        when(world.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        when(host.getLevel()).thenReturn(world);
        var controller = spy(new ECOInfiniteStorageModeController(host));
        doReturn(null).when(controller)
            .getEngine();
        var drive = mock(ECODriveBlockEntity.class);
        when(drive.getBlockPos()).thenReturn(new BlockPos(1, 64, 2));
        UUID domain = UUID.randomUUID();
        var key = AEItemKey.of(stack(name));
        for (String direction : new String[] { "from-domain", "to-domain" }) {
            UUID transaction = controller.migrationTransactionId(domain, drive, key, 42, direction);
            AEKey restored = AEKey.fromTagGeneric(RegistryAccess.EMPTY, key.toTagGeneric(RegistryAccess.EMPTY));
            assertEquals(transaction, controller.migrationTransactionId(domain, drive, restored, 42, direction));
            assertNotEquals(transaction, controller.migrationTransactionId(domain, drive, key, 43, direction));
        }
    }
}
