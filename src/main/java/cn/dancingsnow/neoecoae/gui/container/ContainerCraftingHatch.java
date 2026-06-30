package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;

public class ContainerCraftingHatch extends Container {

    public static final int HATCH_GRID_X = 7;
    public static final int HATCH_GRID_Y = 22;
    public static final int PLAYER_INVENTORY_X = 7;
    public static final int PLAYER_INVENTORY_Y = 73;
    public static final int PLAYER_HOTBAR_Y = 131;
    public static final int ACTION_FLUID_SLOT_CLICK = 0;
    private static final int FIELD_FLUID_ID = 0;
    private static final int FIELD_FLUID_AMOUNT = 1;
    private static final int FIELD_CAPACITY = 2;

    private final TileCraftingHatch hatch;
    private int fluidId = -1;
    private int fluidAmount;
    private int capacity;

    public ContainerCraftingHatch(InventoryPlayer playerInventory, TileCraftingHatch hatch) {
        this.hatch = hatch;
        this.fluidId = hatch == null ? -1 : hatch.getFluidId();
        this.fluidAmount = hatch == null ? 0 : hatch.getFluidAmount();
        this.capacity = hatch == null ? 0 : hatch.getTankCapacity();
        this.addPlayerInventory(playerInventory);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.hatch != null && this.hatch.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        return null;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int nextFluidId = this.hatch == null ? -1 : this.hatch.getFluidId();
        int nextFluidAmount = this.hatch == null ? 0 : this.hatch.getFluidAmount();
        int nextCapacity = this.hatch == null ? 0 : this.hatch.getTankCapacity();
        for (Object crafter : this.crafters) {
            ICrafting listener = (ICrafting) crafter;
            listener.sendProgressBarUpdate(this, FIELD_FLUID_ID, nextFluidId);
            listener.sendProgressBarUpdate(this, FIELD_FLUID_AMOUNT, nextFluidAmount);
            listener.sendProgressBarUpdate(this, FIELD_CAPACITY, nextCapacity);
        }
        this.fluidId = nextFluidId;
        this.fluidAmount = nextFluidAmount;
        this.capacity = nextCapacity;
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == FIELD_FLUID_ID) {
            this.fluidId = value;
        } else if (id == FIELD_FLUID_AMOUNT) {
            this.fluidAmount = value;
        } else if (id == FIELD_CAPACITY) {
            this.capacity = value;
        }
        if (this.hatch != null) {
            this.hatch.setClientFluid(this.fluidId, this.fluidAmount);
        }
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (action == ACTION_FLUID_SLOT_CLICK) {
            return this.hatch != null && this.hatch.handleFluidContainerClick(player);
        }
        return super.enchantItem(player, action);
    }

    public TileCraftingHatch getHatch() {
        return this.hatch;
    }

    public int getFluidId() {
        return this.fluidId;
    }

    public int getFluidAmount() {
        return this.fluidAmount;
    }

    public int getCapacity() {
        return this.capacity;
    }

    private void addPlayerInventory(InventoryPlayer playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlotToContainer(
                    new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + 1 + column * 18,
                        PLAYER_INVENTORY_Y + 1 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlotToContainer(
                new Slot(playerInventory, column, PLAYER_INVENTORY_X + 1 + column * 18, PLAYER_HOTBAR_Y + 1));
        }
    }
}
