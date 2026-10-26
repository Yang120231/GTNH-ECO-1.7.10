package cn.dancingsnow.neoecoae.multiblock.placement;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

public class MultiBlockPlanContext extends MultiBlockContext {

    private final List<PlannedBlock> plannedBlocks = new ArrayList<>();

    public MultiBlockPlanContext(int repeats) {
        this.repeats = repeats;
    }

    @Override
    public void setBlock(BlockPos pos, BlockState blockState) {
        plannedBlocks.add(new PlannedBlock(pos.immutable(), blockState, new ItemStack(blockState.getBlock())));
    }

    @Override
    public void setRequirement(BlockPos pos, ECOMultiblockRequirement requirement) {
        plannedBlocks.add(
            new PlannedBlock(
                pos.immutable(),
                requirement.defaultState(),
                new ItemStack(
                    requirement.defaultState()
                        .getBlock()),
                requirement));
    }

    @Override
    public WorldView getLevel() {
        return null;
    }

    @Override
    public List<BlockPos> allBlocks() {
        return plannedBlocks.stream()
            .map(PlannedBlock::relativePos)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public boolean isFormed() {
        return false;
    }

    public List<PlannedBlock> getPlannedBlocks() {
        return plannedBlocks;
    }
}
