package cn.dancingsnow.neoecoae.recipe;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceIngredient;

class LargeWorkstationRecipeCoreTest {

    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
    }

    @Test
    void overlappingTagsCanReassignEarlierAllocations() {
        assertTrue(
            LargeWorkstationRecipe.matchesQuantities(
                new long[] { 5, 5 },
                new long[] { 5, 5 },
                new boolean[][] { { true, true }, { true, false } }));
        assertFalse(
            LargeWorkstationRecipe.matchesQuantities(
                new long[] { 5, 5 },
                new long[] { 5, 5 },
                new boolean[][] { { true, true }, { false, false } }));
    }

    @Test
    void hugeCountsStayCompactAndRejectSurplus() {
        assertTrue(
            LargeWorkstationRecipe.matchesQuantities(
                new long[] { Integer.MAX_VALUE },
                new long[] { Integer.MAX_VALUE },
                new boolean[][] { { true } }));
        assertFalse(
            LargeWorkstationRecipe.matchesQuantities(new long[] { 6 }, new long[] { 5 }, new boolean[][] { { true } }));
    }

    @Test
    void fullOutputContractSelectsBetweenIdenticalInputs() {
        var recipe = nativeRecipe(new ItemStack(TestItems.DIAMOND, 2));
        KeyCounter inputs = items(TestItems.IRON_INGOT, 4);
        assertTrue(recipe.matches(inputs, items(TestItems.DIAMOND, 2)));
        assertFalse(recipe.matches(inputs, items(TestItems.DIAMOND, 1)));
        assertFalse(recipe.matches(inputs, items(TestItems.GOLD_INGOT, 2)));
        KeyCounter surplus = items(TestItems.DIAMOND, 2);
        surplus.add(AEItemKey.of(TestItems.STONE), 1);
        assertFalse(recipe.matches(inputs, surplus));
        assertFalse(recipe.matches(items(TestItems.IRON_INGOT, 5), items(TestItems.DIAMOND, 2)));
    }

    @Test
    void fractionalOutputsOrDifferentInputScaleAreRejected() {
        var recipe = nativeRecipe(new ItemStack(TestItems.DIAMOND, 2));
        assertEquals(0, recipe.matchingMultiplier(items(TestItems.IRON_INGOT, 6), items(TestItems.DIAMOND, 3)));
        assertEquals(0, recipe.matchingMultiplier(items(TestItems.IRON_INGOT, 4), items(TestItems.DIAMOND, 4)));
        assertEquals(0, recipe.matchingMultiplier(items(TestItems.IRON_INGOT, 9), items(TestItems.DIAMOND, 4)));
    }

    @Test
    void scaledOverlappingIngredientsAllowDifferentConcreteItemsPerRecipeOperation() {
        var recipe = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:mixed"),
            new IntegratedWorkingStationRecipe(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                    new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT, TestItems.GOLD_INGOT), 4),
                    new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT), 1)),
                new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
                new ItemStack(TestItems.DIAMOND),
                null,
                100),
            100,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var inputs = items(TestItems.IRON_INGOT, 3);
        inputs.add(AEItemKey.of(TestItems.GOLD_INGOT), 7);
        assertEquals(2, recipe.matchingMultiplier(inputs, items(TestItems.DIAMOND, 2)));
        inputs.remove(AEItemKey.of(TestItems.IRON_INGOT), 2);
        inputs.add(AEItemKey.of(TestItems.GOLD_INGOT), 2);
        assertEquals(0, recipe.matchingMultiplier(inputs, items(TestItems.DIAMOND, 2)));
    }

    private static LargeWorkstationRecipe nativeRecipe(ItemStack output) {
        return new LargeWorkstationRecipe(
            ResourceLocation.parse("test:recipe"),
            new IntegratedWorkingStationRecipe(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT), 4)),
                new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
                output,
                null,
                100),
            100,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
    }

    private static KeyCounter items(net.minecraft.item.Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }
}
