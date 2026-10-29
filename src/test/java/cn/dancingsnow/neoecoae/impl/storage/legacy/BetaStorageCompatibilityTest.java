package cn.dancingsnow.neoecoae.impl.storage.legacy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTier;

/** Field names and encodings are taken from the writers in tag v7.3.0-beta1. */
class BetaStorageCompatibilityTest {

    private static final Item RESOURCE = new Item();

    @BeforeAll
    static void registerResources() throws Exception {
        // Populate only the identity table; unit tests do not start FML's LaunchClassLoader.
        var add = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        add.setAccessible(true);
        add.invoke(Item.itemRegistry, 31000, "neoecoae_beta_test:resource", RESOURCE);
    }

    private static CompoundTag snapshot(String channel, String identity, BigInteger amount, String variant) {
        CompoundTag key = new CompoundTag();
        key.putString("channel", channel);
        key.putString("identity", identity);
        key.putInt("metadata", 7);
        key.putString("variant", variant);
        CompoundTag entry = new CompoundTag(), count = new CompoundTag();
        entry.put("key", key);
        if (amount.bitLength() > 63) count.putString("big", amount.toString());
        else count.putLong("long", amount.longValueExact());
        entry.put("amount", count);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag storage = new CompoundTag();
        storage.putInt("version", 3);
        storage.putLong("revision", 91);
        storage.put("entries", entries);
        return storage;
    }

    private static ItemStack cell(CompoundTag storage, AEKeyType type) {
        ItemStack stack = new ItemStack(
            new NativeStorageCellItem(
                StorageTier.L9,
                type,
                new ECOCellType(Component.literal("beta-test"), type == AEKeyType.items() ? 315 : 25)));
        CompoundTag root = new CompoundTag();
        root.put("ECOStorage", storage);
        root.putString("ECOTier", "256M");
        stack.setTagCompound(root.nativeTag());
        return stack;
    }

