package cn.dancingsnow.neoecoae.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

class ECOCraftingVirtualPoolTest {

    @Test
    void processingPatternRetainsEveryDeclaredOutput() {
        Item primary = new Item();
        Item byproduct = new Item();
        ItemStack runtimePrimary = new ItemStack(primary, 2);
        List<ItemStack> outputs = ECOCraftingVirtualPool.mergeProcessingOutputs(
            runtimePrimary,
            Arrays.asList(new ItemStack(primary, 2), new ItemStack(byproduct, 3)));

        assertEquals(2, outputs.size());
        assertEquals(2, outputs.get(0).stackSize);
        assertEquals(
            primary,
            outputs.get(0)
                .getItem());
        assertEquals(3, outputs.get(1).stackSize);
        assertEquals(
            byproduct,
            outputs.get(1)
                .getItem());
    }

    @Test
    void processingPatternOutputMergeKeepsDeclaredRemainderWhenRuntimeOutputMatchesLater() {
        Item primary = new Item();
        Item byproduct = new Item();
        List<ItemStack> outputs = ECOCraftingVirtualPool.mergeProcessingOutputs(
            new ItemStack(byproduct, 3),
            Arrays.asList(new ItemStack(primary, 2), new ItemStack(byproduct, 3)));

        assertEquals(2, outputs.size());
        assertEquals(
            primary,
            outputs.get(0)
                .getItem());
        assertEquals(2, outputs.get(0).stackSize);
        assertEquals(
            byproduct,
            outputs.get(1)
                .getItem());
        assertEquals(3, outputs.get(1).stackSize);
    }

}
