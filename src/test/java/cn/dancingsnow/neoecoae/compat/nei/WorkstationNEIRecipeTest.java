package cn.dancingsnow.neoecoae.compat.nei;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceIngredient;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

class WorkstationNEIRecipeTest {

    @Test
    void itemAlternativesPreserveAmountsWithoutChangingMachineIngredients() {
        var ingredient = SourceIngredient
            .alternatives(new ItemStack(TestItems.IRON_INGOT), new ItemStack(TestItems.DIAMOND));
        var view = new WorkstationNEIRecipe(
            new IntegratedWorkingStationRecipe(
                Collections.singletonList(new SizedIngredient(ingredient, 128)),
                new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
                new ItemStack(TestItems.RAW_IRON, 4),
                null,
                42000,
                37));
        assertEquals(
            2,
            view.itemInputs()
                .get(0)
                .size());
        for (var stack : view.itemInputs()
            .get(0)) assertEquals(128, stack.stackSize);
        view.itemInputs()
            .get(0)
            .get(0).stackSize = 1;
        assertEquals(
            128,
            view.itemInputs()
                .get(0)
                .get(0).stackSize);
        assertEquals(
            1,
            ingredient.examples()
                .get(0).stackSize);
        assertTrue(view.uses(new ItemStack(TestItems.DIAMOND), null));
        assertTrue(view.produces(new ItemStack(TestItems.RAW_IRON), null));
        assertFalse(view.produces(new ItemStack(TestItems.IRON_INGOT), null));
        assertEquals(42000, view.energy);
        assertEquals(37, view.ticks);
    }

    @Test
    void fluidOnlyRecipesAreDiscoverableByFluidRegardlessOfContainerAmount() {
        var view = new WorkstationNEIRecipe(
            new IntegratedWorkingStationRecipe(
                Collections.emptyList(),
                new SizedFluidIngredient(SourceFluidIngredient.of(TestFluids.WATER), 144),
                null,
                fluid(TestFluids.LAVA, 72),
                1000));
        assertTrue(view.uses(null, fluid(TestFluids.WATER, 1000)));
        assertFalse(view.uses(null, fluid(TestFluids.LAVA, 1000)));
        assertTrue(view.produces(null, fluid(TestFluids.LAVA, 1000)));
        assertFalse(view.produces(null, fluid(TestFluids.WATER, 1000)));
        assertEquals(144, view.fluid.amount());
        assertEquals(72, view.fluidOutput.amount);
    }

    @Test
    void itemAndFluidOutputsBothRemainDiscoverableAndItemNbtMustMatch() {
        var output = new ItemStack(TestItems.DIAMOND, 4);
        output.setStackDisplayName("Special output");
        var view = new WorkstationNEIRecipe(
            new IntegratedWorkingStationRecipe(
                Arrays
                    .asList(new SizedIngredient(SourceIngredient.alternatives(new ItemStack(TestItems.IRON_INGOT)), 2)),
                new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
                output,
                fluid(TestFluids.LAVA, 500),
                100));
        assertTrue(view.produces(output.copy(), null));
        assertFalse(view.produces(new ItemStack(TestItems.DIAMOND), null));
        assertTrue(view.produces(null, fluid(TestFluids.LAVA, 1)));
    }

    @Test
    void coolingRecipesWithNoByproductStillExposeTheirInputAndLimits() {
        var view = new WorkstationNEIRecipe(
            new CoolingRecipe(
                new SizedFluidIngredient(SourceFluidIngredient.of(TestFluids.WATER), 100),
                null,
                1500,
                2));
        assertTrue(view.uses(null, fluid(TestFluids.WATER, 1)));
        assertFalse(view.produces(null, fluid(TestFluids.WATER, 1)));
        assertEquals(100, view.fluid.amount());
        assertEquals(1500, view.coolant);
        assertEquals(2, view.maxOverclock);
    }

    private static FluidStack fluid(Fluid identity, int amount) {
        var stack = mock(FluidStack.class);
        stack.amount = amount;
        when(stack.getFluid()).thenReturn(identity);
        when(stack.copy()).thenAnswer(invocation -> fluid(identity, stack.amount));
        when(stack.isFluidEqual(any(FluidStack.class)))
            .thenAnswer(invocation -> identity == ((FluidStack) invocation.getArgument(0)).getFluid());
        return stack;
    }
}
