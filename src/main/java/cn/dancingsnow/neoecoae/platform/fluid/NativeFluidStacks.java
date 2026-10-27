package cn.dancingsnow.neoecoae.platform.fluid;

import net.minecraftforge.fluids.FluidStack;

/** Null-safe translation of modern empty-fluid and copy semantics to real Forge fluid stacks. */
public final class NativeFluidStacks {
    private NativeFluidStacks() {}
    public static boolean isEmpty(FluidStack value) { return value == null || value.getFluid() == null || value.amount <= 0; }
    public static int amount(FluidStack value) { return isEmpty(value) ? 0 : value.amount; }
    public static FluidStack copy(FluidStack value, int amount) {
        if (isEmpty(value) || amount <= 0) return null;
        FluidStack result = value.copy(); result.amount = amount; return result;
    }
    public static boolean equal(FluidStack first, FluidStack second) {
        return isEmpty(first) ? isEmpty(second) : !isEmpty(second) && first.isFluidEqual(second);
    }
}
