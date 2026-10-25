package cn.dancingsnow.neoecoae.platform.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestItems;

class SourceShapelessRecipeTest {

    private static InventoryCrafting table() {
        return new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, 3, 3);
    }

    @Test
    void overlappingAlternativesDoNotDependOnGridOrder() {
        var either = SourceIngredient.alternatives(new ItemStack(TestItems.STONE), new ItemStack(TestItems.DIRT));
        var stone = SourceIngredient.alternatives(new ItemStack(TestItems.STONE));
        var recipe = new SourceShapelessRecipe(
            new ItemStack(TestItems.IRON_INGOT),
            Arrays.asList(either, stone),
            false);
        var table = table();
        table.setInventorySlotContents(0, new ItemStack(TestItems.STONE));
        table.setInventorySlotContents(8, new ItemStack(TestItems.DIRT));
        assertTrue(recipe.matches(table, null));
        assertEquals(
            2,
            recipe.getInput()
                .size());
        table.setInventorySlotContents(4, new ItemStack(TestItems.DIRT));
        assertFalse(recipe.matches(table, null));
    }

    @Test
    void smithingCopiesBaseNbtWithoutMutatingInput() {
        var template = SourceIngredient.alternatives(new ItemStack(TestItems.STONE));
        var base = SourceIngredient.alternatives(new ItemStack(TestItems.IRON_INGOT));
        var addition = SourceIngredient.alternatives(new ItemStack(TestItems.DIRT));
        var recipe = new SourceShapelessRecipe(
            new ItemStack(TestItems.DIAMOND),
            Arrays.asList(template, base, addition),
            true);
        var table = table();
        var named = new ItemStack(TestItems.IRON_INGOT);
        named.setStackDisplayName("Preserve me");
        table.setInventorySlotContents(3, new ItemStack(TestItems.STONE));
        table.setInventorySlotContents(7, named);
        table.setInventorySlotContents(0, new ItemStack(TestItems.DIRT));
        var result = recipe.getCraftingResult(table);
        assertNotNull(result);
        assertEquals("Preserve me", result.getDisplayName());
        assertTrue(ItemStack.areItemStackTagsEqual(named, result));
        assertNotSame(named.getTagCompound(), result.getTagCompound());
    }
}
