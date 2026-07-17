package cn.dancingsnow.neoecoae.gui.container;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;

public class ContainerCraftingPatternBus extends Container {

    public static final int COLUMNS = TileCraftingPatternBus.COLUMNS;
    public static final int ROWS = TileCraftingPatternBus.ROWS;
    public static final int PATTERN_SLOTS_PER_PAGE = TileCraftingPatternBus.SLOTS_PER_PAGE;
    public static final int ACTION_PREVIOUS_PAGE = 0;
    public static final int ACTION_NEXT_PAGE = 1;
    public static final int PATTERN_GRID_X = 7;
    public static final int PATTERN_GRID_Y = 22;
    public static final int PLAYER_INVENTORY_X = 7;
    public static final int PLAYER_INVENTORY_Y = 163;
    public static final int PLAYER_HOTBAR_Y = 221;

    private static final int FIELD_PAGE = 0;
    private static final int FIELD_PAGE_COUNT = 1;
    private static final int VISIBLE_SLOT_SYNC_BATCH_SIZE = 8;
    private static final ItemStack FORCE_SYNC_SENTINEL = new ItemStack(Items.stick);

    private final TileCraftingPatternBus bus;
    private int currentPage;
    private int pageCount;
    private int visibleSlotSyncCursor = -1;

    public ContainerCraftingPatternBus(InventoryPlayer playerInventory, TileCraftingPatternBus bus) {
        this.bus = bus;
        this.pageCount = Math.max(1, bus.getPageCount());
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int pageSlot = column + row * COLUMNS;
                this.addSlotToContainer(
                    new PatternSlot(
                        this,
                        bus,
                        pageSlot,
                        PATTERN_GRID_X + column * 18 + 1,
                        PATTERN_GRID_Y + row * 18 + 1));
            }
        }
        this.addPlayerInventory(playerInventory);
    }

    @Override
    public void addCraftingToCrafters(ICrafting listener) {
        if (this.crafters.contains(listener)) {
            throw new IllegalArgumentException("Listener already listening");
        }

        this.crafters.add(listener);
        List<ItemStack> initialContents = this.getInventory();
        for (int slot = 0; slot < this.inventorySlots.size(); slot++) {
            ItemStack stack = initialContents.get(slot);
            if (slot < PATTERN_SLOTS_PER_PAGE) {
                // Encoded patterns can carry large NBT payloads. Keep them out of the initial
                // WindowItems packet and let detectAndSendChanges spread their copies over
                // several ticks instead of stalling the server thread in one tick.
                initialContents.set(slot, null);
                this.inventoryItemStacks.set(slot, stack);
            } else {
                this.inventoryItemStacks.set(slot, stack == null ? null : stack.copy());
            }
        }
        listener.sendContainerAndContentsToPlayer(this, initialContents);
        this.scheduleVisibleSlotSync();
        this.detectAndSendChanges();
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.bus != null && this.bus.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        this.pageCount = Math.max(1, this.bus.getPageCount());
        this.applySyncedPage(this.currentPage);
        for (Object crafter : this.crafters) {
            ((ICrafting) crafter).sendProgressBarUpdate(this, FIELD_PAGE, this.currentPage);
            ((ICrafting) crafter).sendProgressBarUpdate(this, FIELD_PAGE_COUNT, this.pageCount);
        }
        this.prepareVisibleSlotSyncBatch();
        super.detectAndSendChanges();
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == FIELD_PAGE) {
            this.applySyncedPage(value);
        } else if (id == FIELD_PAGE_COUNT) {
            this.pageCount = Math.max(1, value);
            this.applySyncedPage(this.currentPage);
        }
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (action == ACTION_PREVIOUS_PAGE) {
            this.setPage(this.currentPage - 1);
            return true;
        }
        if (action == ACTION_NEXT_PAGE) {
            this.setPage(this.currentPage + 1);
            return true;
        }
        return super.enchantItem(player, action);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = slotIndex >= 0 && slotIndex < this.inventorySlots.size() ? (Slot) this.inventorySlots.get(slotIndex)
            : null;
        if (slot == null || !slot.getHasStack()) {
            return null;
        }

        ItemStack stack = slot.getStack();
        ItemStack copied = stack.copy();
        if (slotIndex < PATTERN_SLOTS_PER_PAGE) {
            if (!this.mergeItemStack(stack, PATTERN_SLOTS_PER_PAGE, this.inventorySlots.size(), true)) {
                return null;
            }
        } else if (!this.bus.isItemValidForSlot(0, stack)
            || !this.mergeItemStack(stack, 0, PATTERN_SLOTS_PER_PAGE, false)) {
                return null;
            }

        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == copied.stackSize) {
            return null;
        }
        slot.onPickupFromSlot(player, stack);
        return copied;
    }

    public int currentPage() {
        return this.currentPage;
    }

    public int pageCount() {
        return this.pageCount;
    }

    public void setPage(int page) {
        if (this.bus.getWorldObj() != null && this.bus.getWorldObj().isRemote) {
            return;
        }
        this.applySyncedPage(page);
    }

    public void setClientPage(int page) {
        this.applySyncedPage(page);
    }

    private void applySyncedPage(int page) {
        int maxPage = Math.max(0, this.pageCount() - 1);
        int clampedPage = Math.max(0, Math.min(maxPage, page));
        if (this.currentPage != clampedPage) {
            this.scheduleVisibleSlotSync();
        }
        this.currentPage = clampedPage;
    }

    private void scheduleVisibleSlotSync() {
        this.visibleSlotSyncCursor = 0;
    }

    private void prepareVisibleSlotSyncBatch() {
        if (this.visibleSlotSyncCursor < 0) {
            return;
        }

        int slotLimit = Math.min(PATTERN_SLOTS_PER_PAGE, this.inventoryItemStacks.size());
        int batchEnd = Math.min(slotLimit, this.visibleSlotSyncCursor + VISIBLE_SLOT_SYNC_BATCH_SIZE);

        // Suppress the not-yet-scheduled slots by temporarily comparing them by identity.
        // Their real snapshot is installed by Container when their batch is sent.
        for (int slot = batchEnd; slot < slotLimit; slot++) {
            this.inventoryItemStacks.set(slot, ((Slot) this.inventorySlots.get(slot)).getStack());
        }
        for (int slot = this.visibleSlotSyncCursor; slot < batchEnd; slot++) {
            // A non-null sentinel also forces an explicit clear packet for empty slots.
            this.inventoryItemStacks.set(slot, FORCE_SYNC_SENTINEL);
        }

        this.visibleSlotSyncCursor = batchEnd >= slotLimit ? -1 : batchEnd;
    }

    private int actualSlot(int pageSlot) {
        return this.currentPage * PATTERN_SLOTS_PER_PAGE + pageSlot;
    }

    private void addPlayerInventory(InventoryPlayer playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlotToContainer(
                    new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18 + 1,
                        PLAYER_INVENTORY_Y + row * 18 + 1));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlotToContainer(
                new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * 18 + 1, PLAYER_HOTBAR_Y + 1));
        }
    }

    private static final class PatternSlot extends Slot {

        private final ContainerCraftingPatternBus container;
        private final TileCraftingPatternBus bus;
        private final int pageSlot;

        private PatternSlot(ContainerCraftingPatternBus container, TileCraftingPatternBus bus, int pageSlot, int x,
            int y) {
            super(bus, pageSlot, x, y);
            this.container = container;
            this.bus = bus;
            this.pageSlot = pageSlot;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return this.bus.isItemValidForSlot(this.getSlotIndex(), stack);
        }

        @Override
        public ItemStack getStack() {
            return this.bus.getStackInSlot(this.getSlotIndex());
        }

        @Override
        public boolean getHasStack() {
            return this.getStack() != null;
        }

        @Override
        public void putStack(ItemStack stack) {
            this.bus.setInventorySlotContents(this.getSlotIndex(), stack);
            this.onSlotChanged();
        }

        @Override
        public void onSlotChanged() {
            this.bus.markDirty();
        }

        @Override
        public int getSlotStackLimit() {
            return this.bus.getInventoryStackLimit();
        }

        @Override
        public ItemStack decrStackSize(int amount) {
            return this.bus.decrStackSize(this.getSlotIndex(), amount);
        }

        @Override
        public boolean isSlotInInventory(IInventory inventory, int slot) {
            return inventory == this.bus && slot == this.getSlotIndex();
        }

        @Override
        public int getSlotIndex() {
            return this.container.actualSlot(this.pageSlot);
        }
    }
}
