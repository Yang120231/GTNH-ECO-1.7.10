package cn.dancingsnow.neoecoae.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;

public class BlockCraftingHatch extends BlockFormedTexturedMachine {

    private final boolean input;

    public BlockCraftingHatch(String normalTextureName, String formedTextureName, boolean input) {
        super(normalTextureName, formedTextureName);
        this.input = input;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileCraftingHatch(this.input);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        CraftingBlockDrops.dropInventory(world, x, y, z, world.getTileEntity(x, y, z));
        super.breakBlock(world, x, y, z, block, meta);
    }
}
