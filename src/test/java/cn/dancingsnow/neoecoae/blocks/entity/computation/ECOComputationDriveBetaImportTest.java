package cn.dancingsnow.neoecoae.blocks.entity.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** 7.3.0-beta computation drives keep their cell, even one the current drive would no longer accept. */
class ECOComputationDriveBetaImportTest {

    private static final String TEST_ITEM = "test_item";

    private static final class TestDrive extends ECOComputationDriveBlockEntity {

        TestDrive() {
            super(
                new BlockEntityType<>("test_computation_drive", () -> null),
                BlockPos.ZERO,
                new BlockState(mock(Block.class), Collections.emptyMap()));
        }

        @Override
        ItemStack loadBetaStack(NBTTagCompound tag) {
            Item item = TestItemRegistry.CONTEXT.item(ResourceLocation.parse(tag.getString(TEST_ITEM)));
            return item == null ? null : new ItemStack(item);
        }
    }

    @BeforeEach
    void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void betaCellIsRestoredAndDropsWithTheDrive() {
        var drive = new TestDrive();

        drive.loadTag(beta("minecraft:diamond"), TestItemRegistry.CONTEXT);

        assertSame(
            TestItems.DIAMOND,
            drive.getCellStack()
                .getItem(),
            "Restored without the tier check");
        List<ItemStack> drops = new ArrayList<>();
        drive.addAdditionalDrops(null, BlockPos.ZERO, drops);
        assertEquals(1, drops.size());
    }

    @Test
    void unresolvedBetaCellSurvivesCurrentFormatSaves() {
        var drive = new TestDrive();
        drive.loadTag(beta("missing:cell"), TestItemRegistry.CONTEXT);
        assertNull(drive.getCellStack());

        var saved = new CompoundTag();
        drive.saveAdditional(saved, TestItemRegistry.CONTEXT);
        var reloaded = new TestDrive();
        reloaded.loadTag(saved, TestItemRegistry.CONTEXT);

        assertTrue(reloaded.hasUnresolvedBetaCell());
    }

    @Test
    void currentFormatDrivesIgnoreTheBetaKey() {
        var drive = new TestDrive();
        var saved = new CompoundTag();
        drive.saveAdditional(saved, TestItemRegistry.CONTEXT);
        saved.put("Cell", beta("minecraft:diamond").getCompound("Cell"));

        drive.loadTag(saved, TestItemRegistry.CONTEXT);

        assertNull(drive.getCellStack());
    }

    private static CompoundTag beta(String item) {
        var cell = new CompoundTag();
        cell.putString(TEST_ITEM, item);
        var tag = new CompoundTag();
        tag.putString("id", "neoecoae:computation_drive");
        tag.put("Cell", cell);
        return tag;
    }
}
