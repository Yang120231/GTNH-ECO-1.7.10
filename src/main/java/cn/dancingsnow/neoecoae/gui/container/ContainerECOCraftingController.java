package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.HostUiStateContainer;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostSnapshot;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import io.netty.buffer.ByteBuf;

public class ContainerECOCraftingController extends HostUiStateContainer {

    private static final int STATE_VERSION = 3;

    private final TileECOController controller;
    private CraftingHostSnapshot state = CraftingHostSnapshot.EMPTY;

    public ContainerECOCraftingController(InventoryPlayer playerInventory, TileECOController controller) {
        this.controller = controller;
        this.addPlayerInventory(playerInventory, HostUiLayouts.CRAFTING);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.controller != null && this.controller.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        return null;
    }

    @Override
    protected void writeHostUiState(ByteBuf buffer) {
        buffer.writeByte(STATE_VERSION);
        CraftingHostSnapshot.create(this.controller)
            .write(buffer);
    }

    @Override
    protected void readHostUiState(ByteBuf buffer) {
        int version = buffer.readUnsignedByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException("Unsupported Crafting UI state version: " + version);
        }
        this.state = CraftingHostSnapshot.read(buffer);
    }

    public CraftingHostSnapshot state() {
        return this.state;
    }

    public TileECOController getController() {
        return this.controller;
    }

    @Override
    protected int hostUiSyncIntervalTicks() {
        return 5;
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
            this.addSlotToContainer(
                new Slot(playerInventory, column, layout.inventoryX() + 1 + column * 18, layout.hotbarY() + 1));
        }
    }
}
