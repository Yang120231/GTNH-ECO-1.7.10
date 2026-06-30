package cn.dancingsnow.neoecoae.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;

public class TileCraftingHatch extends TileCraftingMember implements IInventory, IFluidHandler {

    private static final String TAG_INPUT = "Input";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_TANK = "Tank";
    private static final int SLOTS = 27;
    private static final int TANK_CAPACITY = 16000;

    private boolean input;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY);
    private final ItemStack[] items = new ItemStack[SLOTS];

    public TileCraftingHatch() {
        this(true);
    }

    public TileCraftingHatch(boolean input) {
        this.input = input;
    }

    public boolean isInputHatch() {
        return this.input;
    }

    public boolean isInput() {
        return this.input;
    }

    public int getOccupiedSlotCount() {
        return this.usedSlots();
    }

    public int getCachedItemCount() {
        int count = 0;
        for (ItemStack stack : this.items) {
            if (stack != null) {
                count = saturatedAdd(count, Math.max(0, stack.stackSize));
            }
        }
        return count;
    }

    public FluidStack getFluidStack() {
        FluidStack stack = this.tank.getFluid();
        return stack == null ? null : stack.copy();
    }

    public int getFluidAmount() {
        return this.tank.getFluidAmount();
    }

    public FluidStack drainInputFluid(FluidStack resource, boolean doDrain) {
        if (!this.input || resource == null || resource.amount <= 0) {
            return null;
        }
        FluidStack current = this.tank.getFluid();
        if (current == null || !current.isFluidEqual(resource)) {
            return null;
        }
        FluidStack drained = this.tank.drain(resource.amount, doDrain);
        if (doDrain && drained != null && drained.amount > 0) {
            this.onFluidChanged();
        }
        return drained;
    }

    public int fillOutputFluid(FluidStack resource, boolean doFill) {
        if (this.input || resource == null || resource.amount <= 0) {
            return 0;
        }
        int filled = this.tank.fill(resource, doFill);
        if (doFill && filled > 0) {
            this.onFluidChanged();
        }
        return filled;
    }

    public int getTankCapacity() {
        return this.tank.getCapacity();
    }

    public int getFluidId() {
        FluidStack stack = this.tank.getFluid();
        return stack == null || stack.getFluid() == null ? -1 : FluidRegistry.getFluidID(stack.getFluid());
    }

    public void setClientFluid(int fluidId, int amount) {
        Fluid fluid = fluidId < 0 ? null : FluidRegistry.getFluid(fluidId);
        if (fluid == null || amount <= 0) {
            this.tank.setFluid(null);
        } else {
            this.tank.setFluid(new FluidStack(fluid, Math.min(amount, this.tank.getCapacity())));
        }
    }

    public boolean handleFluidContainerClick(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        InventoryPlayer inventory = player.inventory;
        ItemStack held = inventory == null ? null : inventory.getItemStack();
        if (held == null || held.stackSize != 1) {
            return false;
        }
        if (this.input && this.drainContainerIntoTank(held, inventory)) {
            this.onFluidChanged();
            return true;
        }
        if (this.fillContainerFromTank(held, inventory)) {
            this.onFluidChanged();
            return true;
        }
        return false;
    }

    public boolean insertOutput(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] == null) {
                this.items[i] = remaining;
                this.onInventoryChanged();
                return true;
            }
            if (this.canMerge(this.items[i], remaining)) {
                int limit = Math.min(this.getInventoryStackLimit(), this.items[i].getMaxStackSize());
                int moved = Math.min(limit - this.items[i].stackSize, remaining.stackSize);
                if (moved > 0) {
                    this.items[i].stackSize += moved;
                    remaining.stackSize -= moved;
                    if (remaining.stackSize <= 0) {
                        this.onInventoryChanged();
                        return true;
                    }
                }
            }
        }
        this.onInventoryChanged();
        return false;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : this.items) {
            if (stack != null) {
                used++;
            }
        }
        return used;
    }

    @Override
    public int getSizeInventory() {
        return this.items.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < this.items.length ? this.items[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < 0 || slot >= this.items.length || this.items[slot] == null || amount <= 0) {
            return null;
        }
        ItemStack removed;
        if (this.items[slot].stackSize <= amount) {
            removed = this.items[slot];
            this.items[slot] = null;
        } else {
            removed = this.items[slot].splitStack(amount);
            if (this.items[slot].stackSize <= 0) {
                this.items[slot] = null;
            }
        }
        this.onInventoryChanged();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= this.items.length) {
            return null;
        }
        ItemStack stack = this.items[slot];
        this.items[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.items.length) {
            return;
        }
        this.items[slot] = stack;
        if (this.items[slot] != null && this.items[slot].stackSize > this.getInventoryStackLimit()) {
            this.items[slot].stackSize = this.getInventoryStackLimit();
        }
        this.onInventoryChanged();
    }

    @Override
    public String getInventoryName() {
        return this.input ? "container.neoecoae.crafting_input_hatch" : "container.neoecoae.crafting_output_hatch";
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
        return this.worldObj != null && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
            && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean(TAG_INPUT, this.input);
        NBTTagCompound tankTag = new NBTTagCompound();
        this.tank.writeToNBT(tankTag);
        tag.setTag(TAG_TANK, tankTag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte(TAG_SLOT, (byte) i);
                NBTTagCompound stackTag = new NBTTagCompound();
                this.items[i].writeToNBT(stackTag);
                entry.setTag(TAG_STACK, stackTag);
                list.appendTag(entry);
            }
        }
        tag.setTag(TAG_ITEMS, list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.input = !tag.hasKey(TAG_INPUT) || tag.getBoolean(TAG_INPUT);
        this.tank.readFromNBT(tag.getCompoundTag(TAG_TANK));
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }
        NBTTagList list = tag.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte(TAG_SLOT) & 255;
            if (slot >= 0 && slot < this.items.length) {
                this.items[slot] = ItemStack.loadItemStackFromNBT(entry.getCompoundTag(TAG_STACK));
            }
        }
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        if (this.input) {
            this.pullFluidFromNeighbors();
        } else {
            this.pushFluidToNeighbors();
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeFluidDescription(tag);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFluidDescription(packet.func_148857_g());
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (!this.input || resource == null) {
            return 0;
        }
        int filled = this.tank.fill(resource, doFill);
        if (doFill && filled > 0) {
            this.onFluidChanged();
        }
        return filled;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        FluidStack current = this.tank.getFluid();
        if (resource == null || current == null || !current.isFluidEqual(resource)) {
            return null;
        }
        return this.drain(from, resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        if (this.input || maxDrain <= 0) {
            return null;
        }
        FluidStack drained = this.tank.drain(maxDrain, doDrain);
        if (doDrain && drained != null && drained.amount > 0) {
            this.onFluidChanged();
        }
        return drained;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return this.input && fluid != null;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return fluid != null && this.tank.getFluid() != null
            && this.tank.getFluid()
                .getFluid() == fluid;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { this.tank.getInfo() };
    }

    private void onInventoryChanged() {
        this.markDirty();
        this.notifyCraftingControllerChanged();
    }

    private void onFluidChanged() {
        this.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
        this.notifyCraftingControllerChanged();
    }

    private void writeFluidDescription(NBTTagCompound tag) {
        tag.setInteger("FluidId", this.getFluidId());
        tag.setInteger("FluidAmount", this.getFluidAmount());
    }

    private void readFluidDescription(NBTTagCompound tag) {
        this.setClientFluid(tag.getInteger("FluidId"), tag.getInteger("FluidAmount"));
    }

    private void pullFluidFromNeighbors() {
        int remaining = this.tank.getCapacity() - this.tank.getFluidAmount();
        if (remaining <= 0) {
            return;
        }
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity neighbor = this.worldObj.getTileEntity(
                this.xCoord + direction.offsetX,
                this.yCoord + direction.offsetY,
                this.zCoord + direction.offsetZ);
            if (!(neighbor instanceof IFluidHandler)) {
                continue;
            }
            IFluidHandler handler = (IFluidHandler) neighbor;
            FluidStack available = handler.drain(direction.getOpposite(), remaining, false);
            int filled = this.tank.fill(available, false);
            if (filled <= 0) {
                continue;
            }
            FluidStack drained = handler.drain(direction.getOpposite(), filled, true);
            if (drained != null && this.tank.fill(drained, true) > 0) {
                this.onFluidChanged();
                return;
            }
        }
    }

    private void pushFluidToNeighbors() {
        FluidStack stored = this.tank.getFluid();
        if (stored == null || stored.amount <= 0) {
            return;
        }
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity neighbor = this.worldObj.getTileEntity(
                this.xCoord + direction.offsetX,
                this.yCoord + direction.offsetY,
                this.zCoord + direction.offsetZ);
            if (!(neighbor instanceof IFluidHandler)) {
                continue;
            }
            IFluidHandler handler = (IFluidHandler) neighbor;
            FluidStack offered = stored.copy();
            int accepted = handler.fill(direction.getOpposite(), offered, false);
            if (accepted <= 0) {
                continue;
            }
            FluidStack drained = this.tank.drain(accepted, true);
            int inserted = handler.fill(direction.getOpposite(), drained, true);
            if (drained != null && inserted < drained.amount) {
                drained.amount -= Math.max(0, inserted);
                this.tank.fill(drained, true);
            }
            this.onFluidChanged();
            return;
        }
    }

    private boolean drainContainerIntoTank(ItemStack held, InventoryPlayer inventory) {
        if (held.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem item = (IFluidContainerItem) held.getItem();
            FluidStack preview = item.drain(held, this.tank.getCapacity() - this.tank.getFluidAmount(), false);
            int fillable = this.tank.fill(preview, false);
            if (fillable <= 0) {
                return false;
            }
            FluidStack drained = item.drain(held, fillable, true);
            if (drained == null || drained.amount <= 0) {
                return false;
            }
            this.tank.fill(drained, true);
            inventory.setItemStack(held);
            return true;
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(held);
        int fillable = this.tank.fill(fluid, false);
        if (fluid == null || fillable < fluid.amount) {
            return false;
        }
        ItemStack empty = this.safeDrainFluidContainer(held);
        if (empty == null) {
            return false;
        }
        this.tank.fill(fluid, true);
        inventory.setItemStack(empty);
        return true;
    }

    private boolean fillContainerFromTank(ItemStack held, InventoryPlayer inventory) {
        FluidStack stored = this.tank.getFluid();
        if (stored == null || stored.amount <= 0) {
            return false;
        }
        if (held.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem item = (IFluidContainerItem) held.getItem();
            FluidStack offered = stored.copy();
            int fillable = item.fill(held, offered, false);
            if (fillable <= 0) {
                return false;
            }
            FluidStack drained = this.tank.drain(fillable, true);
            item.fill(held, drained, true);
            inventory.setItemStack(held);
            return true;
        }
        ItemStack filled = FluidContainerRegistry.fillFluidContainer(stored, held);
        FluidStack filledFluid = FluidContainerRegistry.getFluidForFilledItem(filled);
        if (filled == null || filledFluid == null || filledFluid.amount > stored.amount) {
            return false;
        }
        this.tank.drain(filledFluid.amount, true);
        inventory.setItemStack(filled);
        return true;
    }

    private ItemStack safeDrainFluidContainer(ItemStack held) {
        try {
            return FluidContainerRegistry.drainFluidContainer(held);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean canMerge(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.getItem() == second.getItem()
            && first.getItemDamage() == second.getItemDamage()
            && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static int saturatedAdd(int left, int right) {
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }
}
