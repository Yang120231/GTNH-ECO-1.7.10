package cn.dancingsnow.neoecoae.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.TileCraftingWorker;

public class BlockCraftingWorker extends BlockFormedDirectionalModernModel {

    public BlockCraftingWorker(String id, String modelName, String formedModelName, String[] textureNames) {
        super(id, modelName, formedModelName, textureNames);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileCraftingWorker();
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        CraftingBlockDrops.dropInventory(world, x, y, z, world.getTileEntity(x, y, z));
        super.breakBlock(world, x, y, z, block, meta);
    }
}
