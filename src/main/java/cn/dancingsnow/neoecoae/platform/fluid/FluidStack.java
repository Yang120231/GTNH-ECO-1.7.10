package cn.dancingsnow.neoecoae.platform.fluid;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;

/** Modern fluid identity and amount, materialized as a native Forge stack only at an inventory boundary. */
public final class FluidStack {

    public static final FluidStack EMPTY = new FluidStack(null, 0);
    private final Fluid fluid;
    private int amount;
    private NBTTagCompound data;

    public FluidStack(Fluid fluid, int amount) {
        this.fluid = fluid;
        this.amount = Math.max(0, amount);
    }

    public Fluid getFluid() {
        return fluid;
    }

    public int getAmount() {
        return isEmpty() ? 0 : amount;
    }

    public boolean isEmpty() {
        return fluid == null || amount <= 0;
    }

    public boolean is(Fluid other) {
        return !isEmpty() && fluid == other;
    }

    public void setAmount(int amount) {
        if (this == EMPTY) throw new UnsupportedOperationException("Cannot mutate the shared empty fluid");
        this.amount = Math.max(0, amount);
    }

    public void grow(int amount) {
        setAmount(Math.addExact(getAmount(), amount));
    }

    public void shrink(int amount) {
        setAmount(Math.max(0, getAmount() - amount));
    }

    public NBTTagCompound copyTag() {
        return data == null ? null : (NBTTagCompound) data.copy();
    }

    public void setTag(NBTTagCompound data) {
        if (this == EMPTY) throw new UnsupportedOperationException("Cannot mutate the shared empty fluid");
        this.data = data == null ? null : (NBTTagCompound) data.copy();
    }

    public FluidStack copy() {
        return copyWithAmount(getAmount());
    }

    public FluidStack copyWithAmount(int amount) {
        if (fluid == null || amount <= 0) return EMPTY;
        FluidStack result = new FluidStack(fluid, amount);
        result.setTag(data);
        return result;
    }

    public net.minecraftforge.fluids.FluidStack toNative() {
        if (isEmpty()) return null;
        var stack = new net.minecraftforge.fluids.FluidStack(fluid, amount);
        stack.tag = copyTag();
        return stack;
    }

    public static FluidStack fromNative(net.minecraftforge.fluids.FluidStack stack) {
        if (stack == null || stack.amount <= 0) return EMPTY;
        FluidStack result = new FluidStack(stack.getFluid(), stack.amount);
        result.setTag(stack.tag);
        return result;
    }

    public static boolean isSameFluidSameComponents(FluidStack a, FluidStack b) {
        return a.fluid == b.fluid && Objects.equals(a.data, b.data);
    }
}
