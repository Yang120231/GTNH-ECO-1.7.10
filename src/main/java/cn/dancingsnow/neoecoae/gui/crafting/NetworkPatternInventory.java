package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

/** A live slot view; patterns remain owned by their physical buses. */
public final class NetworkPatternInventory extends InventoryBasic {

    private final TileECOInterface owner;

    public NetworkPatternInventory(TileECOInterface owner) {
        super("ECO patterns", false, 0);
        this.owner = owner;
    }

    private List<TileCraftingPatternBus> buses() {
        List<TileCraftingPatternBus> buses = new ArrayList<>();
        TileECOController controller = owner.getBoundController();
        if (controller != null)
            for (TileECOController host : controller.getNetworkMembers()) buses.addAll(host.getCraftingPatternBuses());
        return buses;
    }

    private TileCraftingPatternBus bus(int slot) {
        List<TileCraftingPatternBus> buses = buses();
        int index = slot / TileCraftingPatternBus.PATTERN_SLOTS;
        return slot < 0 || index >= buses.size() ? null : buses.get(index);
    }

    @Override
    public int getSizeInventory() {
        return buses().size() * TileCraftingPatternBus.PATTERN_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        TileCraftingPatternBus bus = bus(slot);
        return bus == null ? null : bus.getStackInSlot(slot % TileCraftingPatternBus.PATTERN_SLOTS);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        TileCraftingPatternBus bus = bus(slot);
        return bus == null ? null : bus.decrStackSize(slot % TileCraftingPatternBus.PATTERN_SLOTS, amount);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        TileCraftingPatternBus bus = bus(slot);
        if (bus != null) bus.setInventorySlotContents(slot % TileCraftingPatternBus.PATTERN_SLOTS, stack);
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        TileCraftingPatternBus bus = bus(slot);
        return bus != null && bus.isItemValidForSlot(slot % TileCraftingPatternBus.PATTERN_SLOTS, stack);
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    public void organize() {
        List<ItemStack> patterns = new ArrayList<>();
        int size = getSizeInventory();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (stack != null) patterns.add(stack);
        }
        patterns.sort(java.util.Comparator.comparing(ItemStack::getDisplayName));
        for (int slot = 0; slot < size; slot++)
            setInventorySlotContents(slot, slot < patterns.size() ? patterns.get(slot) : null);
    }
}
