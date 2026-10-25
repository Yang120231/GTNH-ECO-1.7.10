package cn.dancingsnow.neoecoae.platform.render;

import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

public final class ModelRenderBridge {

    private static final ModelRenderState EMPTY = new ModelRenderState() {

        @Override
        public boolean isHidden(IBlockAccess world, int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isModelBlock(Block block) {
            return false;
        }
    };

    private static volatile ModelRenderState state = EMPTY;

    private ModelRenderBridge() {}

    public static void bind(ModelRenderState renderState) {
        state = Objects.requireNonNull(renderState, "renderState");
    }

    public static void reset() {
        state = EMPTY;
    }

    public static boolean isHidden(IBlockAccess world, int x, int y, int z) {
        return state.isHidden(world, x, y, z);
    }

    public static boolean isModelBlock(Block block) {
        return state.isModelBlock(block);
    }
}
