package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.InventoryAdaptor;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IExternalPowerSink;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.ae2.grid.StoredEnergyAmount;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.inventory.InternalInventoryHost;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

/** Source single-workstation processing, with native AE2 power, inventory and Forge fluid surfaces. */
public class ECOIntegratedWorkingStationBlockEntity extends NativeNetworkBlockEntity implements IGridTickable,
    IExternalPowerSink, ISidedInventory, IFluidHandler, InternalInventoryHost, IAEAppEngInventory, ManagedHolder {

    protected static final int MAX_INPUT_SLOTS = 9;
    protected static final int MAX_PROCESSING_STEPS = 200;
    protected static final int MAX_POWER_STORAGE = 16_000_000;
    protected static final int MAX_TANK_CAPACITY = 64_000;
    private static final int[] AUTOMATION_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };

    @Persisted
    protected final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, MAX_INPUT_SLOTS, 64);
    @Persisted
    protected final AppEngInternalInventory outputInv = new AppEngInternalInventory(this, 1, 64);
    @Persisted
    @DescSynced
    private final StoredEnergyAmount energyStored = new StoredEnergyAmount(0, MAX_POWER_STORAGE, type -> {});
    private final UpgradeInventory upgrades = new UpgradeInventory(this, 4) {

        @Override
        public int getMaxInstalled(Upgrades upgrade) {
            return upgrade == Upgrades.SPEED ? 4 : 0;
        }
    };
    protected final FluidTank inputTank = new FluidTank(MAX_TANK_CAPACITY);
    protected final FluidTank outputTank = new FluidTank(MAX_TANK_CAPACITY);
    @Persisted
    @DescSynced
    private boolean shouldAutoExport;
    @Persisted
    @DescSynced
    private boolean working;
    @Persisted
    @DescSynced
    private int processingTime;
    private final EnumSet<RelativeSide> allowOutputs = EnumSet.allOf(RelativeSide.class);
    private WorkstationRecipes.Match cachedTask;
    private cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation taskId;
    private boolean dirty = true;

    public ECOIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().addService(IGridTickable.class, this);
        getMainNode().setIdlePowerUsage(0);
    }

    @Override
    public void onReady() {
        super.onReady();
        onGridConnectableSidesChanged();
        onChangeTank();
    }

    public AppEngInternalInventory getInputInv() {
        return inputInv;
    }

    public AppEngInternalInventory getOutputInv() {
        return outputInv;
    }

    public UpgradeInventory getUpgrades() {
        return upgrades;
    }

    public FluidTank getInputTank() {
        return inputTank;
    }

    public FluidTank getOutputTank() {
        return outputTank;
    }

    public IFluidHandler getFluidCombined() {
        return this;
    }

    public boolean isWorking() {
        return working;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(int value) {
        processingTime = Math.max(0, value);
    }

    public boolean isAutoExport() {
        return shouldAutoExport;
    }

    public int getMaxProcessingTime() {
        var task = getTask();
        return task == null ? MAX_PROCESSING_STEPS
            : (int) Math.min(Integer.MAX_VALUE, (long) task.processingTicks() * 2);
    }

    public void setWorking(boolean value) {
        if (working != value) {
            working = value;
            if (getBlockState().hasProperty(cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.WORKING))
                nativeBlockState(
                    getBlockState()
                        .setValue(cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.WORKING, value));
            markForClientUpdate();
            markDirty();
        }
    }

    public void setAutoExport(boolean value) {
        shouldAutoExport = value;
        changed();
    }

    public boolean isOutputAllowed(RelativeSide side) {
        return allowOutputs.contains(side);
    }

    public void toggleOutputSide(RelativeSide side) {
        if (!allowOutputs.remove(side)) allowOutputs.add(side);
        changed();
    }

    public Direction getOutputDirection(RelativeSide side) {
        Direction front = Direction.fromNative(getForward());
        switch (side) {
            case FRONT:
                return front;
            case BACK:
                return front.getOpposite();
            case TOP:
                return Direction.UP;
            case BOTTOM:
                return Direction.DOWN;
            case LEFT:
                return front.getCounterClockWise();
            case RIGHT:
                return front.getClockWise();
            default:
                throw new IllegalArgumentException("Invalid workstation output side");
        }
    }

    public void clearFluid() {
        getInputTank().setFluid(null);
        onChangeTank();
    }

    public void clearFluidOut() {
        getOutputTank().setFluid(null);
        onChangeTank();
    }

    public IntegratedWorkingStationRecipe getTask() {
        if (dirty) {
            var next = WorkstationRecipes.find(inputItems(), getInputTank().getFluid());
            if (next == null || (taskId != null && !taskId.equals(next.id()))) processingTime = 0;
            cachedTask = next;
            taskId = next == null ? null : next.id();
            dirty = false;
        }
        return cachedTask == null ? null : cachedTask.recipe();
    }

    protected List<ItemStack> inputItems() {
        List<ItemStack> result = new ArrayList<>(inputInv.size());
        for (int i = 0; i < inputInv.size(); i++) result.add(inputInv.getStackInSlot(i));
        return result;
    }

    protected boolean outputsFit(IntegratedWorkingStationRecipe task) {
        if (task.hasItemOutput() && !ItemStacks.isEmpty(outputInv.insertItem(0, task.itemOutput(), true))) return false;
        FluidStack fluid = task.fluidOutput();
        return fluid == null || getOutputTank().fill(fluid, false) == fluid.amount;
    }

    private boolean hasAutoExportWork() {
        return shouldAutoExport
            && (!ItemStacks.isEmpty(outputInv.getStackInSlot(0)) || getOutputTank().getFluidAmount() > 0);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, getTask() == null && !hasAutoExportWork(), false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        var task = getTask();
        if (task != null && outputsFit(task)) {
            setWorking(true);
            getMainNode().ifPresent(grid -> {
                final int speed = speedFactor(upgrades.getInstalledUpgrades(Upgrades.SPEED));
                final int maxTime = getMaxProcessingTime();
                final int remaining = maxTime - processingTime;
                final float powerRatio = remaining < speed ? (float) remaining / speed : 1;
                final int requiredTicks = (int) Math.ceil((float) maxTime / speed);
                final int power = (int) Math.floor(((float) task.energy() / requiredTicks) * powerRatio);
                final double threshold = power - 0.01;
                IEnergySource source = this;
                double available = extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (available <= threshold) {
                    double network = grid.getEnergyService()
                        .extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                    if (network >= available) {
                        available = network;
                        source = grid.getEnergyService();
                    }
                }
                if (available > threshold) {
                    source.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    processingTime += speed;
                } else if (available != 0 && power > 0) {
                    double ratio = source == this ? available / power
                        : (available - 10 * grid.getEnergyService()
                            .getIdlePowerUsage()) / power;
                    int factor = (int) Math.floor(ratio * speed);
                    if (factor > 1) {
                        double paid = source.extractAEPower(
                            (double) power * factor / speed,
                            Actionable.MODULATE,
                            PowerMultiplier.CONFIG);
                        processingTime += (int) Math.floor(paid / power * speed);
                    }
                }
            });
            if (processingTime >= getMaxProcessingTime()) finishTask(task);
        } else {
            setWorking(false);
        }
        if (working || processingTime > 0) changed();
        if (pushOutResult()) return TickRateModulation.URGENT;
        return getTask() != null ? TickRateModulation.URGENT
            : hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    public static int speedFactor(int cards) {
        switch (cards) {
            case 1:
                return 3;
            case 2:
                return 5;
            case 3:
                return 10;
            case 4:
                return 50;
            default:
                return 2;
        }
    }

    protected void finishTask(IntegratedWorkingStationRecipe task) {
        if (!task.matches(new IntegratedWorkingStationRecipe.Input(inputItems(), getInputTank().getFluid()), worldObj)
            || !outputsFit(task)) return;
        if (task.hasItemOutput()) outputInv.insertItem(0, task.itemOutput(), false);
        if (task.hasFluidOutput()) getOutputTank().fill(task.fluidOutput(), true);
        for (var required : task.inputItems()) {
            int remaining = required.count();
            for (int slot = 0; slot < inputInv.size() && remaining > 0; slot++) {
                ItemStack stack = inputInv.getStackInSlot(slot);
                if (!ItemStacks.isEmpty(stack) && required.ingredient()
                    .test(stack)) {
                    int consumed = Math.min(remaining, stack.stackSize);
                    inputInv.extractItem(slot, consumed, false);
                    remaining -= consumed;
                }
            }
        }
        if (!task.inputFluid()
            .ingredient()
            .isEmpty())
            getInputTank().drain(
                task.inputFluid()
                    .amount(),
                true);
        processingTime = 0;
        cachedTask = null;
        dirty = true;
        taskId = null;
        setWorking(false);
        changed();
    }

    protected boolean pushOutResult() {
        if (!hasAutoExportWork() || worldObj == null) return false;
        for (RelativeSide side : allowOutputs) {
            ForgeDirection direction = getOutputDirection(side).nativeSide();
            int x = xCoord + direction.offsetX, y = yCoord + direction.offsetY, z = zCoord + direction.offsetZ;
            if (!worldObj.blockExists(x, y, z)) continue;
            TileEntity target = worldObj.getTileEntity(x, y, z);
            if (target == null) continue;
            boolean moved = false;
            ItemStack output = outputInv.getStackInSlot(0);
            InventoryAdaptor items = InventoryAdaptor.getAdaptor(target, direction.getOpposite());
            if (items != null && !ItemStacks.isEmpty(output)) {
                ItemStack remainder = items.addItems(output.copy());
                int inserted = output.stackSize - ItemStacks.count(remainder);
                if (inserted > 0) {
                    outputInv.extractItem(0, inserted, false);
                    moved = true;
                }
            }
            FluidStack fluid = getOutputTank().getFluid();
            if (target instanceof IFluidHandler && fluid != null && fluid.amount > 0) {
                int inserted = ((IFluidHandler) target).fill(direction.getOpposite(), fluid.copy(), true);
                if (inserted > 0) {
                    getOutputTank().drain(inserted, true);
                    onChangeTank();
                    moved = true;
                }
            }
            if (moved) return true;
        }
        return false;
    }

    protected void changed() {
        markDirty();
        markForClientUpdate();
        if (getMainNode().isReady()) getMainNode().ifPresent(
            (grid, node) -> grid.getTickManager()
                .wakeDevice(node));
    }

    public void onChangeTank() {
        dirty = true;
        changed();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        dirty = true;
        changed();
    }

    @Override
    public void onChangeInventory(IInventory inventory, int slot, InvOperation operation, ItemStack removed,
        ItemStack added) {
        changed();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inputTank.readFromNBT(
            data.getCompound("inputTank")
                .nativeTag());
        outputTank.readFromNBT(
            data.getCompound("outputTank")
                .nativeTag());
        upgrades.readFromNBT(data.nativeTag(), "upgrades");
        if (data.contains("outputSides")) {
            allowOutputs.clear();
            int flags = data.getInt("outputSides");
            for (RelativeSide side : RelativeSide.values())
                if ((flags & (1 << side.ordinal())) != 0) allowOutputs.add(side);
        }
        dirty = true;
        cachedTask = null;
        taskId = data.contains("taskId", 8)
            ? cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse(data.getString("taskId"))
            : null;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.put("inputTank", new CompoundTag(inputTank.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        data.put("outputTank", new CompoundTag(outputTank.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        upgrades.writeToNBT(data.nativeTag(), "upgrades");
        int flags = 0;
        for (RelativeSide side : allowOutputs) flags |= 1 << side.ordinal();
        data.putInt("outputSides", flags);
        if (taskId != null) data.putString("taskId", taskId.toString());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put(getSyncTag(), serializeInitialData(registries));
        return tag;
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        double inserted = energyStored.insert(amount, mode == Actionable.MODULATE);
        if (mode == Actionable.MODULATE && inserted > 0) changed();
        return amount - inserted;
    }

    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        double extracted = energyStored.extract(multiplier.multiply(amount), mode == Actionable.MODULATE);
        if (mode == Actionable.MODULATE && extracted > 0) changed();
        return multiplier.divide(extracted);
    }

    @Override
    public double getAEMaxPower() {
        return MAX_POWER_STORAGE;
    }

    @Override
    public double getAECurrentPower() {
        return energyStored.getAmount();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return false;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public void notifyPersistence() {
        markDirty();
    }

    @Override
    public double injectExternalPower(PowerUnits unit, double amount, Actionable mode) {
        return PowerUnits.AE.convertTo(unit, injectAEPower(unit.convertTo(PowerUnits.AE, amount), mode));
    }

    @Override
    public double getExternalPowerDemand(PowerUnits unit, double maximum) {
        return Math.min(maximum, PowerUnits.AE.convertTo(unit, MAX_POWER_STORAGE - energyStored.getAmount()));
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return AECableType.COVERED;
    }

    @Override
    public int getSizeInventory() {
        return 10;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 9 ? outputInv.getStackInSlot(0) : inputInv.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return slot == 9 ? outputInv.extractItem(0, amount, false) : inputInv.extractItem(slot, amount, false);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return decrStackSize(slot, Integer.MAX_VALUE);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot == 9) outputInv.setStackInSlot(0, stack);
        else inputInv.setStackInSlot(slot, stack);
    }

    @Override
    public String getInventoryName() {
        return "container.neoecoae.integrated_working_station";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < 9;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return AUTOMATION_SLOTS.clone();
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot == 9;
    }

    @Override
    public int fill(ForgeDirection side, FluidStack fluid, boolean execute) {
        int accepted = getInputTank().fill(fluid, execute);
        if (execute && accepted > 0) onChangeTank();
        return accepted;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, boolean execute) {
        if (fluid == null || !fluid.isFluidEqual(getOutputTank().getFluid())) return null;
        return drain(side, fluid.amount, execute);
    }

    @Override
    public FluidStack drain(ForgeDirection side, int amount, boolean execute) {
        FluidStack drained = getOutputTank().drain(amount, execute);
        if (execute && drained != null && drained.amount > 0) onChangeTank();
        return drained;
    }

    @Override
    public boolean canFill(ForgeDirection side, Fluid fluid) {
        return fluid != null;
    }

    @Override
    public boolean canDrain(ForgeDirection side, Fluid fluid) {
        FluidStack output = getOutputTank().getFluid();
        return output != null && (fluid == null || output.getFluid() == fluid);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { getInputTank().getInfo(), getOutputTank().getInfo() };
    }
}
