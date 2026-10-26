package cn.dancingsnow.neoecoae.recipe;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;

/** Source coolant matching and capacity, with a nullable native empty output. */
@com.github.bsideup.jabel.Desugar
public record CoolingRecipe(SizedFluidIngredient input, FluidStack output, int coolant, int maxOverclock) {

    public CoolingRecipe {
        java.util.Objects.requireNonNull(input);
        output = output == null ? null : output.copy();
    }

    public boolean matches(Input value, World level) {
        return input.test(value.input()) && (value.output() == null || value.output().amount <= 0
            || output != null && output.getFluid() == value.output()
                .getFluid());
    }

    public FluidStack output() {
        return output == null ? null : output.copy();
    }

    public int inputAmount() {
        return input.amount();
    }

    public int outputAmount() {
        return output == null ? 0 : output.amount;
    }

    @com.github.bsideup.jabel.Desugar
    public record Input(FluidStack input, FluidStack output) {}
}
