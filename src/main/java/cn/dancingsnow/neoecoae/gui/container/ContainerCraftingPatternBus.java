package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
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

    private final TileCraftingPatternBus bus;
    private int currentPage;
    private int pageCount;
    private boolean forceVisibleSlotSync = true;

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
        this.prepareForcedVisibleSlotSync();
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
            this.forceVisibleSlotSync = true;
        }
        this.currentPage = clampedPage;
    }

    private void prepareForcedVisibleSlotSync() {
        if (!this.forceVisibleSlotSync) {
            return;
        }
        for (int slot = 0; slot < PATTERN_SLOTS_PER_PAGE && slot < this.inventoryItemStacks.size(); slot++) {
            this.inventoryItemStacks.set(slot, null);
        }
        this.forceVisibleSlotSync = false;
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
