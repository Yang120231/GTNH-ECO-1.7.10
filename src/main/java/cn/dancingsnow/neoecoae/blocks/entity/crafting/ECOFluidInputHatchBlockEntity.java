package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.fluid.FluidStack;
import cn.dancingsnow.neoecoae.platform.fluid.FluidTank;
import cn.dancingsnow.neoecoae.platform.fluid.IFluidHandler;
import cn.dancingsnow.neoecoae.platform.fluid.LongFluidTank;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;

public class ECOFluidInputHatchBlockEntity extends NEBlockEntity<NECraftingCluster, ECOFluidInputHatchBlockEntity>
    implements net.minecraftforge.fluids.IFluidHandler, IAEAppEngInventory {

    public FluidTank tank = new LongFluidTank(this::hasNetworkPullCard) {

        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    private final AppEngInternalInventory upgrades = new AppEngInternalInventory(this, 1, 1) {

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return stack != null && stack.getItem() == NativeContent.item("network_pull_card");
        }
    };
    private FluidStack markedFluid = FluidStack.EMPTY;
    private long targetAmount = 16_000L;

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, NECraftingClusterCalculator::new);
    }

    public AppEngInternalInventory getUpgrades() {
        return upgrades;
    }

    public boolean hasNetworkPullCard() {
        if (upgrades == null) return false;
        var card = upgrades.getStackInSlot(0);
        return card != null && card.stackSize > 0 && card.getItem() == NativeContent.item("network_pull_card");
    }

    public long getStoredFluidAmount() {
        return tank instanceof LongFluidTank large ? large.getStoredAmount() : tank.getFluidAmount();
    }

    public FluidStack getMarkedFluid() {
        return markedFluid.copy();
    }

    public void setMarkedFluid(FluidStack fluid) {
        markedFluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        setChanged();
        markForUpdate();
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(long amount) {
        if (amount < 0) return;
        targetAmount = amount;
        setChanged();
        markForUpdate();
    }

    @Override
    public void onChangeInventory(IInventory inventory, int slot, InvOperation operation, ItemStack removed,
        ItemStack added) {
        setChanged();
        markForUpdate();
    }

    @Override
    public void getDrops(World world, int x, int y, int z, java.util.List<ItemStack> drops) {
        super.getDrops(world, x, y, z, drops);
        var card = upgrades.getStackInSlot(0);
        if (card != null) drops.add(card.copy());
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.setInventorySlotContents(0, null);
        tank.nativeTank()
            .setFluid(null);
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.TICK)
    public void nativeTick() {
        if (worldObj != null && !worldObj.isRemote) tick(getLevel(), getBlockPos(), getBlockState());
    }

    public void tick(WorldView level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        if (hasNetworkPullCard()) {
            if (level.getGameTime() % 20 == 0 && getMainNode().isOnline() && tank instanceof LongFluidTank large) {
                var grid = getMainNode().getGrid();
                if (grid != null) FluidHatchNetworkPull.sync(
                    large,
                    AEFluidKey.of(markedFluid),
                    targetAmount,
                    grid.getStorageService()
                        .getInventory(),
                    grid.getEnergyService(),
                    IActionSource.ofMachine(this));
            }
            return;
        }
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
            int accepted = tank.fill(FluidStack.fromNative(probe), IFluidHandler.FluidAction.SIMULATE);
            if (accepted > 0) {
                var requested = probe.copy();
                requested.amount = Math.min(accepted, probe.amount);
                var taken = other.drain(
                    face.getOpposite()
                        .nativeSide(),
                    requested,
                    true);
                tank.fill(FluidStack.fromNative(taken), IFluidHandler.FluidAction.EXECUTE);
                return;
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
        upgrades.writeToNBT(data.nativeTag(), "upgrades");
        var selected = new net.minecraft.nbt.NBTTagCompound();
        if (!markedFluid.isEmpty()) markedFluid.toNative()
            .writeToNBT(selected);
        data.put("markedFluid", new CompoundTag(selected));
        data.putLong("targetAmount", targetAmount);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
        upgrades.readFromNBT(data.nativeTag(), "upgrades");
        markedFluid = FluidStack
            .fromNative(
                net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(
                    data.getCompound("markedFluid")
                        .nativeTag()))
            .copyWithAmount(1);
        targetAmount = data.contains("targetAmount") ? Math.max(0L, data.getLong("targetAmount")) : 16_000L;
    }

    public int fill(net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.fluids.FluidStack resource,
        boolean execute) {
        return tank.fill(
            FluidStack.fromNative(resource),
            execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
    }

    public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.common.util.ForgeDirection side, int amount,
        boolean execute) {
        return tank.drain(amount, execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE)
            .toNative();
    }

    public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.common.util.ForgeDirection side,
        net.minecraftforge.fluids.FluidStack fluid, boolean execute) {
        var stored = tank.nativeTank()
            .getFluid();
        return fluid != null && stored != null && stored.isFluidEqual(fluid) ? drain(side, fluid.amount, execute)
            : null;
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
