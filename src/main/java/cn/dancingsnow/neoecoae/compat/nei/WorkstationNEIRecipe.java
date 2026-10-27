package cn.dancingsnow.neoecoae.compat.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

/** A view of the installed machine recipe, including amounts and fluid-only outputs. */
final class WorkstationNEIRecipe {

    final List<SizedIngredient> items;
    final SizedFluidIngredient fluid;
    final ItemStack output;
    final FluidStack fluidOutput;
    final int energy;
    final int ticks;
    final int coolant;
    final int maxOverclock;

    WorkstationNEIRecipe(IntegratedWorkingStationRecipe recipe) {
        items = recipe.inputItems();
        fluid = recipe.inputFluid();
        output = recipe.itemOutput();
        fluidOutput = recipe.fluidOutput();
        energy = recipe.energy();
        ticks = recipe.processingTicks();
        coolant = 0;
        maxOverclock = 0;
    }

    WorkstationNEIRecipe(CoolingRecipe recipe) {
        items = Collections.emptyList();
        fluid = recipe.input();
        output = null;
        fluidOutput = recipe.output();
        energy = 0;
        ticks = 0;
        coolant = recipe.coolant();
        maxOverclock = recipe.maxOverclock();
    }

    List<List<ItemStack>> itemInputs() {
        List<List<ItemStack>> result = new ArrayList<>();
        for (var input : items) {
            var alternatives = input.ingredient()
                .examples();
            for (var stack : alternatives) stack.stackSize = input.count();
            result.add(alternatives);
        }
        return result;
    }

    boolean uses(ItemStack item, FluidStack containedFluid) {
        for (var input : items) if (input.ingredient()
            .test(item)) return true;
        return containedFluid != null && fluid.ingredient()
            .accepts(containedFluid.getFluid());
    }

    boolean produces(ItemStack item, FluidStack containedFluid) {
        return output != null && item != null
            && output.isItemEqual(item)
            && ItemStack.areItemStackTagsEqual(output, item)
            || fluidOutput != null && containedFluid != null && fluidOutput.isFluidEqual(containedFluid);
    }
}
