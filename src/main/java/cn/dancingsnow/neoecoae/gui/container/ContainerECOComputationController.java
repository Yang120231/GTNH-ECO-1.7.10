package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.HostUiStateContainer;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostSnapshot;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import io.netty.buffer.ByteBuf;

public class ContainerECOComputationController extends HostUiStateContainer {

    private static final int STATE_VERSION = 2;

    private final TileECOController controller;
    private ComputationHostSnapshot state = ComputationHostSnapshot.EMPTY;

    public ContainerECOComputationController(InventoryPlayer playerInventory, TileECOController controller) {
        this.controller = controller;
        this.addPlayerInventory(playerInventory, HostUiLayouts.COMPUTATION);
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
        ComputationHostSnapshot.create(this.controller)
            .write(buffer);
    }

    @Override
    protected void readHostUiState(ByteBuf buffer) {
        int version = buffer.readUnsignedByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException("Unsupported Computation UI state version: " + version);
        }
        this.state = ComputationHostSnapshot.read(buffer);
    }

    public ComputationHostSnapshot state() {
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
