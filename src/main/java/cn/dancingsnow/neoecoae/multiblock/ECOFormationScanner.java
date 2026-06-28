package cn.dancingsnow.neoecoae.multiblock;

import java.util.ArrayList;
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

public final class ECOFormationScanner {

    private static final int STORAGE_MAX_LENGTH = 15;
    private static final int CRAFTING_MAX_LENGTH = 15;
    private static final int COMPUTATION_MAX_LENGTH = 15;

    private ECOFormationScanner() {}

    public static ECOFormationResult scan(TileECOController controller) {
        World world = controller.getWorldObj();
        if (world == null) {
            return ECOFormationResult.failed("no world");
        }

        int x = controller.xCoord;
        int y = controller.yCoord;
        int z = controller.zCoord;
        if (hasAdjacentController(world, x, y, z)) {
            return ECOFormationResult.failed("adjacent controller");
        }

        ForgeDirection front = controller.getFacing()
            .getDirection();
        ForgeDirection back = front.getOpposite();
        ForgeDirection top = ForgeDirection.UP;
        ForgeDirection down = ForgeDirection.DOWN;
        ForgeDirection left = rotateClockwise(front);
        ForgeDirection right = left.getOpposite();

        ECOFormationResult normal = verify(controller, front, back, top, down, left, right, false);
        if (normal.isFormed()) {
            return normal;
        }

        ECOFormationResult mirrored = verify(controller, front, back, top, down, right, left, true);
        if (mirrored.isFormed()) {
            return mirrored;
        }
        return ECOFormationResult.failed(normal.getMessage());
    }

    private static ECOFormationResult verify(TileECOController controller, ForgeDirection front, ForgeDirection back,
        ForgeDirection top, ForgeDirection down, ForgeDirection interfaceSide, ForgeDirection expandSide,
        boolean mirrored) {
        switch (controller.getSubsystem()) {
            case STORAGE:
                return verifyStorage(controller, front, back, top, down, interfaceSide, expandSide, mirrored);
            case CRAFTING:
                return verifyCrafting(controller, front, back, top, down, interfaceSide, expandSide, mirrored);
            case COMPUTATION:
                return verifyComputation(controller, front, back, top, down, interfaceSide, expandSide, mirrored);
            default:
                return ECOFormationResult.failed("unknown subsystem");
        }
    }

