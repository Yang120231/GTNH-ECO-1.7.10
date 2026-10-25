package cn.dancingsnow.neoecoae.platform.ae2.storage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;

/** Read-only cell-slot access for the pinned native hosts. Unknown host layouts fail explicitly. */
public final class NativeDriveReaders {

    private NativeDriveReaders() {}

    private static ItemStack stack(IChestOrDrive host, int slot) {
        if (slot < 0 || slot >= host.getCellCount()) throw new IllegalArgumentException("Native cell slot");
        String type = host.getClass()
            .getName();
        int nativeSlot;
        if (type.equals("appeng.tile.storage.TileDrive")) nativeSlot = slot;
        // ME chest slot 0 receives inserted items; its single cell is stored in native slot 1.
        else if (type.equals("appeng.tile.storage.TileChest")) nativeSlot = 1;
        else throw new IllegalArgumentException("Unmapped native cell host: " + type);
        try {
            // Referencing TileChest directly resolves optional GT/IC2 parents before FML strips them.
            Object inventory = host.getClass()
                .getMethod("getInternalInventory")
                .invoke(host);
            return ((net.minecraft.inventory.IInventory) inventory).getStackInSlot(nativeSlot);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned native cell inventory changed: " + type, failure);
        }
    }

    public static StorageCell cell(IChestOrDrive host, int slot) {
        var stack = stack(host, slot);
        return stack == null ? null : StorageCells.getCellInventory(stack, null);
    }

    public static Item cellItem(IChestOrDrive host, int slot) {
        var stack = stack(host, slot);
        return stack == null ? null : stack.getItem();
    }
}
