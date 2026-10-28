package cn.dancingsnow.neoecoae.gui.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.CellEntry;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
import cn.dancingsnow.neoecoae.platform.inventory.ItemStackHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

class StoragePanelStateTest {

    @Test
    void installedCellTooltipUsesSourceNameBytesAndTypesWithoutTheOldFooter() {
        ItemStack disk = new ItemStack(TestItems.DIAMOND).setStackDisplayName("My storage matrix");
        CellEntry entry = new CellEntry(7, 3, CellEntry.KIND_ITEM, 4, 315, 131_114, 268_435_456, false)
            .atDrive(new BlockPos(1, 2, 3), disk, null);
        StoragePanelState state = StoragePanelState.capture(config(Collections.singletonList(entry), false), 0, 33);
        List<Component> tooltip = new StorageCellGrid(() -> state, 11).tooltip(0);
        assertEquals(3, tooltip.size(), "Do not prepend the item tooltip or repeat paging and interaction hints");
        assertEquals(Component.literal("My storage matrix"), tooltip.get(0));
        assertEquals(
            Component.translatable(
                "gui.neoecoae.storage.legacy.cell_bytes",
                HostText.usedPart("131,114", 131_114, 268_435_456),
                HostText.totalPart("268,435,456")),
            tooltip.get(1));
        assertEquals(
            Component.translatable(
                "gui.neoecoae.storage.legacy.cell_types",
                HostText.usedPart("4", 4, 315),
                HostText.totalPart("315")),
            tooltip.get(2));
    }

    @Test
    void cellTooltipKeepsExtractionLocksAndShowsUnboundedBytesAsInfinity() {
        ItemStack disk = new ItemStack(TestItems.DIAMOND).setStackDisplayName("Sealed matrix");
        Component lock = Component.translatable("tooltip.neoecoae.storage.infinite_member_locked");
        CellEntry entry = new CellEntry(7, 3, CellEntry.KIND_ITEM, 4, 315, 131_114, Long.MAX_VALUE, false)
            .atDrive(new BlockPos(1, 2, 3), disk, lock);
        StoragePanelState state = StoragePanelState.capture(config(Collections.singletonList(entry), false), 0, 33);
        List<Component> tooltip = new StorageCellGrid(() -> state, 11).tooltip(0);
        assertEquals(4, tooltip.size());
        assertEquals(
            Component.translatable(
                "gui.neoecoae.storage.legacy.cell_bytes",
                HostText.usedPart("131,114", 131_114, -1),
                HostText.totalPart("\u221e")),
            tooltip.get(1));
        assertEquals(
            lock.copy()
                .withColor(0xFF5555),
            tooltip.get(3));
    }

    @Test
    void absentCellTooltipMatchesTheSingleSourceStatusLine() {
        StoragePanelState state = StoragePanelState
            .capture(config(Collections.singletonList(CellEntry.EMPTY), false), 0, 33);
        assertEquals(
            Collections.singletonList(Component.translatable("gui.neoecoae.storage.ui.cell_state.absent")),
            new StorageCellGrid(() -> state, 11).tooltip(0));
    }

