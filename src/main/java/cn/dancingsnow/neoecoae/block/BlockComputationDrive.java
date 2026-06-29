package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.tile.TileComputationDrive;

public class BlockComputationDrive extends BlockModelDrive {

    private static final String[] TEXTURES = { "neoecoae:block/compute/casing_side_east",
        "neoecoae:block/compute/casing_side_west", "neoecoae:block/compute/casing",
        "neoecoae:block/compute/casing_back", "neoecoae:block/compute/drive/drive_inside",
        "neoecoae:block/compute/drive/drive_inside_top", "neoecoae:block/compute/drive/drive_north",
        "neoecoae:block/compute/drive/drive_north_on", "neoecoae:block/compute/drive/cell_inside_back",
        "neoecoae:block/compute/drive/cell_north_a", "neoecoae:block/compute/drive/cell_north_b",
        "neoecoae:block/compute/drive/cell_north_c", "neoecoae:block/compute/drive/cell_side_a",
        "neoecoae:block/compute/drive/cell_side_b", "neoecoae:block/compute/drive/cell_side_c",
        "neoecoae:block/compute/drive/cell_south", "neoecoae:block/compute/drive/cell_top",
        "neoecoae:block/compute/cable/cable_a", "neoecoae:block/compute/cable/cable_b",
        "neoecoae:block/compute/cable/cable_c", "neoecoae:block/compute/cable/plug_a",
        "neoecoae:block/compute/cable/plug_b", "neoecoae:block/compute/cable/plug_c",
        "neoecoae:block/compute/cable/plug_off" };

    public BlockComputationDrive() {
        super(
            "computation_drive",
            "computation_drive_empty",
            "computation_drive_full",
            TEXTURES,
            NeoECOAE.MODID + ":block/compute/casing_side_east");
    }

    @Override
    public boolean useFullModelWhenFormed() {
        return true;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileComputationDrive();
    }

    @Override
    public boolean useFullModelWhenOccupied() {
        return true;
    }

    @Override
    public boolean isOccupied(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileComputationDrive && ((TileComputationDrive) tile).hasCell();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileComputationDrive)) {
            return false;
        }

        TileComputationDrive drive = (TileComputationDrive) tile;
        ItemStack held = player.getHeldItem();
        if (drive.getCellStack() == null && TileComputationDrive.isComputationCell(held)) {
            if (!world.isRemote) {
                ItemStack inserted = held.copy();
                inserted.stackSize = 1;
                drive.setInventorySlotContents(0, inserted);
                held.stackSize--;
                if (held.stackSize <= 0) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
                player.inventory.markDirty();
            }
            return true;
        }

        if (drive.getCellStack() != null && (held == null || player.isSneaking())) {
            if (!world.isRemote) {
                ItemStack removed = drive.getStackInSlotOnClosing(0);
                if (removed == null) {
                    return true;
                }
                if (held == null) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, removed);
                } else if (!player.inventory.addItemStackToInventory(removed)) {
                    dropStack(world, x, y, z, removed);
                }
                player.inventory.markDirty();
            }
            return true;
        }

        return false;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileComputationDrive) {
            ItemStack stack = ((TileComputationDrive) tile).getCellStack();
            if (stack != null) {
                dropStack(world, x, y, z, stack);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static void dropStack(World world, int x, int y, int z, ItemStack stack) {
        if (stack == null || world.isRemote) {
            return;
        }
        EntityItem entity = new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, stack.copy());
        world.spawnEntityInWorld(entity);
    }
}
