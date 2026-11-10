package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.CollisionContext;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.Shapes;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.VoxelShape;
import cn.dancingsnow.neoecoae.platform.registry.NativeSimpleModelBlock;

public class CasingBlock extends NativeSimpleModelBlock {

    public static final VoxelShape SHAPE = Shapes.block();

    public CasingBlock(cn.dancingsnow.neoecoae.platform.registry.NativeBlockProperties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, WorldView level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
