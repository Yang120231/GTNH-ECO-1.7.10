package cn.dancingsnow.neoecoae.platform.recipe;

import net.minecraftforge.fluids.FluidStack;

/** Keeps millibucket quantity separate from the source fluid predicate. */
@com.github.bsideup.jabel.Desugar
public record SizedFluidIngredient(SourceFluidIngredient ingredient, int amount) {

    public SizedFluidIngredient {
        java.util.Objects.requireNonNull(ingredient);
        if (amount <= 0) throw new IllegalArgumentException("Non-positive fluid input amount");
    }

    public boolean test(FluidStack stack) {
        return ingredient.test(stack) && stack.amount >= amount;
    }
}