    @Test
    void addonTierNameSurvivesPagingAndTransportWithoutBeingInferredFromItsNumber() {
        var addon = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor.class);
        Component name = Component.translatable("prototype.tier.l1")
            .withColor(0x12AB34);
        org.mockito.Mockito.when(addon.getPanelTierName())
            .thenReturn(name);
        CellEntry entry = new CellEntry(0, 0, CellEntry.KIND_ITEM, 0, 10, 0, 100, false, CellState.EMPTY, true)
            .withPanelTier(addon)
            .atDrive(new BlockPos(1, 2, 3), new ItemStack(TestItems.DIAMOND), null);
        StoragePanelState received = decode(
            StoragePanelState
                .capture(config(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(entry), false), 0, 33)
                .toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT));
        assertTrue(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells())
                .installed());
        assertEquals(
            name,
            StorageCellGrid.tierName(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells())));
        assertEquals(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells())
                .tier());
        assertEquals(Component.literal("L4"), StorageCellGrid.tierName(cell(0, 100)));
    }

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void onlyTheRequestedPageIsSerializedAndRemovedCellsClearStalePages() {
        List<CellEntry> cells = new ArrayList<>();
        for (int i = 0; i < 72; i++) cells.add(cell(i, 100));
        StorageHostUI.Config config = config(cells, false);
        StoragePanelState first = StoragePanelState.capture(config, 0, 33);
        assertEquals(72, first.cellCount());
        assertEquals(
            33,
            first.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)
                .getList("cells", Tag.TAG_COMPOUND)
                .size());
        assertEquals(BigInteger.valueOf(7_200), first.totalBytes());
        assertEquals(
            0,
            first.cells()
                .get(0)
                .usedBytes());
        assertEquals(
            24,
            first.cells()
                .get(11)
                .usedBytes());
        assertEquals(
            48,
            first.cells()
                .get(22)
                .usedBytes());
        StoragePanelState last = StoragePanelState.capture(config, Integer.MAX_VALUE, 33);
        assertEquals(66, last.cellOffset());
        assertEquals(first.totalBytes(), last.totalBytes());
        assertEquals(
            first.totalBytes(),
            decode(last.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)).totalBytes());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(22L, 23L, 46L, 47L, 70L, 71L),
            last.cells()
                .stream()
                .filter(CellEntry::installed)
                .map(CellEntry::usedBytes)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        cells.subList(6, cells.size())
            .clear();
        StoragePanelState shortened = StoragePanelState.capture(config, last.cellOffset(), 33);
        assertEquals(0, shortened.cellOffset());
        assertEquals(BigInteger.valueOf(600), shortened.totalBytes());
        assertEquals(
            33,
            shortened.cells()
                .size());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0L, 1L, 2L, 3L, 4L, 5L),
            shortened.cells()
                .stream()
                .filter(CellEntry::installed)
                .map(CellEntry::usedBytes)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        assertFalse(
            shortened.cells()
                .get(2)
                .installed());
        assertEquals(
            2,
            shortened.cells()
                .get(11)
                .usedBytes());
        cells.clear();
        assertTrue(
            StoragePanelState.capture(config, 66, 33)
                .cells()
                .isEmpty());
    }

    @Test
    void diskComponentsPhysicalPositionAndExtractionLockTravelInOneSnapshot() {
        ItemStack disk = new ItemStack(TestItems.DIAMOND);
        CompoundTag data = new CompoundTag();
        data.putString("disk-id", "stored-resources-42");
        CellComponents.writeCustomData(disk, CustomData.of(data));
        disk.setStackDisplayName("My disk");
        BlockPos position = new BlockPos(10, 70, 34);
        Component lock = Component.translatable("tooltip.neoecoae.storage.infinite_migration_locked");
        var interactionId = java.util.UUID.randomUUID();
        CellEntry entry = cell(50, 100).atDrive(position, disk, lock, interactionId);
        StoragePanelState received = decode(
            StoragePanelState
                .capture(config(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(entry), false), 0, 33)
                .toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT));
        CellEntry transported = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells());
        assertEquals(position, transported.position());
        assertEquals(interactionId, transported.interactionId());
        assertEquals(lock, transported.extractionBlockReason());
        assertTrue(ItemStack.areItemStacksEqual(disk, transported.stack()));
        disk.setStackDisplayName("Changed after capture");
        assertEquals(
            Component.literal("My disk"),
            Component.literal(
                entry.stack()
                    .getDisplayName()));
    }

    @Test
    void emptyPhysicalDrivesKeepTheirPositionsWhilePaddingHasNoInstallationTarget() {
        BlockPos position = new BlockPos(5, 60, 14);
        CellEntry emptyDrive = CellEntry.EMPTY.atDrive(position, null, null);
        var received = decode(
            StoragePanelState
                .capture(config(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(emptyDrive), false), 0, 33)
                .toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT));
        assertEquals(
            position,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells())
                .position());
        assertFalse(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells())
                .installed());
        assertTrue(
            cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(received.cells())
                    .stack()));
        assertNull(
            received.cells()
                .get(1)
                .position());
    }

    @Test
    void combinedCapacityDoesNotOverflowALong() {
        long finiteCapacity = Long.MAX_VALUE - 1;
        List<CellEntry> cells = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            cell(finiteCapacity / 2, finiteCapacity),
            cell(finiteCapacity / 2, finiteCapacity),
            cell(finiteCapacity / 2, finiteCapacity));
        double ratio = StoragePanelState.usage(cells);
        assertEquals(0.5, ratio, 1.0E-14);
        StoragePanelState captured = StoragePanelState.capture(config(cells, false), 0, 33);
        assertEquals(
            BigInteger.valueOf(finiteCapacity)
                .multiply(BigInteger.valueOf(3)),
            captured.totalBytes());
        assertEquals(
            captured.totalBytes(),
            decode(captured.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)).totalBytes());
        assertEquals(0, StoragePanelState.usage(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        assertEquals(1, StoragePanelState.usage(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(cell(5, -1))));
    }

    @Test
    void specialCellCapacityIsExcludedFromHostTotalsAndUsageAcrossPages() {
        CellEntry special = cell(1_000, Long.MAX_VALUE);
        StoragePanelState onlySpecial = StoragePanelState
            .capture(config(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(special), false), 0, 33);
        assertEquals(BigInteger.ZERO, onlySpecial.totalBytes());
        assertEquals(0, onlySpecial.usage());
        assertFalse(onlySpecial.unbounded());
        assertEquals(special, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(onlySpecial.cells()));

        List<CellEntry> mixed = new ArrayList<>(java.util.Collections.nCopies(35, cell(50, 100)));
        mixed.add(special);
        for (int offset : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0, 33)) {
            StoragePanelState captured = StoragePanelState.capture(config(mixed, false), offset, 33);
            assertEquals(BigInteger.valueOf(3_500), captured.totalBytes());
            assertEquals(0.5, captured.usage(), 1.0E-14);
            StoragePanelState synced = decode(
                captured.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT));
            assertEquals(captured.totalBytes(), synced.totalBytes());
            assertEquals(captured.usage(), synced.usage());
        }
    }

    @Test
    void finiteCapacityShowsColoredUsedAndMaximumWhileInfiniteShowsOnlyPurpleUsage() {
        int[] used = { 0, 90, 100 };
        int[] usedColors = { HostText.USED, HostText.ORANGE, HostText.ERROR };
        for (int index = 0; index < used.length; index++) {
            StoragePanelState state = new StoragePanelState(
                Component.empty(),
                2,
                true,
                false,
                false,
                0,
                100,
                0,
                Integer.toString(used[index]),
                used[index] / 100D,
                1,
                0,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(cell(used[index], 100)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                "",
                0,
                false);
            Component caption = StorageHostCharts.capacityValue(state);
            assertEquals(used[index] + " / 100", caption.getString());
            List<Integer> colors = new ArrayList<>();
            caption.visit((style, text) -> {
                if (!text.isEmpty()) colors.add(
                    style.getColor()
                        .getValue());
                return java.util.Optional.<Void>empty();
            }, cn.dancingsnow.neoecoae.platform.minecraft.Component.TextStyle.EMPTY);
            assertEquals(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(usedColors[index], 0x3F3D52, StoragePanelTextures.VALUE_TEXT),
                colors);
        }
        Component infinite = StorageHostCharts
            .capacityValue(barState(true, 1, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        assertEquals("150", infinite.getString());
        assertEquals(
            StoragePanelTextures.VALUE_TEXT,
            infinite.getStyle()
                .getColor()
                .getValue());
    }

    @Test
    void uninstalledTypesAreOmittedWhileAnInstalledEmptyDiskRetainsItsCard() {
        List<CellEntry> cells = new ArrayList<>();
        cells.add(cell(0, 0).atDrive(BlockPos.ZERO, new ItemStack(TestItems.DIAMOND), null));
        StorageHostUI.Config base = config(cells, false);
        var absent = new StorageHostUI.StorageTypeLine(
            new ECOCellType(Component.literal("unused"), 1),
            8,
            () -> 0,
            () -> 0,
            () -> 0,
            () -> 0,
            () -> "0");
        var empty = new StorageHostUI.StorageTypeLine(
            new ECOCellType(Component.literal("installed"), 1),
            7,
            () -> 0,
            () -> 0,
            () -> 0,
            () -> 0,
            () -> "0");
        StorageHostUI.Config config = new StorageHostUI.Config(
            base.title(),
            base.tier(),
            base.formed(),
            base.storedEnergy(),
            base.maxEnergy(),
            base.energyConsumePerTick(),
            base.totalUsedBytesText(),
            base.cellEntries(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(absent, empty),
            base.infiniteStorage(),
            base.migratingToInfinite(),
            base.canExtractInfiniteComponents(),
            base.domainId(),
            base.missingMembers(),
            base.infiniteComponentInventory());
        StoragePanelState installed = StoragePanelState.capture(config, 0, 33);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(7),
            installed.types()
                .stream()
                .map(StoragePanelState.TypeLine::id)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        cells.set(0, CellEntry.EMPTY.atDrive(BlockPos.ZERO, null, null));
        assertTrue(
            StoragePanelState.capture(config, 0, 33)
                .types()
                .isEmpty());
    }

    @Test
    void emptyPhysicalDriveDoesNotCreateTheFirstInfiniteResourceCard() {
        StorageHostUI.Config base = config(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(CellEntry.EMPTY.atDrive(BlockPos.ZERO, null, null)),
            true);
        var unusedExperience = new StorageHostUI.StorageTypeLine(
            new ECOCellType(Component.literal("experience"), 1),
            -1,
            () -> 0,
            () -> 0,
            () -> 0,
            () -> 0,
            () -> "0");
        var storedFluid = new StorageHostUI.StorageTypeLine(
            new ECOCellType(Component.literal("fluid"), 1),
            -2,
            () -> 1,
            () -> 0,
            () -> 50,
            () -> 0,
            () -> "50");
        StorageHostUI.Config config = new StorageHostUI.Config(
            base.title(),
            base.tier(),
            base.formed(),
            base.storedEnergy(),
            base.maxEnergy(),
            base.energyConsumePerTick(),
            base.totalUsedBytesText(),
            base.cellEntries(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(unusedExperience, storedFluid),
            base.infiniteStorage(),
            base.migratingToInfinite(),
            base.canExtractInfiniteComponents(),
            base.domainId(),
            base.missingMembers(),
            base.infiniteComponentInventory());

        StoragePanelState state = StoragePanelState.capture(config, 0, 33);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(-2),
            state.types()
                .stream()
                .map(StoragePanelState.TypeLine::id)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(-2),
            decode(state.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)).types()
                .stream()
                .map(StoragePanelState.TypeLine::id)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
    }

    @Test
    void transportKeepsTranslatedNamesColorsAndHugeInfiniteAmounts() {
        String huge = "12345678901234567890123456789012345678901234567890";
        Component name = Component.translatable(
            "custom.storage",
            Component.literal("sample")
                .withColor(0x9BC9FE))
            .withColor(0xFFB2D8);
        StoragePanelState.TypeLine type = new StoragePanelState.TypeLine(
            7,
            name,
            0xFFB2D8,
            100,
            -1,
            Long.MAX_VALUE,
            -1,
            huge);
        StoragePanelState state = new StoragePanelState(
            Component.translatable("host.title"),
            3,
            true,
            true,
            false,
            50,
            100,
            20,
            huge,
            1,
            100,
            99,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new CellEntry(7, 3, 1, 0, -1, 0, -1, true)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(type),
            "test-domain",
            2,
            true);
        assertEquals(state, decode(state.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)));
        assertEquals(
            huge,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .first(decode(state.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)).types())
                .bytesText());
        assertEquals(
            name,
            decode(state.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)).type(7)
                .name());
    }

    @Test
    void resourceChannelsUseTheSuppliedLegendInNormalAndInfiniteModes() {
        String[] normal = { "fluids", "items" };
        String[] infinite = { "ae2:f", "ae2:i" };
        for (int i = 0; i < normal.length; i++) {
            assertEquals(i + 1, StoragePanelTextures.swatchIndex(ResourceLocation.parse("neoecoae:" + normal[i])));
            assertEquals(i + 1, StoragePanelTextures.swatchIndex(ResourceLocation.parse(infinite[i])));
        }
        assertEquals(2, StoragePanelTextures.swatchIndex(ResourceLocation.parse("neoecoae:bulk_item")));
        for (String excluded : new String[] { "neoecoae:mega_mana", "mekanism:spent_nuclear_waste",
            "data_energistics:digitalization", "other:mana" }) {
            assertEquals(
                StoragePanelTextures.FALLBACK_SWATCH,
                StoragePanelTextures.swatchIndex(ResourceLocation.parse(excluded)));
        }
        var unknown = StoragePanelTextures.swatch(StoragePanelTextures.FALLBACK_SWATCH, 0x54E6F2);
        assertEquals(16, unknown.spritePosition.x);
        assertEquals(78, unknown.spritePosition.y);
        assertEquals(0xFF54E6F2, unknown.color);
        assertEquals(-1, StoragePanelTextures.swatch(7, 0xA56ACB).color);
        for (int i = 10; i <= 13; i++) {
            int expectedX = 64 + (i - 10) * 8;
            var swatch = StoragePanelTextures.swatch(i, 0);
            assertEquals(expectedX, swatch.spritePosition.x);
            assertEquals(62, swatch.spritePosition.y);
            assertEquals(-1, swatch.color);

            var icon = StoragePanelTextures.resourceIcon(i, 0);
            assertEquals(expectedX, icon.spritePosition.x);
            assertEquals(86, icon.spritePosition.y);
        }
    }

    @Test
    void resourceDistributionMergesAliasesOrdersAmountsAndFillsTheBar() {
        List<StoragePanelState.TypeLine> types = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .listOf(type(1, 30), type(2, 40), type(0, 20), type(2, 60));
        StoragePanelState state = barState(false, 0.75, types);
        var segments = StorageHostCharts.capacitySegments(state, 160);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(2, 1, 0),
            segments.stream()
                .map(StorageHostCharts.CapacitySegment::swatch)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(107, 32, 21),
            segments.stream()
                .map(StorageHostCharts.CapacitySegment::width)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        assertEquals(
            160,
            segments.stream()
                .mapToInt(StorageHostCharts.CapacitySegment::width)
                .sum());
        assertTrue(
            StorageHostCharts
                .capacitySegments(barState(true, 1, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()), 160)
                .isEmpty());
    }

    @Test
    void finiteResourceDistributionRemainsVisibleWhenCapacityUsageRoundsToZeroPixels() {
        var state = barState(
            false,
            0.000001,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(type(2, 32_769), type(1, 81_796)));
        var transported = decode(state.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT));
        assertEquals(0, Math.round(160 * transported.usage()));
        var segments = StorageHostCharts.capacitySegments(transported, 160);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(1, 2),
            segments.stream()
                .map(StorageHostCharts.CapacitySegment::swatch)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(114, 46),
            segments.stream()
                .map(StorageHostCharts.CapacitySegment::width)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
        assertTrue(
            StorageHostCharts
                .capacitySegments(
                    barState(false, 0.000001, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
                    160)
                .isEmpty());
        assertTrue(
            StorageHostCharts.capacitySegments(transported, 0)
                .isEmpty());
    }

    @Test
    void infiniteStackedBarUsesExactAmountsBeyondLongAndTransportsTheirProportions() {
        BigInteger huge = BigInteger.TEN.pow(100);
        StoragePanelState.TypeLine item = new StoragePanelState.TypeLine(
            2,
            Component.literal("item"),
            0xEC8600,
            1,
            -1,
            Long.MAX_VALUE,
            -1,
            "3E100",
            2,
            huge.multiply(BigInteger.valueOf(3)));
        StoragePanelState.TypeLine fluid = new StoragePanelState.TypeLine(
            1,
            Component.literal("fluid"),
            0x3894FF,
            1,
            -1,
            Long.MAX_VALUE,
            -1,
            "1E100",
            1,
            huge);
        StoragePanelState state = barState(true, 1, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(fluid, item));
        StoragePanelState transported = decode(state.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT));
        assertEquals(state, transported);
        var segments = StorageHostCharts.capacitySegments(transported, 160);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(120, 40),
            segments.stream()
                .map(StorageHostCharts.CapacitySegment::width)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
    }

    @Test
    void actualDriveStatesAndEmptyPositionsSurviveSynchronization() {
        List<CellEntry> cells = new ArrayList<>();
        cells.add(CellEntry.EMPTY);
        for (CellState state : CellState.values()) {
            cells.add(new CellEntry(7, 2, CellEntry.KIND_ITEM, 2, 315, 20, 100, false, state, false));
        }
        StoragePanelState captured = StoragePanelState.capture(config(cells, false), 0, 33);
        assertEquals(captured, decode(captured.toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)));
        assertFalse(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(captured.cells())
                .installed());
        assertTrue(
            captured.cells()
                .get(1)
                .installed());
    }

    private static StoragePanelState.TypeLine type(int swatch, long bytes) {
        return new StoragePanelState.TypeLine(
            swatch,
            Component.literal("type"),
            StoragePanelTextures.COLORS[swatch],
            1,
            10,
            bytes,
            200,
            Long.toString(bytes),
            swatch,
            BigInteger.valueOf(bytes));
    }

    private static StoragePanelState barState(boolean infinite, double usage, List<StoragePanelState.TypeLine> types) {
        return new StoragePanelState(
            Component.empty(),
            2,
            true,
            infinite,
            false,
            0,
            100,
            0,
            "150",
            usage,
            0,
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            types,
            "",
            0,
            false);
    }

    static CellEntry cell(long used, long total) {
        return new CellEntry(7, 1, CellEntry.KIND_ITEM, 2, 315, used, total, total < 0);
    }

    static StorageHostUI.Config config(List<CellEntry> cells, boolean infinite) {
        Component name = Component.translatable("custom.storage")
            .withColor(0xF89737);
        StorageHostUI.StorageTypeLine type = new StorageHostUI.StorageTypeLine(
            new ECOCellType(name, 1),
            7,
            () -> 2,
            () -> infinite ? -1 : 315,
            () -> 50,
            () -> infinite ? -1 : 100,
            () -> "999999999999999999999999");
        return new StorageHostUI.Config(
            () -> Component.translatable("host.title"),
            () -> 2,
            () -> true,
            () -> 50,
            () -> 100,
            () -> 20,
            () -> "50",
            () -> cells,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(type),
            () -> infinite,
            () -> false,
            () -> false,
            () -> "test-domain",
            () -> 0,
            new ItemStackHandler(1));
    }

    private static StoragePanelState decode(cn.dancingsnow.neoecoae.platform.nbt.Tag tag) {
        return StoragePanelState.fromTag(tag, cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT);
    }
}
