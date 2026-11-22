package cn.dancingsnow.neoecoae.platform.ae2.multiblock;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.impl.storage.StorageFaults;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

/**
 * Discovers rectangular ECO structures without loading chunks. A drained batch validates each discovered
 * structure once; the calculator decides how to validate its layout and whether it can retain the live cluster.
 */
public abstract class MBCalculator<T extends IAEMultiBlock<C>, C extends IAECluster> {

    private static final ThreadLocal<IAECluster> MODIFICATION = new ThreadLocal<>();
    private static final Map<ServerWorldView, Map<BlockPos, MBCalculator<?, ?>>> PENDING = new IdentityHashMap<>();
    private static final Direction[] MIN_SIDES = { Direction.WEST, Direction.DOWN, Direction.NORTH };
    private static final Direction[] MAX_SIDES = { Direction.EAST, Direction.UP, Direction.SOUTH };
    private static final long CHECK_BUDGET_NANOS = 5_000_000L;
    private static final boolean PROFILE_CHECKS = Boolean.getBoolean("neoecoae.profileMultiblocks");
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
        calculateMultiblock(level, location, null);
    }

    public void requestMultiblockCheck(ServerWorldView level, BlockPos location) {
        if (target.isValid() && !level.getServer()
            .isStopped()) {
            PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                .put(location.immutable(), this);
        }
    }

    public static void tick(ServerWorldView level) {
        Map<BlockPos, MBCalculator<?, ?>> remaining = PENDING.remove(level);
        if (remaining == null || level.getServer()
            .isStopped()) return;
        pauseGridRebuilds();
        long started = System.nanoTime();
        int queued = remaining.size();
        int checked = 0;
        try {
            while (!remaining.isEmpty()) {
                var entry = remaining.entrySet()
                    .iterator()
                    .next();
                BlockPos position = entry.getKey();
                MBCalculator<?, ?> calculator = entry.getValue();
                remaining.remove(position);
                if (level.getBlockEntity(position) == calculator.target) {
                    checked++;
                    calculator.calculateMultiblock(level, position, remaining);
                }
                if (System.nanoTime() - started >= CHECK_BUDGET_NANOS) break;
            }
        } finally {
            if (!remaining.isEmpty()) {
                Map<BlockPos, MBCalculator<?, ?>> newRequests = PENDING.put(level, remaining);
                if (newRequests != null) remaining.putAll(newRequests);
            }
            resumeGridRebuilds();
            if (PROFILE_CHECKS) cn.dancingsnow.neoecoae.NeoECOAE.LOG.info(
                "ECO structure batch tick={} queued={} checked={} deferred={} total_us={}",
                level.getGameTime(),
                queued,
                checked,
                remaining.size(),
                (System.nanoTime() - started) / 1_000);
        }
    }

    public static void clearPending(ServerWorldView level) {
        PENDING.remove(level);
    }

    public static boolean hasPendingChecks(ServerWorldView level) {
        return PENDING.containsKey(level);
    }

    public static void clearPending() {
        PENDING.clear();
    }

    protected boolean deferNeighborChecks() {
        return false;
    }

    protected int maxDiscoverySpan() {
        return Integer.MAX_VALUE;
    }

    private void calculateMultiblock(ServerWorldView level, BlockPos location,
        Map<BlockPos, MBCalculator<?, ?>> remaining) {
        if (isModificationInProgress() || !target.isValid() || !level.hasChunkAt(location)) return;
        // A cluster that is still tearing down must not be rebuilt from inside its own destruction.
        C current = target.getCluster();
        if (current != null && current.isDestroyed()) return;
        // Members join or leave the grid one node at a time; see pauseGridRebuilds.
        pauseGridRebuilds();
        var profiler = level.getServer()
            .nativeServer().theProfiler;
        long started = PROFILE_CHECKS ? System.nanoTime() : 0;
        profiler.startSection("ecoStructureCheck");
        try {
            calculate(level, location, remaining);
        } finally {
            profiler.endSection();
            resumeGridRebuilds();
            if (PROFILE_CHECKS) cn.dancingsnow.neoecoae.NeoECOAE.LOG.info(
                "ECO structure check tick={} calculator={} pos={}, {}, {} elapsed_us={}",
                level.getGameTime(),
                getClass().getSimpleName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                (System.nanoTime() - started) / 1_000);
        }
    }

    /**
     * rv3's {@code CraftingGridCache.addNode/removeNode} rebuild every pattern of the grid synchronously for each
     * crafting provider that joins or leaves it. A multiblock attaches its members one by one, so a structure with
     * many pattern buses next to a large network rebuilt the full pattern list once per bus. While paused, AE2 only
     * records which grids need a rebuild and runs each one once on resume. Calls nest; server thread only.
     */
    public static void pauseGridRebuilds() {
        appeng.me.cache.CraftingGridCache.pauseRebuilds();
    }

    public static void resumeGridRebuilds() {
        appeng.me.cache.CraftingGridCache.unpauseRebuilds();
    }

    private void calculate(ServerWorldView level, BlockPos location, Map<BlockPos, MBCalculator<?, ?>> remaining) {
        MBCalculator<T, C> calculator = this;
        try {
            C current = target.getCluster();
            if (current != null && revalidateCluster(level, current)) {
                BlockPos min = current.getBoundsMin(), max = current.getBoundsMax();
                discardMemberChecks(min, max, remaining);
                boolean claimedOwner = shareOwner(level, min, max);
                setModificationInProgress(current);
                updateExistingCluster(current, claimedOwner);
                faults.recovered("discovery");
                return;
            }
            BlockPos min = location, max = location;
            for (Direction side : MIN_SIDES)
                while (span(location, min, side) < maxDiscoverySpan() && validAt(level, min.relative(side)))
                    min = min.relative(side);
            for (Direction side : MAX_SIDES)
                while (span(max, min, side) < maxDiscoverySpan() && validAt(level, max.relative(side)))
                    max = max.relative(side);
            calculator = discoveryOwner(level, min, max);
            calculator.applyBounds(level, min, max, remaining);
            faults.recovered("discovery");
        } catch (RuntimeException failure) {
            setModificationInProgress(null);
            calculator.disconnect();
            faults.report("discovery", location + ": " + failure, level.getGameTime(), failure);
        } finally {
            setModificationInProgress(null);
        }
    }

    protected MBCalculator<T, C> discoveryOwner(ServerWorldView level, BlockPos min, BlockPos max) {
        return this;
    }

    protected boolean revalidateCluster(ServerWorldView level, C cluster) {
        return false;
    }

    protected final boolean validateKnownBounds(ServerWorldView level, BlockPos min, BlockPos max,
        BooleanSupplier validate) {
        return checkMultiblockScale(min, max) && unownedBoundary(level, min, max) && profileValidation(level, validate);
    }

    private static int span(BlockPos first, BlockPos second, Direction side) {
        return switch (side.getAxis()) {
            case X -> first.getX() - second.getX() + 1;
            case Y -> first.getY() - second.getY() + 1;
            case Z -> first.getZ() - second.getZ() + 1;
        };
    }

    protected static boolean chunksLoaded(ServerWorldView level, BlockPos min, BlockPos max) {
        var chunks = level.nativeWorld()
            .getChunkProvider();
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++) {
                if (!chunks.chunkExists(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private void applyBounds(ServerWorldView level, BlockPos min, BlockPos max,
        Map<BlockPos, MBCalculator<?, ?>> remaining) {
        if (!target.isValid() || target.getCluster() != null && target.getCluster()
            .isDestroyed()) return;
        if (!checkMultiblockScale(min, max) || !unownedBoundary(level, min, max)) {
            invalidateStructure();
            return;
        }
        discardMemberChecks(min, max, remaining);
        if (!profileValidation(level, () -> verifyInternalStructure(level, min, max))) {
            invalidateStructure();
            return;
        }
        // Must run before members expose their sides: an unowned part fails AE2's security check against a
        // secured neighbor network (or a member that already joined it).
        boolean claimedOwner = shareOwner(level, min, max);
        C cluster = target.getCluster();
        if (cluster != null) {
            setModificationInProgress(cluster);
            if (!cluster.getBoundsMin()
                .equals(min)
                || !cluster.getBoundsMax()
                    .equals(max)
                || !sameMembers(level, cluster)) {
                if (reconcileCluster(level, cluster, min, max)) return;
                disconnect();
                cluster = null;
            }
        }
        if (cluster == null) {
            cluster = createCluster(level, min, max);
            setModificationInProgress(cluster);
            updateBlockEntities(cluster, level, min, max);
            if (target.getCluster() != cluster) {
                cluster.destroy();
                return;
            }
            updateNewCluster(cluster);
        } else updateExistingCluster(cluster, claimedOwner);
    }

    private void discardMemberChecks(BlockPos min, BlockPos max, Map<BlockPos, MBCalculator<?, ?>> remaining) {
        if (remaining == null || remaining.isEmpty()) return;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            MBCalculator<?, ?> other = remaining.get(pos);
            if (other != null && other.getClass() == getClass()
                && other.target instanceof TileEntity member
                && isValidBlockEntity(member)) remaining.remove(pos);
        }
    }

    private static boolean profileValidation(ServerWorldView level, BooleanSupplier validate) {
        var profiler = level.getServer()
            .nativeServer().theProfiler;
        profiler.startSection("ecoStructureValidation");
        try {
            return validate.getAsBoolean();
        } finally {
            profiler.endSection();
        }
    }

    /**
     * Gives unowned members the owner of the structure. Parts placed before owners were recorded (or placed by
     * creative StructureLib builds) have no AE2 player id; the first owned member found decides, so one owned part
     * - e.g. a part re-placed or right-clicked by a player - is enough to repair an old structure.
     */
    private static boolean shareOwner(ServerWorldView level, BlockPos min, BlockPos max) {
        int owner = cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity.UNOWNED;
        boolean anyUnowned = false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            TileEntity tile = level.getBlockEntity(pos);
            if (!(tile instanceof cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity)) continue;
            int id = ((cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity) tile).getOwnerPlayerId();
            if (id < 0) anyUnowned = true;
            else if (owner < 0) owner = id;
            if (owner >= 0 && anyUnowned) break;
        }
        if (owner < 0 || !anyUnowned) return false;
        boolean changed = false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            TileEntity tile = level.getBlockEntity(pos);
            if (tile instanceof cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity)
                changed |= ((cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity) tile)
                    .adoptOwner(owner);
        }
        return changed;
    }

    protected boolean sameMembers(ServerWorldView level, C cluster) {
        return true;
    }

    protected boolean reconcileCluster(ServerWorldView level, C cluster, BlockPos min, BlockPos max) {
        return false;
    }

    protected void updateNewCluster(C cluster) {
        cluster.updateStatus(true);
    }

    protected void invalidateStructure() {
        disconnect();
    }

    protected void updateExistingCluster(C cluster, boolean claimedOwner) {
        cluster.updateStatus(claimedOwner);
    }

    private boolean validAt(ServerWorldView level, BlockPos pos) {
        return isValidBlockEntity(level.getBlockEntity(pos));
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
        if (recheck) {
            if (deferNeighborChecks()) requestMultiblockCheck(level, owner);
            else calculateMultiblock(level, owner);
        }
    }

    /**
     * Inside the bounds every position must still hold a member of this cluster; outside them only a newly
     * valid part can extend the structure.
     */
    private boolean affectsCluster(ServerWorldView level, C cluster, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        TileEntity tile = level.getBlockEntity(pos);
        if (cluster.containsPosition(pos)) return !(tile instanceof appeng.me.cluster.IAEMultiBlock)
            || ((appeng.me.cluster.IAEMultiBlock) tile).getCluster() != cluster;
        return isValidBlockEntity(tile);
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
