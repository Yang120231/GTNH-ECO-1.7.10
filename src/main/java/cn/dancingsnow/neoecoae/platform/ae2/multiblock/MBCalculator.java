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
        // A cluster that is still tearing down must not be rebuilt from inside its own destruction.
        C current = target.getCluster();
        if (current != null && current.isDestroyed()) return;
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
                // Unlike AE2, release the old cluster first: members park running CPUs only when their
                // cluster becomes null, and restore them once the replacement cluster is attached.
                disconnect();
                cluster = null;
            }
            boolean created = cluster == null;
            if (created) {
                cluster = createCluster(level, min, max);
                // Same guard as AE2: member updates must not recursively recalculate this or other structures.
                setModificationInProgress(cluster);
                updateBlockEntities(cluster, level, min, max);
                if (target.getCluster() != cluster) {
                    setModificationInProgress(null);
                    cluster.destroy();
                    return;
                }
            } else {
                setModificationInProgress(cluster);
            }
            cluster.updateStatus(created);
            faults.recovered("discovery");
        } catch (RuntimeException failure) {
            setModificationInProgress(null);
            disconnect();
            faults.report("discovery", location + ": " + failure, level.getGameTime(), failure);
        } finally {
            setModificationInProgress(null);
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

    /**
     * AE2's neighbor filter. Forge 1.7.10 does not report which neighbor changed, so callers that only know
     * their own position pass {@code changed == owner} and the six adjacent positions are inspected instead.
     */
    public void updateMultiblockAfterNeighborUpdate(ServerWorldView level, BlockPos owner, BlockPos changed) {
        C cluster = target.getCluster();
        boolean recheck;
        if (cluster == null) {
            // The neighbor may have been a valid part in a wrong placement, or the other way around.
            recheck = true;
        } else if (!changed.equals(owner)) {
            recheck = affectsCluster(level, cluster, changed);
        } else {
            recheck = false;
            for (Direction side : Direction.values()) {
                if (affectsCluster(level, cluster, owner.relative(side))) {
                    recheck = true;
                    break;
                }
            }
        }
        if (recheck) calculateMultiblock(level, owner);
    }

    /**
     * Inside the bounds every position must still hold a member of this cluster; outside them only a newly
     * valid part can extend the structure.
     */
    private boolean affectsCluster(ServerWorldView level, C cluster, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        TileEntity tile = level.getBlockEntity(pos);
        if (isWithinBounds(pos, cluster.getBoundsMin(), cluster.getBoundsMax()))
            return !(tile instanceof appeng.me.cluster.IAEMultiBlock)
                || ((appeng.me.cluster.IAEMultiBlock) tile).getCluster() != cluster;
        return isValidBlockEntity(tile);
    }

    private static boolean isWithinBounds(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getY() >= min.getY()
            && pos.getZ() >= min.getZ()
            && pos.getX() <= max.getX()
            && pos.getY() <= max.getY()
            && pos.getZ() <= max.getZ();
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
