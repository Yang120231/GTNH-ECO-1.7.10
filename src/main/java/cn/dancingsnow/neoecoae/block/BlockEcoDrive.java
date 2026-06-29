package cn.dancingsnow.neoecoae.block;

import java.util.Collections;
import java.util.Map;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.tile.TileECODrive;

public class BlockEcoDrive extends BlockModelDrive {

    private static final String[] TEXTURES = { "neoecoae:block/storage/casing", "neoecoae:block/storage/casing_side",
        "neoecoae:block/storage/casing_back", "neoecoae:block/storage/drive/drive_north",
        "neoecoae:block/storage/drive/drive_north_on", "neoecoae:block/storage/drive/drive_inside",
        "neoecoae:block/storage/drive/drive_inside_top_bottom", "neoecoae:block/storage/drive/cell_housing",
        "neoecoae:block/storage/drive/cell_level", "neoecoae:block/storage/drive/cell_type" };

    public BlockEcoDrive() {
        super(
            "eco_drive",
            "eco_drive_empty",
            "eco_drive_full",
            TEXTURES,
            NeoECOAE.MODID + ":block/storage/casing_side");
    }

    @Override
    public Map<String, String> getFormedTextureOverrides() {
        return Collections.singletonMap(
            NeoECOAE.MODID + ":block/storage/drive/drive_north",
            NeoECOAE.MODID + ":block/storage/drive/drive_north_on");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileECODrive();
    }

    @Override
    public boolean useFullModelWhenOccupied() {
        return true;
    }

    @Override
    public boolean isOccupied(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileECODrive && ((TileECODrive) tile).hasCell();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECODrive)) {
            return false;
        }

        TileECODrive drive = (TileECODrive) tile;
        ItemStack held = player.getHeldItem();
        if (drive.getCellStack() == null && held != null && held.getItem() instanceof cn.dancingsnow.neoecoae.storage.item.IECOStorageMatrixItem) {
            if (!world.isRemote) {
                if (!drive.isItemValidForSlot(0, held)) {
                    return true;
                }
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
                if (!drive.canExtractCellStack()) {
                    return true;
                }
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
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECODrive && !((TileECODrive) tile).prepareForWorldRemoval()) {
            if (!world.isRemote && player != null) {
                player.addChatMessage(new ChatComponentTranslation("chat.neoecoae.storage.infinite_remove_blocked"));
            }
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECODrive && !((TileECODrive) tile).prepareForWorldRemoval()) {
            return;
        }
        super.onBlockExploded(world, x, y, z, explosion);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECODrive) {
            TileECODrive drive = (TileECODrive) tile;
            if (!drive.prepareForWorldRemoval()) {
                return;
            }
            ItemStack stack = ((TileECODrive) tile).getCellStack();
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