    private static ECOFormationResult verifyStorage(TileECOController controller, ForgeDirection front,
        ForgeDirection back, ForgeDirection top, ForgeDirection down, ForgeDirection staticSide,
        ForgeDirection expandSide, boolean mirrored) {
        World world = controller.getWorldObj();
        Pos c = Pos.of(controller);
        ECOControllerTier tier = controller.getTier();

        if (!validateCasing(world, c.offset(staticSide), top, down, NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage side casing");
        }
        if (!validateCasing(world, c.offset(back), top, down, NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage back casing");
        }
        if (!validateInterface(
            world,
            c.offset(staticSide)
                .offset(back),
            top,
            down,
            NEBlocks.storageInterface,
            NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage interface column");
        }
        if (!isBlock(world, c.offset(top), NEBlocks.storageCasing)
            || !isBlock(world, c.offset(down), NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage controller cap");
        }

        Pos storageStart = c.offset(expandSide)
            .offset(top);
        Pos storageEnd = expandTowards(
            world,
            c.offset(expandSide)
                .offset(down),
            expandSide,
            NEBlocks.ecoDrive,
            front);
        if (!validateBlocks(world, storageStart, storageEnd, NEBlocks.ecoDrive, front)) {
            return ECOFormationResult.failed("storage drive line");
        }

        Pos ventStart = c.offset(expandSide)
            .offset(back);
        Pos ventEnd = validateBlockLine(world, expandSide, ventStart, NEBlocks.storageVent, back);
        if (ventEnd == null) {
            return ECOFormationResult.failed("storage vent line");
        }

        Pos upperEnergyStart = c.offset(back)
            .offset(top)
            .offset(expandSide);
        Pos upperEnergyEnd = validateTieredLine(world, expandSide, upperEnergyStart, tier, back, energyCells());
        if (upperEnergyEnd == null) {
            return ECOFormationResult.failed("storage upper energy line");
        }

        Pos lowerEnergyStart = c.offset(back)
            .offset(down)
            .offset(expandSide);
        Pos lowerEnergyEnd = validateTieredLine(world, expandSide, lowerEnergyStart, tier, back, energyCells());
        if (lowerEnergyEnd == null) {
            return ECOFormationResult.failed("storage lower energy line");
        }

        Pos tailCasing = storageEnd.offset(expandSide)
            .offset(top);
        List<Pos> tails = new ArrayList<Pos>();
        tails.add(upperEnergyEnd.offset(expandSide));
        tails.add(lowerEnergyEnd.offset(expandSide));
        tails.add(ventEnd.offset(expandSide));
        tails.add(tailCasing);
        tails.add(tailCasing.offset(top));
        tails.add(tailCasing.offset(down));
        if (!ensureSameSurface(tails)) {
            return ECOFormationResult.failed("storage tail surface");
        }
        if (!validateBlocks(world, tails, NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage tail casing");
        }
        return ECOFormationResult.formed(
            mirrored,
            storageHiddenBlocks(c, front, top, down, staticSide, expandSide, back),
            storageFormedMembers(storageStart, storageEnd));
    }

    private static ECOFormationResult verifyCrafting(TileECOController controller, ForgeDirection front,
        ForgeDirection back, ForgeDirection top, ForgeDirection down, ForgeDirection interfaceSide,
        ForgeDirection expandSide, boolean mirrored) {
        World world = controller.getWorldObj();
        Pos c = Pos.of(controller);
        ECOControllerTier tier = controller.getTier();

        if (!validateCasing(world, c.offset(interfaceSide), top, down, NEBlocks.craftingCasing)
            || !validateCasing(world, c.offset(expandSide), top, down, NEBlocks.craftingCasing)
            || !validateCasing(world, c.offset(back), top, down, NEBlocks.craftingCasing)
            || !validateCasing(
                world,
                c.offset(back)
                    .offset(expandSide),
                top,
                down,
                NEBlocks.craftingCasing)) {
            return ECOFormationResult.failed("crafting casing frame");
        }
        Pos interfacePos = c.offset(back)
            .offset(interfaceSide);
        if (!isBlock(world, interfacePos, NEBlocks.craftingInterface)
            || !isBlock(world, interfacePos.offset(top), NEBlocks.inputHatch)
            || !isBlock(world, interfacePos.offset(down), NEBlocks.outputHatch)) {
            return ECOFormationResult.failed("crafting interface/hatches");
        }
        if (!isBlock(world, c.offset(top), NEBlocks.craftingCasing)
            || !isBlock(world, c.offset(down), NEBlocks.craftingCasing)) {
            return ECOFormationResult.failed("crafting controller cap");
        }

        Pos workerStart = c.offset(expandSide)
            .offset(expandSide);
        Pos workerEnd = validateBlockLine(world, expandSide, workerStart, NEBlocks.craftingWorker, front);
        if (workerEnd == null) {
            return ECOFormationResult.failed("crafting worker line");
        }
        Pos upperParallelEnd = validateTieredLine(
            world,
            expandSide,
            workerStart.offset(top),
            tier,
            front,
            craftingParallelCores());
        Pos lowerParallelEnd = validateTieredLine(
            world,
            expandSide,
            workerStart.offset(down),
            tier,
            front,
            craftingParallelCores());
        Pos ventEnd = validateBlockLine(world, expandSide, workerStart.offset(back), NEBlocks.craftingVent, back);
        Pos upperPatternEnd = validateBlockLine(
            world,
            expandSide,
            workerStart.offset(back)
                .offset(top),
            NEBlocks.craftingPatternBus,
            back);
        Pos lowerPatternEnd = validateBlockLine(
            world,
            expandSide,
            workerStart.offset(back)
                .offset(down),
            NEBlocks.craftingPatternBus,
            back);
        if (upperParallelEnd == null || lowerParallelEnd == null
            || ventEnd == null
            || upperPatternEnd == null
            || lowerPatternEnd == null) {
            return ECOFormationResult.failed("crafting repeat lines");
        }

        List<Pos> endCasings = new ArrayList<Pos>();
        endCasings.add(workerEnd.offset(expandSide));
        endCasings.add(upperParallelEnd.offset(expandSide));
        endCasings.add(lowerParallelEnd.offset(expandSide));
        endCasings.add(upperPatternEnd.offset(expandSide));
        endCasings.add(lowerPatternEnd.offset(expandSide));
        endCasings.add(ventEnd.offset(expandSide));
        if (!ensureSameSurface(endCasings)) {
            return ECOFormationResult.failed("crafting tail surface");
        }
        if (!validateBlocks(world, endCasings, NEBlocks.craftingCasing)) {
            return ECOFormationResult.failed("crafting tail casing");
        }
        return ECOFormationResult.formed(
            mirrored,
            craftingHiddenBlocks(c, front, top, down, interfaceSide, expandSide, back),
            craftingFormedMembers(
                workerStart,
                upperParallelEnd,
                lowerParallelEnd,
                workerStart.offset(back)
                    .offset(top),
                upperPatternEnd,
                workerStart.offset(back)
                    .offset(down),
                lowerPatternEnd,
                workerStart.offset(back),
                ventEnd,
                endCasings,
                expandSide,
                top,
                down));
    }

    private static ECOFormationResult verifyComputation(TileECOController controller, ForgeDirection front,
        ForgeDirection back, ForgeDirection top, ForgeDirection down, ForgeDirection interfaceSide,
        ForgeDirection expandSide, boolean mirrored) {
        World world = controller.getWorldObj();
        Pos c = Pos.of(controller);
        ECOControllerTier tier = controller.getTier();

        if (!validateCasing(world, c.offset(interfaceSide), top, down, NEBlocks.computationCasing)
            || !validateCasing(world, c.offset(expandSide), top, down, NEBlocks.computationCasing)
            || !validateCasing(world, c.offset(back), top, down, NEBlocks.computationCasing)
            || !validateCasing(
                world,
                c.offset(back)
                    .offset(expandSide),
                top,
                down,
                NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation casing frame");
        }
        if (!validateInterface(
            world,
            c.offset(back)
                .offset(interfaceSide),
            top,
            down,
            NEBlocks.computationInterface,
            NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation interface column");
        }
        if (!isBlock(world, c.offset(top), NEBlocks.computationCasing)
            || !isBlock(world, c.offset(down), NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation controller cap");
        }

        Pos connectorStart = c.offset(expandSide)
            .offset(expandSide);
        Pos connectorEnd = validateBlockLine(world, expandSide, connectorStart, NEBlocks.computationTransmitter, front);
        if (connectorEnd == null) {
            return ECOFormationResult.failed("computation transmitter line");
        }
        Pos threadingStart = connectorStart.offset(back);
        Pos threadingEnd = validateTieredLine(
            world,
            expandSide,
            threadingStart,
            tier,
            back,
            computationThreadingCores());
        Pos upperParallelEnd = validateTieredLine(
            world,
            expandSide,
            threadingStart.offset(top),
            tier,
            back,
            computationParallelCores());
        Pos lowerParallelEnd = validateTieredLine(
            world,
            expandSide,
            threadingStart.offset(down),
            tier,
            back,
            computationParallelCores());
        Pos upperDriveEnd = validateBlockLine(
            world,
            expandSide,
            connectorStart.offset(top),
            NEBlocks.computationDrive,
            front);
        Pos lowerDriveEnd = validateBlockLine(
            world,
            expandSide,
            connectorStart.offset(down),
            NEBlocks.computationDrive,
            front);
        if (threadingEnd == null || upperParallelEnd == null
            || lowerParallelEnd == null
            || upperDriveEnd == null
            || lowerDriveEnd == null) {
            return ECOFormationResult.failed("computation repeat lines");
        }

        List<Pos> tails = new ArrayList<Pos>();
        tails.add(connectorEnd);
        tails.add(threadingEnd);
        tails.add(upperDriveEnd);
        tails.add(lowerDriveEnd);
        tails.add(upperParallelEnd);
        tails.add(lowerParallelEnd);
        if (!ensureSameSurface(tails)) {
            return ECOFormationResult.failed("computation tail surface");
        }

        Pos coolerPos = connectorEnd.offset(expandSide);
        if (!isTieredBlock(world, coolerPos, tier, expandSide, coolingControllers())) {
            return ECOFormationResult.failed("computation cooling controller");
        }

        List<Pos> tailCasings = new ArrayList<Pos>();
        tailCasings.add(threadingEnd.offset(expandSide));
        tailCasings.add(upperDriveEnd.offset(expandSide));
        tailCasings.add(lowerDriveEnd.offset(expandSide));
        tailCasings.add(upperParallelEnd.offset(expandSide));
        tailCasings.add(lowerParallelEnd.offset(expandSide));
        if (!validateBlocks(world, tailCasings, NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation tail casing");
        }
        return ECOFormationResult.formed(
            mirrored,
            computationHiddenBlocks(c, front, top, down, interfaceSide, expandSide, back, tailCasings),
            computationFormedMembers(
                connectorStart,
                connectorEnd,
                threadingStart,
                threadingEnd,
                upperParallelEnd,
                lowerParallelEnd,
                upperDriveEnd,
                lowerDriveEnd,
                coolerPos,
                tier,
                expandSide,
                top,
                down));
    }

    private static List<ECOFormationBlockPos> storageHiddenBlocks(Pos controller, ForgeDirection front,
        ForgeDirection top, ForgeDirection down, ForgeDirection staticSide, ForgeDirection expandSide,
        ForgeDirection back) {
        List<ECOFormationBlockPos> hidden = new ArrayList<ECOFormationBlockPos>();
        addColumn(hidden, controller.offset(staticSide), top, down);
        addColumn(hidden, controller.offset(back), top, down);
        addColumn(
            hidden,
            controller.offset(staticSide)
                .offset(back),
            top,
            down);
        hidden.add(
            controller.offset(top)
                .toPublicPos());
        hidden.add(
            controller.offset(down)
                .toPublicPos());
        return hidden;
    }

    private static List<ECOFormationBlockPos> craftingHiddenBlocks(Pos controller, ForgeDirection front,
        ForgeDirection top, ForgeDirection down, ForgeDirection interfaceSide, ForgeDirection expandSide,
        ForgeDirection back) {
        List<ECOFormationBlockPos> hidden = new ArrayList<ECOFormationBlockPos>();
        addControllerModelVolume(hidden, controller, front, interfaceSide, expandSide, back, top, down);
        addColumn(hidden, controller.offset(interfaceSide), top, down);
        addColumn(hidden, controller.offset(expandSide), top, down);
        addColumn(hidden, controller.offset(back), top, down);
        addColumn(
            hidden,
            controller.offset(back)
                .offset(expandSide),
            top,
            down);
        addColumn(
            hidden,
            controller.offset(back)
                .offset(interfaceSide),
            top,
            down);
        return hidden;
    }

    private static List<ECOFormationBlockPos> computationHiddenBlocks(Pos controller, ForgeDirection front,
        ForgeDirection top, ForgeDirection down, ForgeDirection interfaceSide, ForgeDirection expandSide,
        ForgeDirection back, List<Pos> tailCasings) {
        List<ECOFormationBlockPos> hidden = new ArrayList<ECOFormationBlockPos>();
        addControllerModelVolume(hidden, controller, front, interfaceSide, expandSide, back, top, down);
        addColumn(hidden, controller.offset(interfaceSide), top, down);
        addColumn(hidden, controller.offset(expandSide), top, down);
        addColumn(hidden, controller.offset(back), top, down);
        addColumn(
            hidden,
            controller.offset(back)
                .offset(expandSide),
            top,
            down);
        addColumn(
            hidden,
            controller.offset(back)
                .offset(interfaceSide),
            top,
            down);
        for (Pos tailCasing : tailCasings) {
            hidden.add(tailCasing.toPublicPos());
        }

        return hidden;
    }

    private static List<ECOFormationBlockPos> craftingFormedMembers(Pos workerStart, Pos upperParallelEnd,
        Pos lowerParallelEnd, Pos upperPatternStart, Pos upperPatternEnd, Pos lowerPatternStart, Pos lowerPatternEnd,
        Pos ventStart, Pos ventEnd, List<Pos> tailCasings, ForgeDirection expandSide, ForgeDirection top,
        ForgeDirection down) {
        List<ECOFormationBlockPos> formedMembers = new ArrayList<ECOFormationBlockPos>();
        addLine(formedMembers, workerStart, upperParallelEnd.offset(down));
        addLine(formedMembers, workerStart.offset(top), upperParallelEnd);
        addLine(formedMembers, workerStart.offset(down), lowerParallelEnd);
        addLine(formedMembers, ventStart, ventEnd);
        addLine(formedMembers, upperPatternStart, upperPatternEnd);
        addLine(formedMembers, lowerPatternStart, lowerPatternEnd);
        for (Pos tailCasing : tailCasings) {
            formedMembers.add(tailCasing.toPublicPos());
        }
        return formedMembers;
    }

    private static List<ECOFormationBlockPos> storageFormedMembers(Pos storageStart, Pos storageEnd) {
        List<ECOFormationBlockPos> formedMembers = new ArrayList<ECOFormationBlockPos>();
        addLine(formedMembers, storageStart, storageEnd);
        return formedMembers;
    }

    private static List<ECOFormationBlockPos> computationFormedMembers(Pos connectorStart, Pos connectorEnd,
        Pos threadingStart, Pos threadingEnd, Pos upperParallelEnd, Pos lowerParallelEnd, Pos upperDriveEnd,
        Pos lowerDriveEnd, Pos coolerPos, ECOControllerTier tier, ForgeDirection expandSide, ForgeDirection top,
        ForgeDirection down) {
        List<ECOFormationBlockPos> formedMembers = new ArrayList<ECOFormationBlockPos>();
        addLine(formedMembers, connectorStart, connectorEnd, tier);
        addLine(formedMembers, threadingStart, threadingEnd, tier);
        addLine(formedMembers, threadingStart.offset(top), upperParallelEnd, tier);
        addLine(formedMembers, threadingStart.offset(down), lowerParallelEnd, tier);
        addLine(formedMembers, connectorStart.offset(top), upperDriveEnd, tier);
        addLine(formedMembers, connectorStart.offset(down), lowerDriveEnd, tier);
        formedMembers.add(coolerPos.toPublicPos(tier));
        return formedMembers;
    }

    private static void addControllerModelVolume(List<ECOFormationBlockPos> hidden, Pos controller,
        ForgeDirection front, ForgeDirection staticSide, ForgeDirection expandSide, ForgeDirection back,
        ForgeDirection top, ForgeDirection down) {
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

    private static void addColumn(List<ECOFormationBlockPos> hidden, Pos center, ForgeDirection top,
        ForgeDirection down) {
        hidden.add(center.toPublicPos());
        hidden.add(
            center.offset(top)
                .toPublicPos());
        hidden.add(
            center.offset(down)
                .toPublicPos());
    }

    private static void addLine(List<ECOFormationBlockPos> hidden, Pos from, Pos to) {
        addLine(hidden, from, to, null);
    }

    private static void addLine(List<ECOFormationBlockPos> hidden, Pos from, Pos to, ECOControllerTier tier) {
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

    private static boolean hasAdjacentController(World world, int x, int y, int z) {
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

    private static boolean validateCasing(World world, Pos center, ForgeDirection top, ForgeDirection down,
        Block casing) {
        return isBlock(world, center, casing) && isBlock(world, center.offset(top), casing)
            && isBlock(world, center.offset(down), casing);
    }

    private static boolean validateInterface(World world, Pos interfacePos, ForgeDirection top, ForgeDirection down,
        Block interfaceBlock, Block casing) {
        return isBlock(world, interfacePos, interfaceBlock) && isBlock(world, interfacePos.offset(top), casing)
            && isBlock(world, interfacePos.offset(down), casing);
    }

    private static Pos validateBlockLine(World world, ForgeDirection direction, Pos start, Block block,
        ForgeDirection facing) {
        if (!isBlock(world, start, block, facing)) {
            return null;
        }
        return expandTowards(world, start, direction, block, facing);
    }

    private static Pos validateTieredLine(World world, ForgeDirection direction, Pos start, ECOControllerTier maxTier,
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

    private static Pos expandTowards(World world, Pos start, ForgeDirection direction, Block block,
        ForgeDirection facing) {
        Pos current = start;
        while (isBlock(world, current.offset(direction), block, facing)) {
            current = current.offset(direction);
        }
        return current;
    }

    private static boolean validateBlocks(World world, Pos from, Pos to, Block block, ForgeDirection facing) {
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

    private static boolean validateBlocks(World world, List<Pos> positions, Block block) {
        for (Pos pos : positions) {
            if (!isBlock(world, pos, block)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlock(World world, Pos pos, Block block) {
        return world.getBlock(pos.x, pos.y, pos.z) == block;
    }

    private static boolean isBlock(World world, Pos pos, Block block, ForgeDirection facing) {
        if (!isBlock(world, pos, block)) {
            return false;
        }
        return ModelFacing.fromMeta(world.getBlockMetadata(pos.x, pos.y, pos.z))
            .getDirection() == facing;
    }

    private static boolean isTieredBlock(World world, Pos pos, ECOControllerTier maxTier, ForgeDirection facing,
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

    private static boolean ensureSameSurface(List<Pos> positions) {
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

    private static ForgeDirection rotateClockwise(ForgeDirection direction) {
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

    private static Block[] energyCells() {
        return new Block[] { NEBlocks.energyCellL4, NEBlocks.energyCellL6, NEBlocks.energyCellL9 };
    }

    private static Block[] craftingParallelCores() {
        return new Block[] { NEBlocks.craftingParallelCoreL4, NEBlocks.craftingParallelCoreL6,
            NEBlocks.craftingParallelCoreL9 };
    }

    private static Block[] computationParallelCores() {
        return new Block[] { NEBlocks.computationParallelCoreL4, NEBlocks.computationParallelCoreL6,
            NEBlocks.computationParallelCoreL9 };
    }

    private static Block[] computationThreadingCores() {
        return new Block[] { NEBlocks.computationThreadingCoreL4, NEBlocks.computationThreadingCoreL6,
            NEBlocks.computationThreadingCoreL9 };
    }

    private static Block[] coolingControllers() {
        return new Block[] { NEBlocks.computationCoolingControllerL4, NEBlocks.computationCoolingControllerL6,
            NEBlocks.computationCoolingControllerL9 };
    }

    private static final class Pos {

        private final int x;
        private final int y;
        private final int z;

        private Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static Pos of(TileECOController controller) {
            return new Pos(controller.xCoord, controller.yCoord, controller.zCoord);
        }

        private Pos offset(ForgeDirection direction) {
            return new Pos(this.x + direction.offsetX, this.y + direction.offsetY, this.z + direction.offsetZ);
        }

        private ECOFormationBlockPos toPublicPos() {
            return new ECOFormationBlockPos(this.x, this.y, this.z);
        }

        private ECOFormationBlockPos toPublicPos(ECOControllerTier tier) {
            return new ECOFormationBlockPos(this.x, this.y, this.z, tier);
        }
    }
}
