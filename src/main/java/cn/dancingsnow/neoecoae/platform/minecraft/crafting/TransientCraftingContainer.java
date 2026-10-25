package cn.dancingsnow.neoecoae.platform.minecraft.crafting;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

/** Server-side transient native grid used by source recipe execution and remainder verification. */
public final class TransientCraftingContainer extends InventoryCrafting {

    private final int width, height;

    public TransientCraftingContainer(int width, int height) {
        super(new Container() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return false;
            }
        }, width, height);
        this.width = width;
        this.height = height;
    }

    public TransientCraftingContainer(Object menu, int width, int height) {
        this(width, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ItemStack getItem(int slot) {
        return getStackInSlot(slot);
    }

    public void setItem(int slot, ItemStack stack) {
        setInventorySlotContents(slot, stack);
    }

    public int getContainerSize() {
        return getSizeInventory();
    }

    public void clearContent() {
        for (int i = 0; i < getSizeInventory(); i++) setItem(i, null);
    }

    public java.util.List<ItemStack> getItems() {
        var result = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < getSizeInventory(); i++) result.add(getItem(i));
        return result;
    }

    public CraftingInput asCraftInput() {
        return asPositionedCraftInput().input();
    }

    public CraftingInput.Positioned asPositionedCraftInput() {
        return CraftingInput.ofPositioned(width, height, getItems());
    }
}
