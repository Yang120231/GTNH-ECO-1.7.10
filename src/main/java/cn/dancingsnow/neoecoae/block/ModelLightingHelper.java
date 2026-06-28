package cn.dancingsnow.neoecoae.block;

import net.minecraft.world.World;

final class ModelLightingHelper {

    private ModelLightingHelper() {}

    static void updateNeighborLighting(World world, int x, int y, int z) {
        if (world == null) {
            return;
        }

        world.func_147451_t(x, y, z);
        world.func_147451_t(x, y - 1, z);
        world.func_147451_t(x, y + 1, z);
        world.func_147451_t(x - 1, y, z);
        world.func_147451_t(x + 1, y, z);
        world.func_147451_t(x, y, z - 1);
        world.func_147451_t(x, y, z + 1);
        world.markBlockRangeForRenderUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }
}
