package cn.dancingsnow.neoecoae.platform.ae2.multiblock;

import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.impl.storage.StorageFaults;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

/**
 * Adapts AE2's rectangular discovery protocol. Dimensions, accepted parts and formation rules are supplied by
 * the imported calculators. Every probe checks chunk presence before touching the native world.
 */
public abstract class MBCalculator<T extends IAEMultiBlock<C>, C extends IAECluster> {

    private static final ThreadLocal<IAECluster> MODIFICATION = new ThreadLocal<>();
    protected final T target;
    private final StorageFaults faults = new StorageFaults();

    protected MBCalculator(T target) {
        this.target = target;
    }

    public static boolean isModificationInProgress() {
        return MODIFICATION.get() != null;
    }

    public static void setModificationInProgress(IAECluster cluster) {
        if (cluster == null) MODIFICATION.remove();
        else MODIFICATION.set(cluster);
    }

    public void calculateMultiblock(ServerWorldView level, BlockPos location) {
        if (isModificationInProgress() || !target.isValid() || !level.hasChunkAt(location)) return;
        try {
            BlockPos min = location, max = location;
            for (Direction side : new Direction[] { Direction.WEST, Direction.DOWN, Direction.NORTH })
                while (validAt(level, min.relative(side))) min = min.relative(side);
            for (Direction side : new Direction[] { Direction.EAST, Direction.UP, Direction.SOUTH })
                while (validAt(level, max.relative(side))) max = max.relative(side);
            if (!checkMultiblockScale(min, max) || !unownedBoundary(level, min, max)
                || !verifyInternalStructure(level, min, max)) {
                disconnect();
                return;
            }
            C cluster = target.getCluster();
            if (cluster != null && (!cluster.getBoundsMin()
                .equals(min)
                || !cluster.getBoundsMax()
                    .equals(max))) {
                disconnect();
                cluster = null;
            }
            boolean created = cluster == null;
            if (created) {
                cluster = createCluster(level, min, max);
                updateBlockEntities(cluster, level, min, max);
                if (target.getCluster() != cluster) {
                    cluster.destroy();
                    return;
                }
            }
            cluster.updateStatus(created);
            faults.recovered("discovery");
        } catch (RuntimeException failure) {
            disconnect();
            faults.report("discovery", location + ": " + failure, level.getGameTime(), failure);
        }
    }

    private boolean validAt(ServerWorldView level, BlockPos pos) {
        return level.hasChunkAt(pos) && isValidBlockEntity(level.getBlockEntity(pos));
    }

    private boolean unownedBoundary(ServerWorldView level, BlockPos min, BlockPos max) {
        for (Direction side : Direction.values()) {
            BlockPos from, to;
            switch (side) {
                case WEST:
                    from = new BlockPos(min.getX() - 1, min.getY(), min.getZ());
                    to = new BlockPos(min.getX() - 1, max.getY(), max.getZ());
                    break;
                case EAST:
                    from = new BlockPos(max.getX() + 1, min.getY(), min.getZ());
                    to = new BlockPos(max.getX() + 1, max.getY(), max.getZ());
                    break;
                case DOWN:
                    from = new BlockPos(min.getX(), min.getY() - 1, min.getZ());
                    to = new BlockPos(max.getX(), min.getY() - 1, max.getZ());
                    break;
                case UP:
                    from = new BlockPos(min.getX(), max.getY() + 1, min.getZ());
                    to = new BlockPos(max.getX(), max.getY() + 1, max.getZ());
                    break;
                case NORTH:
                    from = new BlockPos(min.getX(), min.getY(), min.getZ() - 1);
                    to = new BlockPos(max.getX(), max.getY(), min.getZ() - 1);
                    break;
                case SOUTH:
                    from = new BlockPos(min.getX(), min.getY(), max.getZ() + 1);
                    to = new BlockPos(max.getX(), max.getY(), max.getZ() + 1);
                    break;
                default:
                    throw new AssertionError(side);
            }
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) if (validAt(level, pos)) return false;
        }
        return true;
    }

    public void updateMultiblockAfterNeighborUpdate(ServerWorldView level, BlockPos owner, BlockPos changed) {
        calculateMultiblock(level, owner);
    }

    public void disconnect() {
        target.disconnect(true);
    }

    public abstract boolean checkMultiblockScale(BlockPos min, BlockPos max);

    public abstract C createCluster(ServerWorldView level, BlockPos min, BlockPos max);

    public abstract boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max);

    public abstract void updateBlockEntities(C cluster, ServerWorldView level, BlockPos min, BlockPos max);

    public abstract boolean isValidBlockEntity(TileEntity tile);
}
