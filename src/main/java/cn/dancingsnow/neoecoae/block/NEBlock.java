package cn.dancingsnow.neoecoae.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;

public class NEBlock extends Block {

    public NEBlock(Material material) {
        super(material);
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        int blockX = x - direction.offsetX;
        int blockY = y - direction.offsetY;
        int blockZ = z - direction.offsetZ;
        if (ECOFormationVisibility.isHidden(world, blockX, blockY, blockZ)) {
            return false;
        }
        return super.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!ECOStorageStructureRemovalGuard.canRemoveOrNotify(world, player, x, y, z)) {
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        if (!ECOStorageStructureRemovalGuard.canRemove(world, x, y, z)) {
            return;
        }
        super.onBlockExploded(world, x, y, z, explosion);
    }
}
