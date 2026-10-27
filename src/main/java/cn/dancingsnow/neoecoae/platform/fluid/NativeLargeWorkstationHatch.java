package cn.dancingsnow.neoecoae.platform.fluid;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;

/** Forge tank/capability boundary for the source's 1,024,000 mB automatic input and output hatches. */
public abstract class NativeLargeWorkstationHatch extends NEBlockEntity<NEIntegratedWorkingStationCluster, NativeLargeWorkstationHatch>
    implements IFluidHandler {
    public static final int CAPACITY = 1_024_000;
    private final boolean input;
    public final FluidTank tank = new FluidTank(CAPACITY) {
        @Override public int fill(FluidStack fluid, boolean execute) {
            if (input && !coolant(fluid)) return 0;
            int amount = super.fill(fluid, execute); if (execute && amount > 0) changed(); return amount;
        }
        @Override public FluidStack drain(int maximum, boolean execute) {
            var result = super.drain(maximum, execute); if (execute && !NativeFluidStacks.isEmpty(result)) changed(); return result;
        }
        @Override public void setFluid(FluidStack value) { super.setFluid(value); changed(); }
    };

    protected NativeLargeWorkstationHatch(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean input) {
        super(type, pos, state, NEIntegratedWorkingStationClusterCalculator::new); this.input = input;
    }

    private void changed() {
        setChanged(); markForClientUpdate();
        if (cluster != null && cluster.getController() != null) cluster.getController().onChangeTankFromHatch();
    }

    private boolean coolant(FluidStack fluid) {
        if (NativeFluidStacks.isEmpty(fluid)) return false;
        for (var recipe : WorkstationRecipes.cooling().values()) if (recipe.input().ingredient().test(fluid)) return true;
        return false;
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.TICK)
    public void tickFluidTransfer() {
        if (worldObj == null || worldObj.isRemote || level == null) return;
        for (var side : cn.dancingsnow.neoecoae.platform.minecraft.Direction.values()) {
            BlockPos neighbor = getBlockPos().relative(side);
            if (!level.hasChunkAt(neighbor)) continue;
            TileEntity tile = level.getBlockEntity(neighbor);
            if (cluster != null && cluster.containsBlockEntity(tile) || !(tile instanceof IFluidHandler)) continue;
            IFluidHandler handler = (IFluidHandler) tile; ForgeDirection facing = side.getOpposite().nativeSide();
            if (input) {
                FluidStack offered = handler.drain(facing, Math.max(0, CAPACITY - tank.getFluidAmount()), false);
                if (!coolant(offered)) continue;
                int amount = tank.fill(offered, false); if (amount <= 0) continue;
                FluidStack extracted = handler.drain(facing, NativeFluidStacks.copy(offered, amount), true);
                if (NativeFluidStacks.isEmpty(extracted)) continue;
                int inserted = tank.fill(extracted, true);
                if (inserted < extracted.amount) handler.fill(facing, NativeFluidStacks.copy(extracted, extracted.amount - inserted), true);
                if (inserted > 0) return;
            } else {
                FluidStack offered = tank.drain(tank.getFluidAmount(), false); if (NativeFluidStacks.isEmpty(offered)) return;
                int amount = Math.min(offered.amount, handler.fill(facing, offered, false)); if (amount <= 0) continue;
                FluidStack extracted = tank.drain(amount, true); int inserted = handler.fill(facing, extracted, true);
                if (inserted < extracted.amount) tank.fill(NativeFluidStacks.copy(extracted, extracted.amount - inserted), true);
                if (inserted > 0) return;
            }
        }
    }

    @Override public int fill(ForgeDirection side, FluidStack fluid, boolean execute) { return input && coolant(fluid) ? tank.fill(fluid, execute) : 0; }
    @Override public FluidStack drain(ForgeDirection side, FluidStack fluid, boolean execute) { return !input && tank.getFluid() != null && tank.getFluid().isFluidEqual(fluid) ? tank.drain(fluid.amount, execute) : null; }
    @Override public FluidStack drain(ForgeDirection side, int maximum, boolean execute) { return input ? null : tank.drain(maximum, execute); }
    @Override public boolean canFill(ForgeDirection side, Fluid fluid) { return input && fluid != null && coolant(new FluidStack(fluid, 1)); }
    @Override public boolean canDrain(ForgeDirection side, Fluid fluid) { return !input && (fluid == null || tank.getFluid() != null && tank.getFluid().getFluid() == fluid); }
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection side) { return new FluidTankInfo[] { tank.getInfo() }; }
    @Override public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries); data.put("hatchTank", new CompoundTag(tank.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
    }
    @Override public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries); tank.readFromNBT(data.contains("hatchTank") ? data.getCompound("hatchTank").nativeTag() : data.nativeTag());
    }
}
