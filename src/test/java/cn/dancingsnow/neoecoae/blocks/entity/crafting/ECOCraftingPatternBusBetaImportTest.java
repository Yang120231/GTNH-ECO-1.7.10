package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
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
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** 7.3.0-beta pattern buses held 126 patterns; every one survives the move into the 63-slot bus. */
class ECOCraftingPatternBusBetaImportTest {

    private static final String TEST_ITEM = "test_item";

    /** Real bus; beta stacks name a test-registry item instead of a numeric FML item ID. */
    private static final class TestBus extends ECOCraftingPatternBusBlockEntity {

        TestBus() {
            super(
                new BlockEntityType<>("test_pattern_bus", () -> null),
                BlockPos.ZERO,
                new BlockState(mock(Block.class), Collections.emptyMap()));
        }

        void world(World value) {
            worldObj = value;
        }

        @Override
        ItemStack loadBetaStack(NBTTagCompound tag) {
            Item item = TestItemRegistry.CONTEXT.item(ResourceLocation.parse(tag.getString(TEST_ITEM)));
            return item == null ? null : new ItemStack(item, 1, tag.getInteger("Slot"));
        }

        @Override
        void postPatternChange() {}
    }

    @BeforeEach
    void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @AfterEach
    void stopServer() {
        ServerTasks.stopped();
    }

    @Test
    void fullBetaBusKeepsEveryPatternInSlotOrder() {
        var bus = new TestBus();
        var patterns = new ListTag();
        // Beta wrote only occupied slots, across both 63-slot pages.
        for (int slot = 125; slot >= 0; slot -= 2) patterns.add(entry(slot, "minecraft:diamond"));
        for (int slot = 124; slot >= 0; slot -= 2) patterns.add(entry(slot, "minecraft:stone"));

        bus.loadTag(beta(patterns), TestItemRegistry.CONTEXT);

        var inventory = bus.getInternalInventory();
        for (int slot = 0; slot < inventory.size(); slot++) assertEquals(
            slot,
            inventory.getStackInSlot(slot)
                .getItemDamage(),
            "Lowest beta slots fill in order");
        assertEquals(126 - inventory.size(), bus.betaOverflowCount());
        List<ItemStack> drops = new ArrayList<>();
        bus.addAdditionalDrops(null, BlockPos.ZERO, drops);
        assertEquals(126, drops.size(), "Breaking the bus drops every beta pattern");
    }

    @Test
    void unresolvedBetaItemsAreKeptInsteadOfDropped() {
        var bus = new TestBus();
        var patterns = new ListTag();
        patterns.add(entry(0, "missing:pattern"));
        patterns.add(entry(1, "minecraft:diamond"));

        bus.loadTag(beta(patterns), TestItemRegistry.CONTEXT);

        assertSame(
            TestItems.DIAMOND,
            bus.getInternalInventory()
                .getStackInSlot(0)
                .getItem());
        assertEquals(1, bus.betaOverflowCount());
    }

    @Test
    void overflowSurvivesCurrentFormatSaves() {
        var bus = new TestBus();
        var patterns = new ListTag();
        patterns.add(entry(0, "missing:pattern"));
        bus.loadTag(beta(patterns), TestItemRegistry.CONTEXT);

        var saved = new CompoundTag();
        bus.saveAdditional(saved, TestItemRegistry.CONTEXT);
        var reloaded = new TestBus();
        reloaded.loadTag(saved, TestItemRegistry.CONTEXT);

        assertEquals(1, reloaded.betaOverflowCount(), "The unresolved stack survives a current-format save");
    }

    @Test
    void freedSlotsRefillFromOverflowOnTheServer() {
        var bus = new TestBus();
        World world = mock(World.class);
        bus.world(world);
        var patterns = new ListTag();
        for (int slot = 0; slot < 65; slot++) patterns.add(entry(slot, "minecraft:diamond"));
        bus.loadTag(beta(patterns), TestItemRegistry.CONTEXT);

        bus.getInternalInventory()
            .setStackInSlot(5, null);

        assertEquals(
            63,
            bus.getInternalInventory()
                .getStackInSlot(5)
                .getItemDamage());
        assertEquals(1, bus.betaOverflowCount());
    }

    @Test
    void currentFormatBusesIgnoreTheBetaKey() {
        var bus = new TestBus();
        var saved = new CompoundTag();
        bus.saveAdditional(saved, TestItemRegistry.CONTEXT);
        var patterns = new ListTag();
        patterns.add(entry(0, "minecraft:diamond"));
        saved.put("Patterns", patterns);

        bus.loadTag(saved, TestItemRegistry.CONTEXT);

        assertNull(
            bus.getInternalInventory()
                .getStackInSlot(0));
        assertEquals(0, bus.betaOverflowCount());
    }

    private static CompoundTag beta(ListTag patterns) {
        var tag = new CompoundTag();
        tag.putString("id", "neoecoae:crafting_pattern_bus");
        tag.put("Patterns", patterns);
        return tag;
    }

    private static CompoundTag entry(int slot, String item) {
        var stack = new CompoundTag();
        stack.putString(TEST_ITEM, item);
        stack.putInt("Slot", slot);
        var entry = new CompoundTag();
        entry.putInt("Slot", slot);
        entry.put("Stack", stack);
        return entry;
    }
}