    @Test
    void itemDamageVariantAndAmountsSurviveExtractionReloadAndEmptying() throws Exception {
        NBTTagCompound variant = new NBTTagCompound();
        variant.setString("owner", "升级兼容");
        variant.setInteger("charge", 1234567);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(variant, bytes);
        ItemStack stack = cell(
            snapshot(
                "item",
                "neoecoae_beta_test:resource",
                BigInteger.valueOf(123456),
                Base64.getEncoder()
                    .encodeToString(bytes.toByteArray())),
            AEKeyType.items());
        NBTTagCompound before = (NBTTagCompound) stack.getTagCompound()
            .copy();
        var entries = CellComponents.readStacks(stack);
        AEItemKey key = (AEItemKey) entries.get(0)
            .what();
        assertEquals(
            7,
            key.toStack()
                .getItemDamage());
        assertEquals(
            variant,
            key.toStack()
                .getTagCompound());
        assertEquals(before, stack.getTagCompound());
        ECOStorageCell inventory = new ECOStorageCell(stack, null);
        assertEquals(123456, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(before, stack.getTagCompound());
        assertEquals(123456, inventory.extract(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()));
        inventory.persist();
        assertTrue(
            new ECOStorageCell(stack, null).getAvailableStacks()
                .isEmpty());
        assertEquals(
            before.getCompoundTag("ECOStorage"),
            stack.getTagCompound()
                .getCompoundTag("ECOStorage"));
        try (var cells = org.mockito.Mockito
            .mockStatic(cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells.class)) {
            assertEquals(19, inventory.insert(key, 19, Actionable.MODULATE, IActionSource.empty()));
        }
        inventory.persist();
        assertEquals(
            19,
            new ECOStorageCell(stack, null).getAvailableStacks()
                .get(key));
    }

    @Test
    void fluidUsesMillibucketsAndKeepsItsVariant() throws Exception {
        NBTTagCompound variant = new NBTTagCompound();
        variant.setString("grade", "pure");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(variant, bytes);
        var converted = BetaStorageReader.entries(
            snapshot(
                "fluid",
                "water",
                BigInteger.valueOf(987654321L),
                Base64.getEncoder()
                    .encodeToString(bytes.toByteArray())),
            true);
        CompoundTag entry = (CompoundTag) converted.get(0);
        assertEquals(987654321L, entry.getLong("amount"));
        var context = cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT;
        AEFluidKey key = (AEFluidKey) AEKey.fromTagGeneric(context, entry.getCompound("key"));
        assertEquals(
            variant,
            key.toTagGeneric(context)
                .getCompound("Tag")
                .nativeTag());
    }

    @Test
    void memberBindingsAreReadOnlyAndClearingThemDoesNotReviveTheOldSeal() {
        ItemStack stack = cell(snapshot("item", "neoecoae_beta_test:resource", BigInteger.TEN, ""), AEKeyType.items());
        UUID domain = UUID.randomUUID(), disk = UUID.randomUUID();
        NBTTagCompound root = stack.getTagCompound();
        root.setString("ECOStorageMode", "domain_member");
        root.setString("ECOHostDomainId", domain.toString());
        root.setString("ECODiskId", disk.toString());
        NBTTagCompound before = (NBTTagCompound) root.copy();
        assertTrue(ECOInfiniteStorageMember.isMemberOf(stack, domain));
        assertEquals(disk, ECOInfiniteStorageMember.identity(stack));
        assertTrue(
            CellComponents.readStacks(stack)
                .isEmpty());
        root = stack.getTagCompound();
        assertEquals(before, root);
        CellComponents.clearStacks(stack);
        ECOInfiniteStorageMember.clearMember(stack);
        assertFalse(ECOInfiniteStorageMember.isSealed(stack));
        assertTrue(
            CellComponents.readStacks(stack)
                .isEmpty());
    }

    private static CompoundTag domain(CompoundTag storage, UUID disk) {
        CompoundTag domain = new CompoundTag();
        domain.put("storage", storage);
        ListTag sources = new ListTag();
        CompoundTag source = new CompoundTag();
        source.putString("diskId", disk.toString());
        sources.add(source);
        domain.put("committedSources", sources);
        return domain;
    }

    private static void writeDomains(Path file, CompoundTag... entries) throws Exception {
        ListTag domains = new ListTag();
        for (CompoundTag entry : entries) domains.add(entry);
        CompoundTag root = new CompoundTag(), data = new CompoundTag();
        data.putInt("dataVersion", 2);
        data.put("domains", domains);
        root.put("data", data);
        NbtIo.writeCompressed(root, file);
    }

    @Test
    void cachedDomainIndexReturnsCopiesAndFollowsFileChanges(@TempDir Path dir) throws Exception {
        BetaStorageReader.clearCache();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        CompoundTag a = domain(snapshot("item", "neoecoae_beta_test:resource", BigInteger.TEN, ""), UUID.randomUUID());
        a.putString("id", first.toString());
        Path file = dir.resolve(BetaStorageReader.DOMAIN_FILE);
        writeDomains(file, a);

        CompoundTag read = BetaStorageReader.domain(file, first);
        assertNotNull(read);
        read.putString("mutated", "caller edit");
        assertFalse(
            BetaStorageReader.domain(file, first)
                .contains("mutated"),
            "Callers must not edit the cached index");

        CompoundTag b = domain(snapshot("item", "neoecoae_beta_test:resource", BigInteger.ONE, ""), UUID.randomUUID());
        b.putString("id", second.toString());
        writeDomains(file, b);
        Files.setLastModifiedTime(
            file,
            java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(file)
                    .toMillis() + 5_000));
        assertNull(BetaStorageReader.domain(file, first));
        assertNotNull(BetaStorageReader.domain(file, second));

        Files.write(file, new byte[] { 1, 2, 3 });
        assertThrows(java.io.IOException.class, () -> BetaStorageReader.domain(file, second));
        assertThrows(java.io.IOException.class, () -> BetaStorageReader.domain(file, second));
        BetaStorageReader.clearCache();
    }

    @Test
    void exactInfiniteAmountsAndCommittedDiskReceiptsSurviveSaveAndReload(@TempDir Path dir) throws Exception {
        UUID disk = UUID.randomUUID(), id = UUID.randomUUID();
        BigInteger exact = BigInteger.ONE.shiftLeft(100)
            .add(BigInteger.valueOf(123));
        CompoundTag old = domain(snapshot("item", "neoecoae_beta_test:resource", exact, ""), disk);
        old.putString("id", id.toString());
        ListTag domains = new ListTag();
        domains.add(old);
        CompoundTag root = new CompoundTag(), data = new CompoundTag();
        data.putInt("dataVersion", 2);
        data.put("domains", domains);
        root.put("data", data);
        Path file = dir.resolve(BetaStorageReader.DOMAIN_FILE);
        NbtIo.writeCompressed(root, file);
        byte[] original = Files.readAllBytes(file);
        ECOInfiniteStorageData loaded = ECOInfiniteStorageData
            .load(BetaStorageReader.convertDomain(BetaStorageReader.domain(file, id)), RegistryAccess.EMPTY);
        var engine = new SavedDataInfiniteStorageEngine(loaded);
        AEItemKey key = AEItemKey.of(new ItemStack(RESOURCE, 1, 7));
        assertEquals(
            exact,
            engine.getAmount(key)
                .toBigInteger());
        assertTrue(engine.hasMigrationReceipt(disk));
        assertArrayEquals(original, Files.readAllBytes(file));
        var reloaded = new SavedDataInfiniteStorageEngine(
            ECOInfiniteStorageData.load(loaded.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY));
        assertEquals(
            exact,
            reloaded.getAmount(key)
                .toBigInteger());
        assertTrue(reloaded.hasMigrationReceipt(disk));
    }

