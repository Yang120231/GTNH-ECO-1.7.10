package cn.dancingsnow.neoecoae.multiblock.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirements;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.inventory.IItemHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.multiblock.NativePlacementPlatform;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public final class MultiBlockPlacementService {

    private static final int PLACEMENT_DELAY_TICKS = 1;

    public enum PlacementTickResult {
        WAITING,
        ADVANCED,
        COMPLETED,
        BLOCKED
    }

    private MultiBlockPlacementService() {}

    public static MultiBlockPlacementPlan preview(WorldView level, BlockPos controllerPos, BlockState controllerState,
        MultiBlockDefinition definition, int repeats, boolean mirrored) {
        Direction facing = controllerState
            .getOptionalValue(cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties.HORIZONTAL_FACING)
            .orElse(Direction.NORTH);
        MultiBlockPlanContext context = new MultiBlockPlanContext(repeats);
        definition.createLevel(context);

        List<WorldPlannedBlock> allBlocks = new ArrayList<>();
        List<WorldPlannedBlock> missingBlocks = new ArrayList<>();
        List<BlockPos> conflictPositions = new ArrayList<>();
        List<RequiredItem> requiredItems = new ArrayList<>();
        int reusedBlockCount = 0;

        for (PlannedBlock plannedBlock : context.getPlannedBlocks()) {
            if (plannedBlock.relativePos()
                .equals(MultiBlockRotation.CONTROLLER_ANCHOR)) {
                continue;
            }
            BlockPos worldPos = MultiBlockRotation
                .localToWorld(plannedBlock.relativePos(), controllerPos, facing, mirrored);
            BlockState targetState = MultiBlockRotation.rotateState(plannedBlock.targetState(), facing, mirrored);
            WorldPlannedBlock worldBlock = new WorldPlannedBlock(
                worldPos,
                targetState,
                plannedBlock.requiredItem()
                    .copy(),
                plannedBlock.requirement()
                    .transform(state -> MultiBlockRotation.rotateState(state, facing, mirrored)));
            allBlocks.add(worldBlock);

            BlockState existingState = level.getBlockState(worldPos);
            if (acceptsExisting(level, worldBlock, existingState)) {
                reusedBlockCount++;
                continue;
            }
            if (NativePlacementPlatform.replaceable(level, worldPos, existingState)) {
                missingBlocks.add(worldBlock);
                mergeItem(requiredItems, worldBlock.requiredItem());
                continue;
            }
            conflictPositions.add(worldPos);
        }

        return new MultiBlockPlacementPlan(
            allBlocks,
            missingBlocks,
            conflictPositions,
            requiredItems,
            reusedBlockCount);
    }

    public static boolean buildInstant(ServerWorldView level, MultiBlockPlacementPlan plan, EntityPlayerMP player) {
        if (!plan.getConflictPositions()
            .isEmpty() || (!player.capabilities.isCreativeMode && !hasRequiredItems(player, plan.getRequiredItems()))) {
            return false;
        }
        for (WorldPlannedBlock worldBlock : plan.getMissingBlocks()) {
            if (!placeBuildBlock(level, worldBlock, player)) return false;
        }
        return true;
    }

    public static MultiBlockBuildSession createBuildSession(ServerWorldView level, MultiBlockPlacementPlan plan) {
        return new MultiBlockBuildSession(plan.getMissingBlocks(), PLACEMENT_DELAY_TICKS);
    }

    public static PlacementTickResult tickBuild(ServerWorldView level, MultiBlockBuildSession session,
        EntityPlayerMP player) {
        if (session == null || session.isFinished()) {
            return PlacementTickResult.COMPLETED;
        }
        if (!session.tickDelay()) {
            return PlacementTickResult.WAITING;
        }

        if (!placeBuildBlock(level, session.getCurrentBlock(), player)) {
            return PlacementTickResult.BLOCKED;
        }
        session.advance(PLACEMENT_DELAY_TICKS);
        return session.isFinished() ? PlacementTickResult.COMPLETED : PlacementTickResult.ADVANCED;
    }

    private static boolean placeBuildBlock(ServerWorldView level, WorldPlannedBlock worldBlock, EntityPlayerMP player) {
        if (!level.hasChunkAt(worldBlock.worldPos())
            || !NativePlacementPlatform.insideWorldBounds(worldBlock.worldPos())
            || !NativePlacementPlatform.mayInteract(level, player, worldBlock)) return false;
        BlockState existingState = level.getBlockState(worldBlock.worldPos());
        if (acceptsExisting(level, worldBlock, existingState)) return true;
        if (!(NativePlacementPlatform.replaceable(level, worldBlock.worldPos(), existingState))) return false;
        if (!placeWithPermissionCheck(level, worldBlock, player)) return false;
        if (!player.capabilities.isCreativeMode && !consumeRequiredItem(player, worldBlock.requiredItem())) {
            level.setBlock(worldBlock.worldPos(), existingState, 3);
            return false;
        }
        playPlacementSound(level, worldBlock);
        return true;
    }

    public static boolean acceptsExisting(WorldView level, WorldPlannedBlock planned, BlockState actual) {
        var role = ECOMultiblockParts
            .acceptsExpected(ECOMultiblockParts.context(level, planned.worldPos()), planned.targetState());
        return ECOMultiblockRequirements.accepts(
            new ECOMultiblockRequirements.Context(level, planned.worldPos(), planned.targetState(), actual),
            role == null ? planned.requirement()
                .accepts(actual) : role);
    }

    /** Validates the same alternatives used by previews/building without mutating the world. */
    public static boolean matches(WorldView level, BlockPos controllerPos, BlockState controllerState,
        MultiBlockDefinition definition, int repeats, boolean mirrored) {
        var plan = preview(level, controllerPos, controllerState, definition, repeats, mirrored);
        return plan.getConflictPositions()
            .isEmpty()
            && plan.getMissingBlocks()
                .isEmpty();
    }

    public static boolean hasRequiredItems(EntityPlayer player, List<RequiredItem> requiredItems) {
        for (RequiredItem requiredItem : requiredItems) {
            if (countMatchingItems(player, requiredItem.stack()) < requiredItem.count()) {
                return false;
            }
        }
        return true;
    }

    public static int countMatchingItems(EntityPlayer player, ItemStack target) {
        ReferenceSet<IItemHandler> visitedHandlers = new ReferenceOpenHashSet<>();
        return countMatchingItems(java.util.Arrays.asList(player.inventory.mainInventory), target, visitedHandlers)
            + countMatchingItems(java.util.Collections.emptyList(), target, visitedHandlers);
    }

    private static boolean consumeRequiredItem(EntityPlayer player, ItemStack requiredItem) {
        ReferenceSet<IItemHandler> visitedHandlers = new ReferenceOpenHashSet<>();
        int remaining = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(requiredItem);
        remaining = consumeFromList(
            java.util.Arrays.asList(player.inventory.mainInventory),
            requiredItem,
            remaining,
            visitedHandlers);
        if (remaining > 0) {
            remaining = consumeFromList(java.util.Collections.emptyList(), requiredItem, remaining, visitedHandlers);
        }
        if (remaining > 0) {
            return false;
        }
        player.inventory.markDirty();
        return true;
    }

    private static int countMatchingItems(List<ItemStack> stacks, ItemStack target, Set<IItemHandler> visitedHandlers) {
        int count = 0;
        for (ItemStack stack : stacks) {
            count += countMatchingItems(stack, target, visitedHandlers);
        }
        return count;
    }

    private static int countMatchingItems(ItemStack stack, ItemStack target, Set<IItemHandler> visitedHandlers) {
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return 0;
        }

        int count = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItemSameComponents(stack, target)
            ? cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(stack)
            : 0;
        IItemHandler itemHandler = NativePlacementPlatform.itemInventory(stack);
        if (itemHandler == null || !visitedHandlers.add(itemHandler)) {
            return count;
        }

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            count += countMatchingItems(itemHandler.getStackInSlot(slot), target, visitedHandlers);
        }
        return count;
    }

    private static int consumeFromList(List<ItemStack> stacks, ItemStack target, int remaining,
        Set<IItemHandler> visitedHandlers) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                return 0;
            }
            remaining = consumeFromStack(stack, target, remaining, visitedHandlers);
        }
        return remaining;
    }

    private static int consumeFromStack(ItemStack stack, ItemStack target, int remaining,
        Set<IItemHandler> visitedHandlers) {
        if (remaining <= 0 || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return remaining;
        }

        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItemSameComponents(stack, target)) {
            int taken = Math.min(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(stack), remaining);
            cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.shrink(stack, taken);
            remaining -= taken;
            if (remaining <= 0) {
                return 0;
            }
        }

        IItemHandler itemHandler = NativePlacementPlatform.itemInventory(stack);
        if (itemHandler == null || !visitedHandlers.add(itemHandler)) {
            return remaining;
        }

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            if (remaining <= 0) {
                return 0;
            }

            ItemStack slotStack = itemHandler.getStackInSlot(slot);
            if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(slotStack)) {
                continue;
            }
            if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItemSameComponents(slotStack, target)) {
                int toExtract = Math
                    .min(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(slotStack), remaining);
                ItemStack extracted = itemHandler.extractItem(slot, toExtract, false);
                remaining -= cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(extracted);
                if (remaining <= 0) {
                    return 0;
                }
            }

            remaining = consumeFromStack(itemHandler.getStackInSlot(slot), target, remaining, visitedHandlers);
        }
        return remaining;
    }

    private static void mergeItem(List<RequiredItem> requiredItems, ItemStack toAdd) {
        for (int i = 0; i < requiredItems.size(); i++) {
            RequiredItem requiredItem = requiredItems.get(i);
            if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks
                .isSameItemSameComponents(requiredItem.stack(), toAdd)) {
                requiredItems
                    .set(i, requiredItem.grow(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(toAdd)));
                return;
            }
        }
        requiredItems.add(new RequiredItem(toAdd, cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(toAdd)));
    }

    private static boolean placeWithPermissionCheck(ServerWorldView level, WorldPlannedBlock worldBlock,
        EntityPlayerMP player) {
        return NativePlacementPlatform.placeWithPermissionCheck(level, worldBlock, player);
    }

    private static void playPlacementSound(ServerWorldView level, WorldPlannedBlock worldBlock) {
        NativePlacementPlatform.playPlacementSound(level, worldBlock);
    }
}
