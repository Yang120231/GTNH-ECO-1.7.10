package cn.dancingsnow.neoecoae.multiblock.placement;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;

@com.github.bsideup.jabel.Desugar
public record WorldPlannedBlock(BlockPos worldPos, BlockState targetState, ItemStack requiredItem,
    ECOMultiblockRequirement requirement) {

    public WorldPlannedBlock(BlockPos worldPos, BlockState targetState, ItemStack requiredItem) {
        this(worldPos, targetState, requiredItem, ECOMultiblockRequirement.of(targetState));
    }
}
