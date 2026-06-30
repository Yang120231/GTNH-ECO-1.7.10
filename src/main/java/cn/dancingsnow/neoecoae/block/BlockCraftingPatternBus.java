package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.NEGuiIds;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;

public class BlockCraftingPatternBus extends BlockFormedDirectionalModernModel {

    public BlockCraftingPatternBus(String id, String modelName, String formedModelName, String[] textureNames) {
        super(id, modelName, formedModelName, textureNames);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileCraftingPatternBus();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (player == null || player.isSneaking()) {
            return false;
        }
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileCraftingPatternBus) {
            player.openGui(NeoECOAE.instance, NEGuiIds.CRAFTING_PATTERN_BUS, world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        CraftingBlockDrops.dropInventory(world, x, y, z, world.getTileEntity(x, y, z));
        super.breakBlock(world, x, y, z, block, meta);
    }
}
