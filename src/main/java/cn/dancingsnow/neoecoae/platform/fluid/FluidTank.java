package cn.dancingsnow.neoecoae.platform.fluid;

/** Native Forge tank with the source cooling transaction vocabulary. */
public class FluidTank {

    private final net.minecraftforge.fluids.FluidTank tank;

    public FluidTank(int capacity) {
        this(new net.minecraftforge.fluids.FluidTank(capacity));
    }

    protected FluidTank(net.minecraftforge.fluids.FluidTank tank) {
        this.tank = java.util.Objects.requireNonNull(tank);
    }

    public net.minecraftforge.fluids.FluidTank nativeTank() {
        return tank;
    }

    public FluidStack getFluid() {
        return FluidStack.fromNative(tank.getFluid());
    }

    public int getFluidAmount() {
        return tank.getFluidAmount();
    }

    public int getCapacity() {
        return tank.getCapacity();
    }

    public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
        int accepted = tank.fill(stack.toNative(), action == IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0 && action == IFluidHandler.FluidAction.EXECUTE) onContentsChanged();
        return accepted;
    }

    public FluidStack drain(int amount, IFluidHandler.FluidAction action) {
        var result = FluidStack.fromNative(tank.drain(amount, action == IFluidHandler.FluidAction.EXECUTE));
        if (!result.isEmpty() && action == IFluidHandler.FluidAction.EXECUTE) onContentsChanged();
        return result;
    }

    protected void onContentsChanged() {}

    public void writeToNBT(cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider provider,
        cn.dancingsnow.neoecoae.platform.nbt.CompoundTag tag) {
        tank.writeToNBT(tag.nativeTag());
    }

    public void readFromNBT(cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider provider,
        cn.dancingsnow.neoecoae.platform.nbt.CompoundTag tag) {
        tank.readFromNBT(tag.nativeTag());
    }
}
