package cn.dancingsnow.neoecoae.multiblock.definition;

import java.util.List;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

public abstract class MultiBlockContext {

    protected int repeats;

    public abstract void setBlock(BlockPos pos, BlockState blockState);

    public void setRequirement(BlockPos pos, ECOMultiblockRequirement requirement) {
        setBlock(pos, requirement.defaultState());
    }

    public abstract WorldView getLevel();

    public abstract List<BlockPos> allBlocks();

    public abstract boolean isFormed();

    public int getRepeats() {
        return repeats;
    }
}
