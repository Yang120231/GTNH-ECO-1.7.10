package cn.dancingsnow.neoecoae.multiblock;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.block.BlockECOController;
import cn.dancingsnow.neoecoae.block.BlockTieredModernModel;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class FormationPatternHelper {

    private FormationPatternHelper() {}

    static void addControllerModelVolume(List<ECOFormationBlockPos> hidden, Pos controller, ForgeDirection staticSide,
        ForgeDirection expandSide, ForgeDirection back, ForgeDirection top, ForgeDirection down) {
        ForgeDirection[] horizontal = new ForgeDirection[] { staticSide, expandSide };
        ForgeDirection[] vertical = new ForgeDirection[] { down, ForgeDirection.UNKNOWN, top };
        ForgeDirection[] depth = new ForgeDirection[] { ForgeDirection.UNKNOWN, back };
        for (ForgeDirection side : horizontal) {
            for (ForgeDirection y : vertical) {
                for (ForgeDirection z : depth) {
                    Pos pos = controller.offset(side);
                    if (y != ForgeDirection.UNKNOWN) {
                        pos = pos.offset(y);
                    }
                    if (z != ForgeDirection.UNKNOWN) {
                        pos = pos.offset(z);
                    }
                    hidden.add(pos.toPublicPos());
                }
            }
        }

        hidden.add(
            controller.offset(top)
                .toPublicPos());
        hidden.add(
            controller.offset(down)
                .toPublicPos());
    }

    static void addColumn(List<ECOFormationBlockPos> hidden, Pos center, ForgeDirection top, ForgeDirection down) {
        hidden.add(center.toPublicPos());
        hidden.add(
            center.offset(top)
                .toPublicPos());
        hidden.add(
            center.offset(down)
                .toPublicPos());
    }

    static void addLine(List<ECOFormationBlockPos> hidden, Pos from, Pos to) {
        addLine(hidden, from, to, null);
    }

    static void addLine(List<ECOFormationBlockPos> hidden, Pos from, Pos to, ECOControllerTier tier) {
        int minX = Math.min(from.x, to.x);
        int minY = Math.min(from.y, to.y);
        int minZ = Math.min(from.z, to.z);
        int maxX = Math.max(from.x, to.x);
        int maxY = Math.max(from.y, to.y);
        int maxZ = Math.max(from.z, to.z);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    hidden.add(new ECOFormationBlockPos(x, y, z, tier));
                }
            }
        }
    }

    static boolean hasAdjacentController(World world, int x, int y, int z) {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (world.getBlock(
                x + direction.offsetX,
                y + direction.offsetY,
                z + direction.offsetZ) instanceof BlockECOController) {
                return true;
            }
        }
        return false;
    }

    static boolean validateCasing(World world, Pos center, ForgeDirection top, ForgeDirection down, Block casing) {
        return isBlock(world, center, casing) && isBlock(world, center.offset(top), casing)
            && isBlock(world, center.offset(down), casing);
    }

    static boolean validateInterface(World world, Pos interfacePos, ForgeDirection top, ForgeDirection down,
        Block interfaceBlock, Block casing) {
        return isBlock(world, interfacePos, interfaceBlock) && isBlock(world, interfacePos.offset(top), casing)
            && isBlock(world, interfacePos.offset(down), casing);
    }

    static Pos validateBlockLine(World world, ForgeDirection direction, Pos start, Block block, ForgeDirection facing) {
        if (!isBlock(world, start, block, facing)) {
            return null;
        }
        return expandTowards(world, start, direction, block, facing);
    }

    static Pos validateTieredLine(World world, ForgeDirection direction, Pos start, ECOControllerTier maxTier,
        ForgeDirection facing, Block[] allowedBlocks) {
        if (!isTieredBlock(world, start, maxTier, facing, allowedBlocks)) {
            return null;
        }

        Pos current = start;
        while (isTieredBlock(world, current.offset(direction), maxTier, facing, allowedBlocks)) {
            current = current.offset(direction);
        }
        return current;
    }

    static Pos expandTowards(World world, Pos start, ForgeDirection direction, Block block, ForgeDirection facing) {
        Pos current = start;
        while (isBlock(world, current.offset(direction), block, facing)) {
            current = current.offset(direction);
        }
        return current;
    }

    static boolean validateBlocks(World world, Pos from, Pos to, Block block, ForgeDirection facing) {
        int minX = Math.min(from.x, to.x);
        int minY = Math.min(from.y, to.y);
        int minZ = Math.min(from.z, to.z);
        int maxX = Math.max(from.x, to.x);
        int maxY = Math.max(from.y, to.y);
        int maxZ = Math.max(from.z, to.z);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isBlock(world, new Pos(x, y, z), block, facing)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean validateBlocks(World world, List<Pos> positions, Block block) {
        for (Pos pos : positions) {
            if (!isBlock(world, pos, block)) {
                return false;
            }
        }
        return true;
    }

    static boolean isBlock(World world, Pos pos, Block block) {
        return world.getBlock(pos.x, pos.y, pos.z) == block;
    }

    static boolean isBlock(World world, Pos pos, Block block, ForgeDirection facing) {
        if (!isBlock(world, pos, block)) {
            return false;
        }
        return ModelFacing.fromMeta(world.getBlockMetadata(pos.x, pos.y, pos.z))
            .getDirection() == facing;
    }

    static boolean isTieredBlock(World world, Pos pos, ECOControllerTier maxTier, ForgeDirection facing,
        Block[] allowedBlocks) {
        Block block = world.getBlock(pos.x, pos.y, pos.z);
        boolean allowed = false;
        for (Block allowedBlock : allowedBlocks) {
            if (block == allowedBlock) {
                allowed = true;
                break;
            }
        }
        if (!allowed || !(block instanceof BlockTieredModernModel)) {
            return false;
        }
        BlockTieredModernModel tiered = (BlockTieredModernModel) block;
        return maxTier.supports(tiered.getTier()) && ModelFacing.fromMeta(world.getBlockMetadata(pos.x, pos.y, pos.z))
            .getDirection() == facing;
    }

    static boolean ensureSameSurface(List<Pos> positions) {
        int firstX = positions.get(0).x;
        int firstY = positions.get(0).y;
        int firstZ = positions.get(0).z;
        boolean sameX = true;
        boolean sameY = true;
        boolean sameZ = true;
        for (Pos pos : positions) {
            sameX &= pos.x == firstX;
            sameY &= pos.y == firstY;
            sameZ &= pos.z == firstZ;
        }
        return sameX || sameY || sameZ;
    }

    static ForgeDirection rotateClockwise(ForgeDirection direction) {
        switch (direction) {
            case NORTH:
                return ForgeDirection.EAST;
            case EAST:
                return ForgeDirection.SOUTH;
            case SOUTH:
                return ForgeDirection.WEST;
            case WEST:
                return ForgeDirection.NORTH;
            default:
                return direction;
        }
    }

    static Block[] energyCells() {
        return new Block[] { NEBlocks.energyCellL4, NEBlocks.energyCellL6, NEBlocks.energyCellL9 };
    }

    static Block[] craftingParallelCores() {
        return new Block[] { NEBlocks.craftingParallelCoreL4, NEBlocks.craftingParallelCoreL6,
            NEBlocks.craftingParallelCoreL9 };
    }

    static Block[] computationParallelCores() {
        return new Block[] { NEBlocks.computationParallelCoreL4, NEBlocks.computationParallelCoreL6,
            NEBlocks.computationParallelCoreL9 };
    }

    static Block[] computationThreadingCores() {
        return new Block[] { NEBlocks.computationThreadingCoreL4, NEBlocks.computationThreadingCoreL6,
            NEBlocks.computationThreadingCoreL9 };
    }

    static Block[] coolingControllers() {
        return new Block[] { NEBlocks.computationCoolingControllerL4, NEBlocks.computationCoolingControllerL6,
            NEBlocks.computationCoolingControllerL9 };
    }

    static final class Pos {

        final int x;
        final int y;
        final int z;

        Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Pos of(TileECOController controller) {
            return new Pos(controller.xCoord, controller.yCoord, controller.zCoord);
        }

        Pos offset(ForgeDirection direction) {
            return new Pos(this.x + direction.offsetX, this.y + direction.offsetY, this.z + direction.offsetZ);
        }

        ECOFormationBlockPos toPublicPos() {
            return new ECOFormationBlockPos(this.x, this.y, this.z);
        }

        ECOFormationBlockPos toPublicPos(ECOControllerTier tier) {
            return new ECOFormationBlockPos(this.x, this.y, this.z, tier);
        }
    }
}
