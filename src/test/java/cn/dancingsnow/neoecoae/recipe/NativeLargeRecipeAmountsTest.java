package cn.dancingsnow.neoecoae.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceIngredient;

class NativeLargeRecipeAmountsTest {

    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
    }

    @Test
    void itemsAndFluidsMustUseTheSameWholeRecipeMultiplier() {
        var display = new IntegratedWorkingStationRecipe(
            Collections.singletonList(new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT), 128)),
            new SizedFluidIngredient(SourceFluidIngredient.of(TestFluids.WATER), 1000),
            new ItemStack(TestItems.DIAMOND, 8),
            fluid(TestFluids.LAVA, 250),
            12345);
        var recipe = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:fluids"),
            display,
            12345,
            Collections.emptyList());
        var inputs = items(TestItems.IRON_INGOT, 256);
        inputs.add(AEFluidKey.of(TestFluids.WATER), 2000);
        var outputs = items(TestItems.DIAMOND, 16);
        outputs.add(AEFluidKey.of(TestFluids.LAVA), 500);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        outputs.remove(AEFluidKey.of(TestFluids.LAVA), 250);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        outputs.add(AEFluidKey.of(TestFluids.LAVA), 250);
        inputs.remove(AEFluidKey.of(TestFluids.WATER), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test
    void scaledSelectionSkipsOverflowingPreferredContracts() {
        var display = display(2);
        var overflowing = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:overflow"),
            display,
            Long.MAX_VALUE,
            Collections.emptyList());
        var legal = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:legal"),
            display,
            100,
            Collections.emptyList());
        var match = LargeWorkstationRecipes
            .findScaled(Arrays.asList(overflowing, legal), items(TestItems.IRON_INGOT, 8), items(TestItems.DIAMOND, 4));
        assertNotNull(match);
        assertEquals(legal, match.recipe());
        assertEquals(2, match.multiplier());
    }

    @Test
    void sourceCostSelectionComparesTheActualScaledEnergyBudget() {
        var one = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:one"),
            display(1),
            100,
            Collections.emptyList());
        var twoDisplay = new IntegratedWorkingStationRecipe(
            Collections.singletonList(new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT), 8)),
            new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
            new ItemStack(TestItems.DIAMOND, 2),
            null,
            150);
        var two = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:two"),
            twoDisplay,
            150,
            Collections.emptyList());
        var match = LargeWorkstationRecipes
            .findScaled(Arrays.asList(one, two), items(TestItems.IRON_INGOT, 8), items(TestItems.DIAMOND, 2));
        assertNotNull(match);
        assertEquals(two, match.recipe());
        assertEquals(1, match.multiplier());
    }

    @Test
    void largeDurationCapRetainsTheSmallRecipeAndBudgets() {
        var display = new IntegratedWorkingStationRecipe(
            Collections.emptyList(),
            new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
            null,
            null,
            1000,
            160000);
        var recipe = new LargeWorkstationRecipe(
            ResourceLocation.parse("test:duration"),
            display,
            1000,
            Collections.emptyList());
        assertEquals(200, recipe.processingTicks());
        assertEquals(160000, display.processingTicks());
        var adjusted = recipe.processing(199, 2000, true);
        assertEquals(199, adjusted.processingTicks());
        var capped = adjusted.processing(Integer.MAX_VALUE, 2000, true);
        assertEquals(200, capped.processingTicks());
        assertEquals(1000, capped.energy());
        assertEquals(2000, capped.feEnergy());
        assertTrue(capped.feAlternative());
    }

    private static IntegratedWorkingStationRecipe display(int output) {
        return new IntegratedWorkingStationRecipe(
            Collections.singletonList(new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT), 4)),
            new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
            new ItemStack(TestItems.DIAMOND, output),
            null,
            100);
    }

    private static KeyCounter items(Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }

    private static FluidStack fluid(Fluid identity, int amount) {
        var stack = org.mockito.Mockito.mock(FluidStack.class);
        stack.amount = amount;
        org.mockito.Mockito.when(stack.getFluid())
            .thenReturn(identity);
        org.mockito.Mockito.when(stack.copy())
            .thenAnswer(invocation -> fluid(identity, stack.amount));
        return stack;
    }
}
