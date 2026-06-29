package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.HostUiStateContainer;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostSnapshot;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import io.netty.buffer.ByteBuf;

public class ContainerECOStorageController extends HostUiStateContainer {

    private static final int STATE_VERSION = 4;
    private static final int CONTROLLER_SLOT_COUNT = 1;
    public static final int INFINITE_COMPONENT_SLOT_FRAME_X = 145;
    public static final int INFINITE_COMPONENT_SLOT_FRAME_Y = 131;
    public static final int INFINITE_COMPONENT_SLOT_X = INFINITE_COMPONENT_SLOT_FRAME_X + 1;
    public static final int INFINITE_COMPONENT_SLOT_Y = INFINITE_COMPONENT_SLOT_FRAME_Y + 1;

    private final TileECOController controller;
    private StorageHostSnapshot state = StorageHostSnapshot.EMPTY;

    public ContainerECOStorageController(InventoryPlayer playerInventory, TileECOController controller) {
        this.controller = controller;
        this.addSlotToContainer(
            new SlotInfiniteComponent(controller, 0, INFINITE_COMPONENT_SLOT_X, INFINITE_COMPONENT_SLOT_Y));
        this.addPlayerInventory(playerInventory, HostUiLayouts.STORAGE);
    }

    public TileECOController getController() {
        return this.controller;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.controller != null && this.controller.isUseableByPlayer(player);
    }

    @Override
    protected void writeHostUiState(ByteBuf buffer) {
        buffer.writeByte(STATE_VERSION);
        StorageHostSnapshot.create(this.controller).write(buffer);
    }

    @Override
    protected void readHostUiState(ByteBuf buffer) {
        int version = buffer.readUnsignedByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException("Unsupported Storage UI state version: " + version);
        }
        this.state = StorageHostSnapshot.read(buffer);
    }

    public StorageHostSnapshot state() {
        return this.state;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack copied = null;
        Slot slot = index >= 0 && index < this.inventorySlots.size() ? (Slot) this.inventorySlots.get(index) : null;
        if (slot == null || !slot.getHasStack()) {
            return null;
        }

        ItemStack stack = slot.getStack();
        copied = stack.copy();
        if (index < CONTROLLER_SLOT_COUNT) {
            if (!slot.canTakeStack(player)) {
                return null;
            }
            if (!this.mergeItemStack(stack, CONTROLLER_SLOT_COUNT, this.inventorySlots.size(), true)) {
                return null;
            }
        } else {
            Slot controllerSlot = (Slot) this.inventorySlots.get(0);
            if (!controllerSlot.isItemValid(stack) || !this.mergeItemStack(stack, 0, CONTROLLER_SLOT_COUNT, false)) {
                return null;
            }
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

    private void addPlayerInventory(InventoryPlayer playerInventory, HostUiLayouts.Layout layout) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlotToContainer(
                    new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        layout.inventoryX() + 1 + column * 18,
                        layout.inventoryY() + 1 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlotToContainer(new Slot(playerInventory, column, layout.inventoryX() + 1 + column * 18, layout.hotbarY() + 1));
        }
    }
}
