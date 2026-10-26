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
        var provided = recipeInput.inputs();
        int[] remaining = new int[provided.size()];
        for (int i = 0; i < provided.size(); i++) {
            var stack = provided.get(i);
            remaining[i] = stack == null ? 0 : stack.stackSize;
        }
        for (var required : inputItems) {
            int needed = required.count();
            for (int i = 0; i < provided.size() && needed > 0; i++) {
                var stack = provided.get(i);
                if (stack == null || stack.stackSize <= 0 || remaining[i] <= 0) continue;
                if (required.ingredient()
                    .test(stack)) {
                    int take = Math.min(remaining[i], needed);
                    remaining[i] -= take;
                    needed -= take;
                }
            }
            if (needed > 0) return false;
        }
        if (inputFluid.ingredient()
            .isEmpty()) return true;
        return inputFluid.test(recipeInput.fluid());
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
