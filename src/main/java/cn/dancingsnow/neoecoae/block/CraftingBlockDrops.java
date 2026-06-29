package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.TileCraftingWorker;

final class CraftingBlockDrops {

    private CraftingBlockDrops() {}

    static void dropInventory(World world, int x, int y, int z, TileEntity tile) {
        if (world == null || world.isRemote || tile == null) {
            return;
        }
        if (tile instanceof IInventory) {
            IInventory inventory = (IInventory) tile;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                dropStack(world, x, y, z, inventory.getStackInSlotOnClosing(slot));
            }
        }
        if (tile instanceof TileCraftingWorker) {
            dropStack(world, x, y, z, ((TileCraftingWorker) tile).takePendingOutput());
        }
    }

    private static void dropStack(World world, int x, int y, int z, ItemStack stack) {
        if (stack == null) {
            return;
        }
        world.spawnEntityInWorld(new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, stack.copy()));
    }
}
