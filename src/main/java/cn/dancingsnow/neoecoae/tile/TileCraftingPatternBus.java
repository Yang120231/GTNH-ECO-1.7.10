package cn.dancingsnow.neoecoae.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.Constants;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.util.ScheduledReason;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlan;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook;

public class TileCraftingPatternBus extends TileCraftingMember implements IInventory, ICraftingProvider {

    public static final int COLUMNS = 9;
    public static final int ROWS = 7;
    public static final int SLOTS_PER_PAGE = COLUMNS * ROWS;
    public static final int PAGE_COUNT = 2;
    public static final int PATTERN_SLOTS = SLOTS_PER_PAGE * PAGE_COUNT;

    private static final String TAG_PATTERNS = "Patterns";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_PATTERN_COUNT = "PatternCount";

    private final ItemStack[] patterns = new ItemStack[PATTERN_SLOTS];
    private int clientPatternCount;
    private ScheduledReason scheduledReason = ScheduledReason.UNDEFINED;

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        if (helper == null || this.worldObj == null) {
            return;
        }
        for (ItemStack pattern : this.patterns) {
            ICraftingPatternDetails details = this.patternDetails(pattern);
            if (details != null) {
                helper.addCraftingOption(this, details);
            }
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        TileECOController controller = this.findCraftingController();
        if (controller == null || !controller.isFormed()) {
            this.scheduledReason = ScheduledReason.NO_TARGET;
            return false;
        }
        TileCraftingWorker worker = controller.findAvailableCraftingWorker();
        if (worker == null) {
            this.scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return false;
        }
        ECOFastPathPlan plannerPlan = ECOFastPathPlannerHook.tryPlan(controller, patternDetails, table);
        boolean accepted = worker.acceptPattern(patternDetails, table);
        if (accepted) {
            controller.recordCraftingPlannerDecision(plannerPlan.accepted());
        }
        this.scheduledReason = accepted ? ScheduledReason.UNDEFINED : ScheduledReason.SOMETHING_STUCK;
        return accepted;
    }

    @Override
    public boolean isBusy() {
        TileECOController controller = this.findCraftingController();
        return controller == null || !controller.isFormed() || controller.findAvailableCraftingWorker() == null;
    }

    @Override
    public ItemStack getCrafterIcon() {
        return this.getStackInSlot(0);
    }

    @Override
    public ScheduledReason getScheduledReason() {
        return this.scheduledReason;
    }

    public int getPatternCount() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return this.clientPatternCount;
        }
        return this.countPatterns();
    }

    public int patternCount() {
        return this.getPatternCount();
    }

    public int getPageCount() {
        return PAGE_COUNT;
    }

    @Override
    public int getSizeInventory() {
        return PATTERN_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.isValidSlot(slot) ? this.patterns[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (!this.isValidSlot(slot) || this.patterns[slot] == null || amount <= 0) {
            return null;
        }
        ItemStack removed;
        if (this.patterns[slot].stackSize <= amount) {
            removed = this.patterns[slot];
            this.patterns[slot] = null;
        } else {
            removed = this.patterns[slot].splitStack(amount);
            if (this.patterns[slot].stackSize <= 0) {
                this.patterns[slot] = null;
            }
        }
        this.onInventoryChanged();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (!this.isValidSlot(slot)) {
            return null;
        }
        ItemStack stack = this.patterns[slot];
        this.patterns[slot] = null;
        if (stack != null) {
            this.onInventoryChanged();
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (!this.isValidSlot(slot)) {
            return;
        }
        this.patterns[slot] = stack;
        if (this.patterns[slot] != null && this.patterns[slot].stackSize > this.getInventoryStackLimit()) {
            this.patterns[slot].stackSize = this.getInventoryStackLimit();
        }
        this.onInventoryChanged();
    }

    @Override
    public String getInventoryName() {
        return "container.neoecoae.crafting_pattern_bus";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
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
        return this.isValidSlot(slot) && this.patternDetails(stack) != null;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < this.patterns.length; i++) {
            if (this.patterns[i] != null) {
                NBTTagCompound slotTag = new NBTTagCompound();
                slotTag.setInteger(TAG_SLOT, i);
                NBTTagCompound stackTag = new NBTTagCompound();
                this.patterns[i].writeToNBT(stackTag);
                slotTag.setTag(TAG_STACK, stackTag);
                list.appendTag(slotTag);
            }
        }
        tag.setTag(TAG_PATTERNS, list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        for (int i = 0; i < this.patterns.length; i++) {
            this.patterns[i] = null;
        }
        NBTTagList list = tag.getTagList(TAG_PATTERNS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound slotTag = list.getCompoundTagAt(i);
            int slot = slotTag.getInteger(TAG_SLOT);
            if (this.isValidSlot(slot) && slotTag.hasKey(TAG_STACK)) {
                this.patterns[slot] = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag(TAG_STACK));
            }
        }
        this.clientPatternCount = this.countPatterns();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_PATTERN_COUNT, this.countPatterns());
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.clientPatternCount = packet.func_148857_g()
            .getInteger(TAG_PATTERN_COUNT);
    }

    private void onInventoryChanged() {
        this.clientPatternCount = this.countPatterns();
        this.markDirty();
        this.notifyCraftingControllerChanged();
    }

    private int countPatterns() {
        int count = 0;
        for (ItemStack pattern : this.patterns) {
            if (this.patternDetails(pattern) != null) {
                count++;
            }
        }
        return count;
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < this.patterns.length;
    }

    private ICraftingPatternDetails patternDetails(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ICraftingPatternItem)) {
            return null;
        }
        return ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, this.worldObj);
    }
}
