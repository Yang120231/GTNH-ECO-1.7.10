package cn.dancingsnow.neoecoae.platform.render;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

/** Read-only render information supplied by the ported business adapters. */
public interface ModelRenderState {

    boolean isHidden(IBlockAccess world, int x, int y, int z);

    boolean isModelBlock(Block block);
}
