package cn.dancingsnow.neoecoae.multiblock.placement;

import java.util.List;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

public class MultiBlockPlacementPlan {

    private final List<WorldPlannedBlock> allBlocks;
    private final List<WorldPlannedBlock> missingBlocks;
    private final List<BlockPos> conflictPositions;
    private final List<RequiredItem> requiredItems;
    private final int reusedBlockCount;

    public MultiBlockPlacementPlan(List<WorldPlannedBlock> allBlocks, List<WorldPlannedBlock> missingBlocks,
        List<BlockPos> conflictPositions, List<RequiredItem> requiredItems, int reusedBlockCount) {
        this.allBlocks = allBlocks;
        this.missingBlocks = missingBlocks;
        this.conflictPositions = conflictPositions;
        this.requiredItems = requiredItems;
        this.reusedBlockCount = reusedBlockCount;
    }

    public int getRequiredItemCount() {
        return requiredItems.stream()
            .mapToInt(RequiredItem::count)
            .sum();
    }

    public List<WorldPlannedBlock> getAllBlocks() {
        return allBlocks;
    }

    public List<WorldPlannedBlock> getMissingBlocks() {
        return missingBlocks;
    }

    public List<BlockPos> getConflictPositions() {
        return conflictPositions;
    }

    public List<RequiredItem> getRequiredItems() {
        return requiredItems;
    }

    public int getReusedBlockCount() {
        return reusedBlockCount;
    }
}
