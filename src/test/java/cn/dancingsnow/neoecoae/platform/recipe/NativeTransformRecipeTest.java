package cn.dancingsnow.neoecoae.platform.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

class NativeTransformRecipeTest {

    @Test
    void waterOnlyStartsFromTheFirstIngredientButExplosionCanStartFromAny() {
        var dust = SourceIngredient.alternatives(new ItemStack(TestItems.STONE));
        var crystal = SourceIngredient.alternatives(new ItemStack(TestItems.DIAMOND));
        var water = recipe(false, dust, crystal);
        assertTrue(water.acceptsAnchor(new ItemStack(TestItems.STONE)));
        assertFalse(water.acceptsAnchor(new ItemStack(TestItems.DIAMOND)));
        assertTrue(water.inFluid(TestFluids.WATER));
        assertFalse(water.inFluid(TestFluids.LAVA));
        var explosion = recipe(true, dust, crystal);
        assertTrue(explosion.acceptsAnchor(new ItemStack(TestItems.DIAMOND)));
        assertFalse(explosion.inFluid(TestFluids.WATER));
    }

    @Test
    void aStackCanSatisfySeveralIngredientsButOnlyWithSufficientQuantity() {
        var material = SourceIngredient.alternatives(new ItemStack(TestItems.STONE));
        var recipe = recipe(false, material, material);
        var one = new ItemStack(TestItems.STONE);
        assertNull(recipe.claimed(Collections.singletonList(one), one));
        assertEquals(1, one.stackSize);
        var two = new ItemStack(TestItems.STONE, 2);
        assertArrayEquals(new int[] { 2 }, recipe.claimed(Collections.singletonList(two), two));
        assertEquals(2, two.stackSize);
    }

    @Test
    void missingOrUnrelatedItemsNeverCausePartialConsumption() {
        var first = SourceIngredient.alternatives(new ItemStack(TestItems.STONE));
        var second = SourceIngredient.alternatives(new ItemStack(TestItems.DIAMOND));
        var recipe = recipe(false, first, second);
        var stone = new ItemStack(TestItems.STONE, 64);
        var unrelated = new ItemStack(TestItems.DIRT, 64);
        assertNull(recipe.claimed(Arrays.asList(stone, unrelated), stone));
        assertEquals(64, stone.stackSize);
        assertArrayEquals(
            new int[] { 1, 0, 1 },
            recipe.claimed(Arrays.asList(stone, unrelated, new ItemStack(TestItems.DIAMOND, 7)), stone));
    }

    @Test
    void sourceClaimOrderIsPreservedForOverlappingPredicates() {
        var either = SourceIngredient.alternatives(new ItemStack(TestItems.STONE), new ItemStack(TestItems.DIRT));
        var stoneOnly = SourceIngredient.alternatives(new ItemStack(TestItems.STONE));
        var recipe = recipe(false, either, stoneOnly);
        var stone = new ItemStack(TestItems.STONE);
        var dirt = new ItemStack(TestItems.DIRT);
        assertNull(recipe.claimed(Arrays.asList(stone, dirt), stone));
        assertArrayEquals(new int[] { 1, 1 }, recipe.claimed(Arrays.asList(dirt, stone), dirt));
    }

    private static NativeTransformRecipe recipe(boolean explosion, SourceIngredient... inputs) {
        return new NativeTransformRecipe(
            ResourceLocation.parse("neoecoae:transform/test"),
            Arrays.asList(inputs),
            new ItemStack(TestItems.IRON_INGOT),
            explosion,
            explosion ? SourceFluidIngredient.empty() : SourceFluidIngredient.of(TestFluids.WATER));
    }
}
