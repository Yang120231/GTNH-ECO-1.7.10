package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/** Physical pattern slots of one source or target. Every write is read back by {@link PatternTransfer}. */
public interface PatternSlotAccess {

    int size();

    ItemStack get(int slot);

    /** Whether {@code stack} may be merged into the current content of {@code slot}. */
    boolean accepts(int slot, ItemStack stack);

    void set(int slot, ItemStack stack);

    /** Save and publish the change through the owner's normal callbacks. */
    void changed();

    /** The host is still the same live object in its world. */
    boolean isLive();

    /** The single-host access and slot that physically hold {@code slot}; composite domains override this. */
    default SlotRef physical(int slot) {
        return new SlotRef(this, slot);
    }

    static PatternSlotAccess of(IInventory inventory, int limit, TileEntity owner) {
        return new InventoryAccess(inventory, limit, owner);
    }

    final class SlotRef {

        public final PatternSlotAccess access;
        public final int slot;

        public SlotRef(PatternSlotAccess access, int slot) {
            this.access = access;
            this.slot = slot;
        }
    }

    final class InventoryAccess implements PatternSlotAccess {

        private final IInventory inventory;
        private final int limit;
        private final TileEntity owner;

        InventoryAccess(IInventory inventory, int limit, TileEntity owner) {
            this.inventory = inventory;
            this.limit = Math.max(0, Math.min(limit, inventory == null ? 0 : inventory.getSizeInventory()));
            this.owner = owner;
        }

        @Override
        public int size() {
            return limit;
        }

        @Override
        public ItemStack get(int slot) {
            return slot < 0 || slot >= limit ? null : inventory.getStackInSlot(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            if (slot < 0 || slot >= limit || stack == null) return false;
            ItemStack current = inventory.getStackInSlot(slot);
            if (current == null) return inventory.isItemValidForSlot(slot, stack);
            return PatternStacks.sameItem(current, stack) && current.stackSize + stack.stackSize
                <= Math.min(inventory.getInventoryStackLimit(), current.getMaxStackSize());
        }

        @Override
        public void set(int slot, ItemStack stack) {
            inventory.setInventorySlotContents(slot, stack);
        }

        @Override
        public void changed() {
            inventory.markDirty();
            // Some pattern mappers have an empty markDirty; the host tile must still be saved.
            if (owner != null && owner != inventory) owner.markDirty();
        }

        @Override
        public boolean isLive() {
            if (owner == null) return true;
            return !owner.isInvalid() && owner.getWorldObj() != null
                && owner.getWorldObj()
                    .getTileEntity(owner.xCoord, owner.yCoord, owner.zCoord) == owner;
        }
    }
}
