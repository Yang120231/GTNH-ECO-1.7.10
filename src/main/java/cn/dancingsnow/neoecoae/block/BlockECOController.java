package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public class BlockECOController extends BlockDirectionalModernModel {

    private final ECOControllerSubsystem subsystem;
    private final ECOControllerTier tier;
    private final String formedModelName;
    private final String mirroredFormedModelName;

    public BlockECOController(String id, String modelName, String formedModelName, String[] textureNames,
        ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        this(id, modelName, formedModelName, formedModelName + "_mirrored", textureNames, subsystem, tier);
    }

    public BlockECOController(String id, String modelName, String formedModelName, String mirroredFormedModelName,
        String[] textureNames, ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        super(id, modelName, textureNames);
        this.subsystem = subsystem;
        this.tier = tier;
        this.formedModelName = formedModelName;
        this.mirroredFormedModelName = mirroredFormedModelName;
    }

    public BlockECOController(String id, String modelName, String formedModelName, String[] textureNames,
        ModelFacing inventoryModelFacing, ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        this(
            id,
            modelName,
            formedModelName,
            formedModelName + "_mirrored",
            textureNames,
            inventoryModelFacing,
            subsystem,
            tier);
    }

    public BlockECOController(String id, String modelName, String formedModelName, String mirroredFormedModelName,
        String[] textureNames, ModelFacing inventoryModelFacing, ECOControllerSubsystem subsystem,
        ECOControllerTier tier) {
        super(id, modelName, textureNames, inventoryModelFacing);
        this.subsystem = subsystem;
        this.tier = tier;
        this.formedModelName = formedModelName;
        this.mirroredFormedModelName = mirroredFormedModelName;
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }

    public String getFormedModelName() {
        return this.formedModelName;
    }

    public String getMirroredFormedModelName() {
        return this.mirroredFormedModelName;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        TileECOController controller = new TileECOController(this.subsystem, this.tier);
        controller.setFacingMeta(metadata);
        return controller;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController) {
            ((TileECOController) tile).setFacingMeta(world.getBlockMetadata(x, y, z));
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOController) || this.subsystem != ECOControllerSubsystem.STORAGE) {
            return false;
        }
        TileECOController controller = (TileECOController) tile;
        ItemStack held = player.getHeldItem();
        if (held != null && controller.isItemValidForSlot(0, held)) {
            if (!world.isRemote) {
                ItemStack existing = controller.getStackInSlot(0);
                int room = controller.getInventoryStackLimit() - (existing == null ? 0 : existing.stackSize);
                if (room <= 0) {
                    return true;
                }
                int moved = Math.min(room, held.stackSize);
                if (existing == null) {
                    ItemStack inserted = held.copy();
                    inserted.stackSize = moved;
                    controller.setInventorySlotContents(0, inserted);
                } else {
                    existing.stackSize += moved;
                    controller.setInventorySlotContents(0, existing);
                }
                held.stackSize -= moved;
                if (held.stackSize <= 0) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
                player.inventory.markDirty();
            }
            return true;
        }
        if (player.isSneaking() && held == null && controller.getStackInSlot(0) != null) {
            if (!world.isRemote) {
                ItemStack removed = controller.decrStackSize(0, controller.getStackInSlot(0).stackSize);
                if (removed != null) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, removed);
                    player.inventory.markDirty();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController) {
            ItemStack stack = ((TileECOController) tile).getStackInSlot(0);
            if (stack != null && !world.isRemote) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, stack.copy()));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
