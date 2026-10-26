package cn.dancingsnow.neoecoae.multiblock.definition;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.block.Block;

import com.google.common.collect.ImmutableList;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

public class MultiBlockDefinition {

    private final List<BlockInstruction> builderActions;

    private final Holder<Block> owner;

    private final Component name;

    private final int expandMin;

    private final int expandMax;

    private final BiConsumer<BlockPos, WorldView> onFormed;

    public MultiBlockDefinition(List<BlockInstruction> builderActions, Holder<Block> owner, Component name,
        int expandMin, int expandMax, BiConsumer<BlockPos, WorldView> onFormed) {
        this.builderActions = builderActions;
        this.owner = owner;
        this.name = name;
        this.expandMin = expandMin;
        this.expandMax = expandMax;
        this.onFormed = onFormed;
    }

    public static Builder builder(Holder<Block> owner) {
        return new Builder(owner);
    }

    public WorldView createLevel(MultiBlockContext context) {
        for (BlockInstruction builderAction : builderActions) {
            builderAction.accept(context);
        }
        if (context.isFormed()) {
            for (BlockPos pos : context.allBlocks()) {
                onFormed.accept(pos, context.getLevel());
            }
        }
        return context.getLevel();
    }

    /** Local slot requirements, including accepted alternatives, for inspection by addons. */
    public List<cn.dancingsnow.neoecoae.multiblock.placement.PlannedBlock> requirements(int repeats) {
        if (repeats < expandMin || repeats > expandMax) throw new IllegalArgumentException("Invalid structure length");
        var context = new cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlanContext(repeats);
        createLevel(context);
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(context.getPlannedBlocks());
    }

    public static class Builder {

        private final ImmutableList.Builder<BlockInstruction> builder = new ImmutableList.Builder<>();
        private final Holder<Block> owner;
        private Component name;
        private int expandMin = 1;
        private BiConsumer<BlockPos, WorldView> onFormed = (a, b) -> {};
        private int expandMax = 16;

        public Builder(Holder<Block> owner) {
            this.owner = owner;
            name = Component.literal(new net.minecraft.item.ItemStack(owner.value()).getDisplayName());
        }

        public Builder setRequirement(BlockPos pos, ECOMultiblockRequirement requirement) {
            builder.add(context -> context.setRequirement(pos, requirement));
            return this;
        }

        public Builder setRequirementRepeatable(BlockPos origin, Direction direction,
            ECOMultiblockRequirement requirement) {
            builder.add(
                context -> {
                    for (int i = 0; i < context.getRepeats(); i++)
                        context.setRequirement(origin.relative(direction, i), requirement);
                });
            return this;
        }

        public Builder setBlock(BlockPos pos, BlockState block) {
            builder.add(context -> context.setBlock(pos, block));
            return this;
        }

        public Builder setBlockRepeatable(BlockPos origin, Direction expandDirection, BlockState blockState) {
            builder.add(context -> {
                BlockPos.MutableBlockPos mutable = origin.mutable();
                for (int i = 0; i < context.getRepeats(); i++) {
                    mutable.set(
                        origin.getX() + expandDirection.getStepX() * i,
                        origin.getY() + expandDirection.getStepY() * i,
                        origin.getZ() + expandDirection.getStepZ() * i);
                    context.setBlock(mutable.immutable(), blockState);
                }
            });
            return this;
        }

        public Builder setBlockWithRepeatShifted(BlockPos origin, Direction expandDirection, int shift,
            BlockState blockState) {
            builder.add(context -> {
                int i = context.getRepeats() + shift;
                BlockPos pos = new BlockPos(
                    origin.getX() + expandDirection.getStepX() * i,
                    origin.getY() + expandDirection.getStepY() * i,
                    origin.getZ() + expandDirection.getStepZ() * i);
                context.setBlock(pos, blockState);
            });
            return this;
        }

        public Builder expandMax(int expandMax) {
            this.expandMax = expandMax;
            return this;
        }

        public Builder expandMin(int expandMin) {
            this.expandMin = expandMin;
            return this;
        }

        public Builder onFormed(BiConsumer<BlockPos, WorldView> levelBiConsumer) {
            this.onFormed = levelBiConsumer;
            return this;
        }

        public MultiBlockDefinition create() {
            MultiBlockDefinition def = new MultiBlockDefinition(
                builder.build(),
                owner,
                name,
                expandMin,
                expandMax,
                onFormed);
            NEMultiBlocks.DEFINITIONS.add(def);
            return def;
        }

        public MultiBlockDefinition create(Consumer<MultiBlockDefinition> onBuild) {
            MultiBlockDefinition def = new MultiBlockDefinition(
                builder.build(),
                owner,
                name,
                expandMin,
                expandMax,
                onFormed);
            onBuild.accept(def);
            return def;
        }
    }

    public Holder<Block> getOwner() {
        return owner;
    }

    public Component getName() {
        return name;
    }

    public int getExpandMin() {
        return expandMin;
    }

    public int getExpandMax() {
        return expandMax;
    }

    public BiConsumer<BlockPos, WorldView> getOnFormed() {
        return onFormed;
    }
}
