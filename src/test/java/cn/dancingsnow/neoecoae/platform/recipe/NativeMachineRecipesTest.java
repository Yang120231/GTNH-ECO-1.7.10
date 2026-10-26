package cn.dancingsnow.neoecoae.platform.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

class NativeMachineRecipesTest {

    @Test
    void countedIngredientsNeverReuseTheSameInputQuantity() {
        var iron = SourceIngredient.alternatives(new ItemStack(TestItems.IRON_INGOT));
        var recipe = new IntegratedWorkingStationRecipe(
            Arrays.asList(new SizedIngredient(iron, 64), new SizedIngredient(iron, 64)),
            emptyFluid(),
            new ItemStack(TestItems.DIAMOND),
            null,
            1000000);
        assertFalse(
            recipe.matches(
                new IntegratedWorkingStationRecipe.Input(
                    Collections.singletonList(new ItemStack(TestItems.IRON_INGOT, 64)),
                    null),
                null));
        var inputs = Arrays.asList(new ItemStack(TestItems.IRON_INGOT, 64), new ItemStack(TestItems.IRON_INGOT, 64));
        assertTrue(recipe.matches(new IntegratedWorkingStationRecipe.Input(inputs, null), null));
        assertEquals(64, inputs.get(0).stackSize);
        assertEquals(100, recipe.processingTicks());
    }

    @Test
    void fluidInputChecksBothIdentityAndMillibuckets() {
        var recipe = new IntegratedWorkingStationRecipe(
            Collections.emptyList(),
            new SizedFluidIngredient(SourceFluidIngredient.of(TestFluids.WATER), 1000),
            null,
            fluid(TestFluids.LAVA, 1000),
            1000,
            37);
        assertFalse(recipe.matches(new IntegratedWorkingStationRecipe.Input(Collections.emptyList(), null), null));
        assertFalse(
            recipe.matches(
                new IntegratedWorkingStationRecipe.Input(Collections.emptyList(), fluid(TestFluids.WATER, 999)),
                null));
        assertFalse(
            recipe.matches(
                new IntegratedWorkingStationRecipe.Input(Collections.emptyList(), fluid(TestFluids.LAVA, 1000)),
                null));
        assertTrue(
            recipe.matches(
                new IntegratedWorkingStationRecipe.Input(Collections.emptyList(), fluid(TestFluids.WATER, 1000)),
                null));
        assertEquals(37, recipe.processingTicks());
        assertThrows(
            IllegalArgumentException.class,
            () -> new IntegratedWorkingStationRecipe(Collections.emptyList(), emptyFluid(), null, null, 1000, 0));
    }

    @Test
    void returnedMachineOutputsCannotMutateSharedRecipes() {
        var item = new ItemStack(TestItems.DIAMOND, 4);
        item.setStackDisplayName("Source output");
        var fluid = fluid(TestFluids.WATER, 1000);
        var recipe = new IntegratedWorkingStationRecipe(Collections.emptyList(), emptyFluid(), item, fluid, 1000);
        item.stackSize = 1;
        fluid.amount = 1;
        recipe.itemOutput().stackSize = 2;
        recipe.fluidOutput().amount = 2;
        assertEquals(4, recipe.itemOutput().stackSize);
        assertEquals(1000, recipe.fluidOutput().amount);
        assertEquals(
            "Source output",
            recipe.itemOutput()
                .getDisplayName());
    }

    @Test
    void emptyCoolantOutputRejectsAnOccupiedByproductTank() {
        var water = new SizedFluidIngredient(SourceFluidIngredient.of(TestFluids.WATER), 100);
        var recipe = new CoolingRecipe(water, null, 1500, 2);
        assertTrue(recipe.matches(new CoolingRecipe.Input(fluid(TestFluids.WATER, 100), null), null));
        assertFalse(recipe.matches(new CoolingRecipe.Input(fluid(TestFluids.WATER, 99), null), null));
        assertFalse(
            recipe.matches(new CoolingRecipe.Input(fluid(TestFluids.WATER, 100), fluid(TestFluids.LAVA, 1)), null));
        assertEquals(100, recipe.inputAmount());
        assertEquals(0, recipe.outputAmount());
        assertEquals(1500, recipe.coolant());
        assertEquals(2, recipe.maxOverclock());
    }

    @Test
    void blastingRetainsItsOwnTimingAndExperience() {
        var recipe = new BlastingRecipes.Recipe(
            ResourceLocation.parse("neoecoae:aluminum_blasting"),
            SourceIngredient.alternatives(new ItemStack(TestItems.RAW_IRON)),
            new ItemStack(TestItems.IRON_INGOT),
            0.8F,
            100);
        assertEquals(100, recipe.cookingTime());
        assertEquals(0.8F, recipe.experience());
        recipe.result().stackSize = 64;
        assertEquals(1, recipe.result().stackSize);
    }

    private static FluidStack fluid(net.minecraftforge.fluids.Fluid identity, int amount) {
        var stack = org.mockito.Mockito.mock(FluidStack.class);
        stack.amount = amount;
        org.mockito.Mockito.when(stack.getFluid())
            .thenReturn(identity);
        org.mockito.Mockito.when(stack.copy())
            .thenAnswer(invocation -> fluid(identity, stack.amount));
        return stack;
    }

    private static SizedFluidIngredient emptyFluid() {
        return new SizedFluidIngredient(SourceFluidIngredient.empty(), 1);
    }
}
