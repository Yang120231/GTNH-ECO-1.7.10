package cn.dancingsnow.neoecoae.crafting.cooling;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public final class ECOCoolingRecipe {

    private final Fluid input;
    private final int inputAmount;
    private final FluidStack output;
    private final int coolant;
    private final int maxOverclock;

    ECOCoolingRecipe(Fluid input, int inputAmount, FluidStack output, int coolant, int maxOverclock) {
        this.input = input;
        this.inputAmount = Math.max(1, inputAmount);
        this.output = output == null ? null : output.copy();
        this.coolant = Math.max(0, coolant);
        this.maxOverclock = Math.max(0, maxOverclock);
    }

    public boolean matches(FluidStack inputStack, FluidStack outputStack) {
        if (inputStack == null || inputStack.getFluid() != this.input || inputStack.amount < this.inputAmount) {
            return false;
        }
        return this.output == null || outputStack == null || outputStack.isFluidEqual(this.output);
    }

    public int getInputAmount() {
        return this.inputAmount;
    }

    public Fluid getInputFluid() {
        return this.input;
    }

    public FluidStack getOutput() {
        return this.output == null ? null : this.output.copy();
    }

    public int getCoolant() {
        return this.coolant;
    }

    public int getMaxOverclock() {
        return this.maxOverclock;
    }
}
