package cn.dancingsnow.neoecoae.platform.minecraft.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingStateSlots;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;

class CraftingInputTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8 })
    void singleIngredientKeepsItsOriginalSlotAndMatchesNativeShapedRecipe(int slot) {
        var grid = new TransientCraftingContainer(3, 3);
        grid.setItem(slot, new ItemStack(TestItems.DIAMOND));
        var input = grid.asCraftInput();
        assertEquals(1, input.size());
        var nativeGrid = input.nativeInventory();
        assertEquals(9, nativeGrid.getSizeInventory());
        for (int index = 0; index < 9; index++) {
            if (index == slot) assertSame(TestItems.DIAMOND, nativeGrid.getStackInSlot(index).getItem());
            else assertNull(nativeGrid.getStackInSlot(index));
        }
        var recipe = new ShapedRecipes(
            1,
            1,
            new ItemStack[] { new ItemStack(TestItems.DIAMOND) },
            new ItemStack(TestItems.IRON_NUGGET, 4));
        assertTrue(recipe.matches(nativeGrid, null));
        assertEquals(4, recipe.getCraftingResult(nativeGrid).stackSize);
        nativeGrid.setInventorySlotContents(slot, null);
        assertNotNull(input.getItem(0));
    }

    @Test
    void offsetMultiRowRecipeRetainsInternalEmptySlotsAndMatchesShapedAndShapelessRecipes() {
        var grid = new TransientCraftingContainer(3, 3);
        grid.setItem(4, new ItemStack(TestItems.STONE));
        grid.setItem(5, new ItemStack(TestItems.DIRT));
        grid.setItem(8, new ItemStack(TestItems.SAND));
        var positioned = grid.asPositionedCraftInput();
        assertEquals(1, positioned.left());
        assertEquals(1, positioned.top());
        var input = positioned.input();
        assertEquals(2, input.width());
        assertEquals(2, input.height());
        assertNull(input.getItem(2));
        var nativeGrid = input.nativeInventory();
        var recipe = new ShapedRecipes(
            2,
            2,
            new ItemStack[] { new ItemStack(TestItems.STONE), new ItemStack(TestItems.DIRT), null,
                new ItemStack(TestItems.SAND) },
            new ItemStack(TestItems.GLASS));
        assertTrue(recipe.matches(nativeGrid, null));
        var shapeless = new ShapelessRecipes(
            new ItemStack(TestItems.GLASS),
            Arrays.asList(new ItemStack(TestItems.STONE), new ItemStack(TestItems.DIRT), new ItemStack(TestItems.SAND)));
        assertTrue(shapeless.matches(nativeGrid, null));
    }

    @Test
    void containerRemaindersKeepTheirOriginalSlotsAfterNativeAssembly() {
        var grid = new TransientCraftingContainer(3, 3);
        grid.setItem(7, new ItemStack(TestItems.WATER_BUCKET));
        var positioned = grid.asPositionedCraftInput();
        assertSame(TestItems.WATER_BUCKET, positioned.input().nativeInventory().getStackInSlot(7).getItem());
        var remaining = ECOCraftingStateSlots.expandRemainingItems(
            positioned,
            Collections.singletonList(new ItemStack(TestItems.BUCKET)),
            3,
            3);
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 7) assertSame(TestItems.BUCKET, remaining.get(slot).getItem());
            else assertNull(remaining.get(slot));
        }
    }

    @Test
    void emptyInputStillRetainsThePhysicalGridDimensions() {
        var input = CraftingInput.of(3, 3, Collections.nCopies(9, null));
        assertTrue(input.isEmpty());
        var nativeGrid = input.nativeInventory();
        assertEquals(9, nativeGrid.getSizeInventory());
        for (int slot = 0; slot < 9; slot++) assertNull(nativeGrid.getStackInSlot(slot));
    }
}
