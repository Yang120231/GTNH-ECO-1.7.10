package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class ECOFastPathExpandedSupportTest {

    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
    }

    @Test
    void unchangedCatalystSupportsHugeFastPathBatchWithOneItem() {
        ItemStack catalyst = new ItemStack(TestItems.DIAMOND);
        ItemStack ingredient = new ItemStack(TestItems.COAL);
        var analysis = ECOReusableStateAnalyzer.analyze(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(catalyst, ingredient),
            cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.listOf(catalyst.copy(), null));
        assertFalse(analysis.rejected());
        var model = analysis.model();
        assertNotNull(model);
        long crafts = 137346430105L;
        var inputs = model.batchInputs(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(GenericStack.fromItemStack(catalyst), GenericStack.fromItemStack(ingredient)),
            crafts);
        assertEquals(1, amount(inputs, catalyst));
        assertEquals(crafts, amount(inputs, ingredient));
        assertEquals(
            1,
            amount(
                model.batchRemainders(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(GenericStack.fromItemStack(catalyst)),
                    crafts),
                catalyst));
    }

    @Test
    void fixedDamagedOutputIsCacheable() {
        ItemStack tool = new ItemStack(TestItems.IRON_PICKAXE);
        tool.setItemDamage(17);
        assertTrue(
            ECOFastPathStacks.areValidItemStacks(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(GenericStack.fromItemStack(tool)),
                Integer.MAX_VALUE,
                true,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH));
    }

    @Test
    void mixedToolAndCatalystAreConsumedAndReturnedOnce() {
        ItemStack tool = new ItemStack(TestItems.IRON_PICKAXE);
        tool.setItemDamage(tool.getMaxDamage() - 2);
        ItemStack worn = tool.copy();
        worn.setItemDamage(tool.getItemDamage() + 1);
        ItemStack catalyst = new ItemStack(TestItems.DIAMOND);
        ItemStack ingredient = new ItemStack(TestItems.COBBLESTONE);
        var analysis = ECOReusableStateAnalyzer.analyze(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(tool, catalyst, ingredient),
            cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.listOf(worn, catalyst.copy(), null));
        assertFalse(analysis.rejected());
        var model = analysis.model();
        assertNotNull(model);
        assertEquals(2, model.maxBatchSize());
        var inputs = model.batchInputs(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                GenericStack.fromItemStack(tool),
                GenericStack.fromItemStack(catalyst),
                GenericStack.fromItemStack(ingredient)),
            2);
        assertEquals(1, amount(inputs, tool));
        assertEquals(1, amount(inputs, catalyst));
        assertEquals(2, amount(inputs, ingredient));
        var remaining = model.batchRemainders(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(GenericStack.fromItemStack(worn), GenericStack.fromItemStack(catalyst)),
            2);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(GenericStack.fromItemStack(catalyst)),
            remaining);
    }

    @Test
    void numericChangesPreserveUnchangedNestedMetadata() {
        CompoundTag before = new CompoundTag();
        before.putString("owner", "player");
        CompoundTag energy = new CompoundTag();
        energy.putInt("stored", 100);
        energy.putString("type", "mana");
        before.put("energy", energy);
        CompoundTag after = before.copy();
        after.getCompound("energy")
            .putInt("stored", 90);
        CompoundTag result = ECOStateTransitionBatchModel.applyCustomDataTransition(before, after, 4)
            .orElseThrow(java.util.NoSuchElementException::new);
        assertEquals(
            60,
            result.getCompound("energy")
                .getInt("stored"));
        assertEquals(
            "mana",
            result.getCompound("energy")
                .getString("type"));
        assertEquals("player", result.getString("owner"));
        after.putString("owner", "another");
        assertTrue(
            !ECOStateTransitionBatchModel.applyCustomDataTransition(before, after, 4)
                .isPresent());
    }

    private static long amount(List<GenericStack> stacks, ItemStack item) {
        var key = GenericStack.fromItemStack(item)
            .what();
        return stacks.stream()
            .filter(
                stack -> stack.what()
                    .equals(key))
            .mapToLong(GenericStack::amount)
            .sum();
    }
}
