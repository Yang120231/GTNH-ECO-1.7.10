package cn.dancingsnow.neoecoae.tile;

import java.util.Arrays;

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
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.util.ScheduledReason;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlan;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchCoordinator;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchTransaction;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingExecutionContext;

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
    private int cachedPatternCount;
    private ScheduledReason scheduledReason = ScheduledReason.UNDEFINED;

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        this.provideCrafting(helper, this);
    }

    public void provideCrafting(ICraftingProviderHelper helper, ICraftingMedium medium) {
        if (helper == null || this.worldObj == null) {
            return;
        }
        for (ItemStack pattern : this.patterns) {
            ICraftingPatternDetails details = this.patternDetails(pattern);
            if (details != null) {
                helper.addCraftingOption(medium, details);
            }
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        TileECOController controller = this.findCraftingController();
        boolean accepted = pushPattern(controller, patternDetails, table);
        this.scheduledReason = accepted ? ScheduledReason.UNDEFINED
            : controller == null || !controller.isFormed() ? ScheduledReason.NO_TARGET
                : ScheduledReason.SOMETHING_STUCK;
        return accepted;
    }

    public static boolean pushPattern(TileECOController controller, ICraftingPatternDetails patternDetails,
        InventoryCrafting table) {
        if (controller == null || !controller.isFormed() || controller.lacksVirtualCraftingCapacity()) {
            return false;
        }
        // AE2 authorises exactly one craft per pushPattern and has already consumed that craft's
        // inputs into the table. We must enqueue exactly one craft here - pulling additional inputs
        // from the network to inflate the batch would overproduce and double-spend ingredients.
        ECOCraftingBatchTransaction transaction = null;
        ECOCraftingBatchCoordinator coordinator = ECOCraftingExecutionContext.currentBatchCoordinator();
        if (coordinator != null) {
            transaction = coordinator.prepareBatch(patternDetails, table, controller);
            if (transaction == null && coordinator.isBatchDispatchSuspended()) {
                return false;
            }
        }
        int craftCount = transaction == null ? 1 : Math.max(1, transaction.craftCount());
        boolean accepted;
        try {
            accepted = controller.acceptVirtualCraftingBatch(
                patternDetails,
                table,
                craftCount,
                ECOCraftingExecutionContext.currentJobId());
        } catch (RuntimeException e) {
            if (transaction != null) {
                try {
                    transaction.rollback();
                } catch (RuntimeException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
            if (coordinator != null) {
                coordinator.handleBatchFailure(e);
            }
            return false;
        }
        if (transaction != null) {
            if (accepted) {
                transaction.commit();
            } else {
                transaction.rollback();
            }
        } else if (accepted && coordinator != null) {
            coordinator.recordSlowCraftAccepted();
        }
        if (accepted) {
            ECOFastPathPlan plannerPlan = ECOFastPathPlannerHook.tryPlan(controller, patternDetails, table);
            controller.recordCraftingPlannerDecision(plannerPlan.accepted());
        }
        return accepted;
    }

    @Override
    public boolean isBusy() {
        TileECOController controller = this.findCraftingController();
        return controller == null || !controller.isFormed() || controller.lacksVirtualCraftingCapacity();
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
        return this.cachedPatternCount;
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
        Arrays.fill(this.patterns, null);
        NBTTagList list = tag.getTagList(TAG_PATTERNS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound slotTag = list.getCompoundTagAt(i);
            int slot = slotTag.getInteger(TAG_SLOT);
            if (this.isValidSlot(slot) && slotTag.hasKey(TAG_STACK)) {
                this.patterns[slot] = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag(TAG_STACK));
            }
        }
        this.cachedPatternCount = this.countPatterns();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_PATTERN_COUNT, this.cachedPatternCount);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.cachedPatternCount = packet.func_148857_g()
            .getInteger(TAG_PATTERN_COUNT);
    }

    private void onInventoryChanged() {
        this.cachedPatternCount = this.countPatterns();
        this.markDirty();
        this.notifyCraftingPatternsChanged();
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
