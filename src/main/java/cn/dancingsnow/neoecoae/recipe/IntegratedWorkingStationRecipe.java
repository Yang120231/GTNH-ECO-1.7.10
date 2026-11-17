package cn.dancingsnow.neoecoae.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;

/** Source workstation contract adapted to native 1.7.10 stack and fluid APIs. */
@com.github.bsideup.jabel.Desugar
public record IntegratedWorkingStationRecipe(List<SizedIngredient> inputItems, SizedFluidIngredient inputFluid,
    ItemStack itemOutput, FluidStack fluidOutput, int energy, int processingTicks) {

    public IntegratedWorkingStationRecipe(List<SizedIngredient> inputItems, SizedFluidIngredient inputFluid,
        ItemStack itemOutput, FluidStack fluidOutput, int energy) {
        this(inputItems, inputFluid, itemOutput, fluidOutput, energy, 100);
    }

    public IntegratedWorkingStationRecipe {
        if (processingTicks <= 0) throw new IllegalArgumentException("Processing time must be positive");
        if (energy < 0) throw new IllegalArgumentException("Negative recipe energy");
        inputItems = Collections.unmodifiableList(new ArrayList<>(inputItems));
        java.util.Objects.requireNonNull(inputFluid);
        itemOutput = itemOutput == null ? null : itemOutput.copy();
        fluidOutput = fluidOutput == null ? null : fluidOutput.copy();
    }

    public boolean matches(Input recipeInput, World level) {
        return allocateInputs(recipeInput) != null;
    }

    public long[] allocateInputs(Input recipeInput) {
        if (!inputFluid.ingredient()
            .isEmpty() && !inputFluid.test(recipeInput.fluid())) return null;

        long[] supplied = new long[recipeInput.size()];
        long[] needed = new long[inputItems.size()];
        for (int ingredient = 0; ingredient < needed.length; ingredient++) {
            needed[ingredient] = inputItems.get(ingredient)
                .count();
        }
        for (int slot = 0; slot < supplied.length; slot++) {
            ItemStack stack = recipeInput.getItem(slot);
            if (stack != null && stack.stackSize > 0) supplied[slot] = stack.stackSize;
        }
        if (needed.length == 0) return new long[supplied.length];
        boolean[][] accepts = new boolean[supplied.length][];
        for (int slot = 0; slot < supplied.length; slot++) {
            if (supplied[slot] == 0) continue;
            ItemStack stack = recipeInput.getItem(slot);
            accepts[slot] = new boolean[needed.length];
            for (int ingredient = 0; ingredient < needed.length; ingredient++) {
                accepts[slot][ingredient] = inputItems.get(ingredient)
                    .ingredient()
                    .test(stack);
            }
        }
        return LargeWorkstationRecipe.allocateQuantities(supplied, needed, accepts, false);
    }

    public ItemStack assemble(Input input) {
        return itemOutput();
    }

    public ItemStack itemOutput() {
        return itemOutput == null ? null : itemOutput.copy();
    }

    public FluidStack fluidOutput() {
        return fluidOutput == null ? null : fluidOutput.copy();
    }

    public boolean hasItemOutput() {
        return itemOutput != null && itemOutput.stackSize > 0;
    }

    public boolean hasFluidOutput() {
        return fluidOutput != null && fluidOutput.amount > 0;
    }

    @com.github.bsideup.jabel.Desugar
    public record Input(List<ItemStack> inputs, FluidStack fluid) {

        public ItemStack getItem(int index) {
            return inputs.get(index);
        }

        public int size() {
            return inputs.size();
        }

        public boolean isEmpty() {
            for (ItemStack input : inputs) if (input != null && input.stackSize > 0) return false;
            return fluid == null || fluid.amount <= 0;
        }
    }
}