    @Test
    void unavailableOverflowAndMalformedVariantsCannotBeTurnedIntoEmptyCells() {
        var overflow = snapshot("item", "neoecoae_beta_test:resource", BigInteger.ONE.shiftLeft(80), "");
        // Overflow is reported as an unreadable snapshot, the same failure type callers preserve.
        assertThrows(IllegalStateException.class, () -> CellComponents.readStacks(cell(overflow, AEKeyType.items())));
        var storage = snapshot("item", "neoecoae_beta_test:resource", BigInteger.TEN, "not base64");
        ItemStack corrupt = cell(storage, AEKeyType.items());
        assertThrows(IllegalStateException.class, () -> CellComponents.readStacks(corrupt));
        storage.putBoolean("unavailable", true);
        assertThrows(IllegalStateException.class, () -> CellComponents.readStacks(corrupt));
        CompoundTag malformed = new CompoundTag();
        assertThrows(IllegalStateException.class, () -> BetaStorageReader.entries(malformed, true));
    }

    @Test
    void interruptedBetaMigrationDoesNotInsertAnAlreadyCommittedDiskAgain() {
        UUID disk = UUID.randomUUID(), id = UUID.randomUUID();
        CompoundTag snapshot = snapshot("item", "neoecoae_beta_test:resource", BigInteger.valueOf(81), "");
        ItemStack stack = cell(snapshot, AEKeyType.items());
        stack.getTagCompound()
            .setString("ECOStorageMode", "migrating");
        stack.getTagCompound()
            .setString("ECOHostDomainId", id.toString());
        stack.getTagCompound()
            .setString("ECODiskId", disk.toString());
        var engine = new SavedDataInfiniteStorageEngine(
            ECOInfiniteStorageData.load(BetaStorageReader.convertDomain(domain(snapshot, disk)), RegistryAccess.EMPTY));
        AEItemKey key = AEItemKey.of(new ItemStack(RESOURCE, 1, 7));
        ECOStorageCell inventory = new ECOStorageCell(stack, null);
        assertTrue(
            new ECOInfiniteStorageTransfer().step(
                stack,
                inventory,
                id,
                engine,
                RegistryAccess.EMPTY,
                () -> fail("Committed disk must not be imported again"),
                () -> {
                    inventory.clearMigrationStacks();
                    ECOInfiniteStorageMember.markMember(stack, id);
                },
                (ignored, amount) -> UUID.randomUUID()));
        assertEquals(
            BigInteger.valueOf(81),
            engine.getAmount(key)
                .toBigInteger());
        assertTrue(ECOInfiniteStorageMember.isMemberOf(stack, id));
    }

    @Test
    void uncommittedPortableDiskKeepsTheIdentityListedByTheOldController() {
        ItemStack stack = cell(
            snapshot("item", "neoecoae_beta_test:resource", BigInteger.valueOf(17), ""),
            AEKeyType.items());
        UUID disk = UUID.randomUUID();
        stack.getTagCompound()
            .setString("ECOStorageMode", "portable");
        stack.getTagCompound()
            .setString("ECODiskId", disk.toString());
        assertFalse(ECOInfiniteStorageMember.isSealed(stack));
        assertEquals(disk, ECOInfiniteStorageMember.identity(stack));
        UUID domain = UUID.randomUUID();
        ECOInfiniteStorageMember.beginMigration(stack, domain);
        ECOInfiniteStorageMember.markMember(stack, domain);
        assertEquals(disk, ECOInfiniteStorageMember.identity(stack));
    }

    @Test
    void unfinishedRestoreKeepsTheDomainAsAuthorityAndHidesSealedTargetSnapshots() {
        UUID disk = UUID.randomUUID(), id = UUID.randomUUID();
        CompoundTag storage = snapshot("item", "neoecoae_beta_test:resource", BigInteger.valueOf(999), "");
        CompoundTag old = domain(storage, disk), plan = new CompoundTag();
        plan.put(disk.toString(), storage.copy());
        plan.putBoolean("Completed", false);
        old.put("restorePlan", plan);
        var engine = new SavedDataInfiniteStorageEngine(
            ECOInfiniteStorageData.load(BetaStorageReader.convertDomain(old), RegistryAccess.EMPTY));
        assertEquals(
            BigInteger.valueOf(999),
            engine.getAmount(AEItemKey.of(new ItemStack(RESOURCE, 1, 7)))
                .toBigInteger());
        ItemStack target = cell(storage.copy(), AEKeyType.items());
        target.getTagCompound()
            .setString("ECOStorageMode", "domain_member");
        target.getTagCompound()
            .setString("ECODiskId", disk.toString());
        target.getTagCompound()
            .setString("ECOHostDomainId", id.toString());
        assertTrue(
            CellComponents.readStacks(target)
                .isEmpty());
        assertTrue(ECOInfiniteStorageMember.isSealed(target));
        assertEquals(
            storage,
            old.getCompound("restorePlan")
                .getCompound(disk.toString()));
    }
}
