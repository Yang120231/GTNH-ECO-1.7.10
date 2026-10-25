package cn.dancingsnow.neoecoae.blocks;

import java.util.stream.Stream;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.BooleanOp;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.CollisionContext;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.Shapes;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.VoxelShape;
import cn.dancingsnow.neoecoae.platform.registry.NativeSimpleModelBlock;

public class CasingBlock extends NativeSimpleModelBlock {

    public static final VoxelShape SHAPE = Stream
        .of(
            Shapes.box(0, 0, 0, 12, 4, 4),
            Shapes.box(0, 0, 4, 4, 4, 16),
            Shapes.box(4, 0, 12, 16, 4, 16),
            Shapes.box(12, 0, 0, 16, 4, 12),
            Shapes.box(12, 12, 0, 16, 16, 12),
            Shapes.box(0, 12, 0, 12, 16, 4),
            Shapes.box(0, 12, 4, 4, 16, 16),
            Shapes.box(4, 12, 12, 16, 16, 16),
            Shapes.box(0, 4, 0, 4, 12, 4),
            Shapes.box(0, 4, 12, 4, 12, 16),
            Shapes.box(12, 4, 12, 16, 12, 16),
            Shapes.box(12, 4, 0, 16, 12, 4),
            Shapes.box(4, 4, 4, 12, 12, 12))
        .reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR))
        .get();

    public CasingBlock(cn.dancingsnow.neoecoae.platform.registry.NativeBlockProperties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, WorldView level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
