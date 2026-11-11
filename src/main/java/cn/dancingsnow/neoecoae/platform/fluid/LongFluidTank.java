package cn.dancingsnow.neoecoae.platform.fluid;

import java.util.function.BooleanSupplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidTankInfo;

/** Exact long inventory with a saturated Forge int view; all native and wrapper mutations notify the host. */
public class LongFluidTank extends FluidTank {

    private final NativeLongTank storage;

    public LongFluidTank(BooleanSupplier expanded) {
        super(new NativeLongTank(expanded));
        storage = (NativeLongTank) nativeTank();
        storage.changed = this::onContentsChanged;
    }

    public long getStoredAmount() {
        return storage.amount;
    }

    public void add(net.minecraftforge.fluids.FluidStack sample, long count) {
        storage.add(sample, count);
    }

    public void remove(long count) {
        storage.remove(count);
    }

    @Override
    public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
        return storage.fill(stack.toNative(), action == IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public FluidStack drain(int amount, IFluidHandler.FluidAction action) {
        return FluidStack.fromNative(storage.drain(amount, action == IFluidHandler.FluidAction.EXECUTE));
    }

    private static final class NativeLongTank extends net.minecraftforge.fluids.FluidTank {

        private final BooleanSupplier expanded;
        private long amount;
        private Runnable changed = () -> {};

        private NativeLongTank(BooleanSupplier expanded) {
            super(16_000);
            this.expanded = expanded;
        }

        @Override
        public int getCapacity() {
            return expanded.getAsBoolean() ? Integer.MAX_VALUE : 16_000;
        }

        @Override
        public int getFluidAmount() {
            return (int) Math.min(Integer.MAX_VALUE, amount);
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(fluid == null ? null : fluid.copy(), getCapacity());
        }

        @Override
        public void setFluid(net.minecraftforge.fluids.FluidStack stack) {
            amount = stack == null ? 0L : Math.max(0, stack.amount);
            fluid = amount == 0 ? null : stack.copy();
            changed.run();
        }

        private void add(net.minecraftforge.fluids.FluidStack sample, long count) {
            if (sample == null || sample.getFluid() == null
                || count <= 0
                || count > Long.MAX_VALUE - amount
                || fluid != null && !fluid.isFluidEqual(sample))
                throw new IllegalArgumentException("Invalid fluid buffer addition");
            fluid = sample.copy();
            amount += count;
            fluid.amount = getFluidAmount();
            changed.run();
        }

        private void remove(long count) {
            if (count < 0 || count > amount) throw new IllegalArgumentException("Invalid fluid buffer removal");
            if (count == 0) return;
            amount -= count;
            if (amount == 0) fluid = null;
            else fluid.amount = getFluidAmount();
            changed.run();
        }

        @Override
        public int fill(net.minecraftforge.fluids.FluidStack resource, boolean execute) {
            if (resource == null || resource.amount <= 0
                || resource.getFluid() == null
                || fluid != null && !fluid.isFluidEqual(resource)) return 0;
            long capacity = expanded.getAsBoolean() ? Long.MAX_VALUE : 16_000L;
            int accepted = (int) Math.min(resource.amount, Math.max(0L, capacity - amount));
            if (execute && accepted > 0) add(resource, accepted);
            return accepted;
        }

        @Override
        public net.minecraftforge.fluids.FluidStack drain(int maximum, boolean execute) {
            int drained = (int) Math.min(Math.max(0, maximum), amount);
            if (drained == 0) return null;
            var result = fluid.copy();
            result.amount = drained;
            if (execute) remove(drained);
            return result;
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            super.writeToNBT(tag);
            tag.setLong("StoredAmount", amount);
            return tag;
        }

        @Override
        public net.minecraftforge.fluids.FluidTank readFromNBT(NBTTagCompound tag) {
            super.readFromNBT(tag);
            amount = fluid == null ? 0L
                : tag.hasKey("StoredAmount", 4) ? Math.max(0L, tag.getLong("StoredAmount")) : Math.max(0, fluid.amount);
            if (amount == 0) fluid = null;
            else fluid.amount = getFluidAmount();
            return this;
        }
    }
}
