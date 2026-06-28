package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;

public class BlockDirectionalModernModel extends BlockModernModel {

    public BlockDirectionalModernModel(String id, String modelName, String[] textureNames) {
        super(id, modelName, textureNames);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z, BlockModelDrive.getFacingMetaFromYaw(placer.rotationYaw), 3);
    }

    @Override
    public ModelFacing getModelFacing(int meta) {
        return ModelFacing.fromMeta(meta);
    }
}
