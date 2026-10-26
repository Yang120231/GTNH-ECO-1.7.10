package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.platform.fluid.FluidTank;
import cn.dancingsnow.neoecoae.platform.fluid.IFluidHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

public class ECOFluidInputHatchBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOFluidInputHatchBlockEntity>
    implements net.minecraftforge.fluids.IFluidHandler {

    public FluidTank tank = new FluidTank(16000) {

        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.TICK)
    public void nativeTick() {
        if (worldObj != null && !worldObj.isRemote) tick(getLevel(), getBlockPos(), getBlockState());
    }

    public void tick(WorldView level, BlockPos pos, BlockState state) {
        for (Direction face : Direction.values()) {
            if (!level.hasChunkAt(pos.relative(face))) continue;
            var tile = level.getBlockEntity(pos.relative(face));
            if (!(tile instanceof net.minecraftforge.fluids.IFluidHandler)) continue;
            var other = (net.minecraftforge.fluids.IFluidHandler) tile;
            var probe = other.drain(
                face.getOpposite()
                    .nativeSide(),
                tank.getCapacity(),
                false);
            if (probe == null) continue;
            int accepted = tank.fill(
                cn.dancingsnow.neoecoae.platform.fluid.FluidStack.fromNative(probe),
                IFluidHandler.FluidAction.SIMULATE);
            if (accepted > 0) {
                var taken = other.drain(
                    face.getOpposite()
                        .nativeSide(),
                    accepted,
                    true);
                tank.fill(
                    cn.dancingsnow.neoecoae.platform.fluid.FluidStack.fromNative(taken),
                    IFluidHandler.FluidAction.EXECUTE);
                return;
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
    }

    public int fill(net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.fluids.FluidStack resource,
        boolean execute) {
        return tank.fill(
            cn.dancingsnow.neoecoae.platform.fluid.FluidStack.fromNative(resource),
            execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
    }

    public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.common.util.ForgeDirection side, int amount,
        boolean execute) {
        return tank.drain(amount, execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE)
            .toNative();
    }

    public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.common.util.ForgeDirection side,
        net.minecraftforge.fluids.FluidStack fluid, boolean execute) {
        return fluid != null && tank.getFluid()
            .getFluid() == fluid.getFluid() ? drain(side, fluid.amount, execute) : null;
    }

    public boolean canFill(net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.fluids.Fluid fluid) {
        return true;
    }

    public boolean canDrain(net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.fluids.Fluid fluid) {
        return true;
    }

    public net.minecraftforge.fluids.FluidTankInfo[] getTankInfo(net.minecraftforge.common.util.ForgeDirection side) {
        return new net.minecraftforge.fluids.FluidTankInfo[] { tank.nativeTank()
            .getInfo() };
    }
}
