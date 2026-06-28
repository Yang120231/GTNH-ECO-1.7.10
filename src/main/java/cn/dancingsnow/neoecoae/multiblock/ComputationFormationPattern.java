package cn.dancingsnow.neoecoae.multiblock;

import static cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.Pos;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class ComputationFormationPattern implements ECOFormationPattern {

    static final ComputationFormationPattern INSTANCE = new ComputationFormationPattern();

    private ComputationFormationPattern() {}

    @Override
    public ECOFormationResult verify(TileECOController controller, FormationDirections directions) {
        World world = controller.getWorldObj();
        Pos c = Pos.of(controller);
        ECOControllerTier tier = controller.getTier();

        if (!validateCasing(
            world,
            c.offset(directions.interfaceSide),
            directions.top,
            directions.down,
            NEBlocks.computationCasing)
            || !validateCasing(
                world,
                c.offset(directions.expandSide),
                directions.top,
                directions.down,
                NEBlocks.computationCasing)
            || !validateCasing(
                world,
                c.offset(directions.back),
                directions.top,
                directions.down,
                NEBlocks.computationCasing)
            || !validateCasing(
                world,
                c.offset(directions.back)
                    .offset(directions.expandSide),
                directions.top,
                directions.down,
                NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation casing frame");
        }
        if (!validateInterface(
            world,
            c.offset(directions.back)
                .offset(directions.interfaceSide),
            directions.top,
            directions.down,
            NEBlocks.computationInterface,
            NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation interface column");
        }
        if (!isBlock(world, c.offset(directions.top), NEBlocks.computationCasing)
            || !isBlock(world, c.offset(directions.down), NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation controller cap");
        }

        Pos connectorStart = c.offset(directions.expandSide)
            .offset(directions.expandSide);
        Pos connectorEnd = validateBlockLine(
            world,
            directions.expandSide,
            connectorStart,
            NEBlocks.computationTransmitter,
            directions.front);
        if (connectorEnd == null) {
            return ECOFormationResult.failed("computation transmitter line");
        }
        Pos threadingStart = connectorStart.offset(directions.back);
        Pos threadingEnd = validateTieredLine(
            world,
            directions.expandSide,
            threadingStart,
            tier,
            directions.back,
            computationThreadingCores());
        Pos upperParallelEnd = validateTieredLine(
            world,
            directions.expandSide,
            threadingStart.offset(directions.top),
            tier,
            directions.back,
            computationParallelCores());
        Pos lowerParallelEnd = validateTieredLine(
            world,
            directions.expandSide,
            threadingStart.offset(directions.down),
            tier,
            directions.back,
            computationParallelCores());
        Pos upperDriveEnd = validateBlockLine(
            world,
            directions.expandSide,
            connectorStart.offset(directions.top),
            NEBlocks.computationDrive,
            directions.front);
        Pos lowerDriveEnd = validateBlockLine(
            world,
            directions.expandSide,
            connectorStart.offset(directions.down),
            NEBlocks.computationDrive,
            directions.front);
        if (threadingEnd == null || upperParallelEnd == null
            || lowerParallelEnd == null
            || upperDriveEnd == null
            || lowerDriveEnd == null) {
            return ECOFormationResult.failed("computation repeat lines");
        }

        List<Pos> tails = new ArrayList<>();
        tails.add(connectorEnd);
        tails.add(threadingEnd);
        tails.add(upperDriveEnd);
        tails.add(lowerDriveEnd);
        tails.add(upperParallelEnd);
        tails.add(lowerParallelEnd);
        if (!ensureSameSurface(tails)) {
            return ECOFormationResult.failed("computation tail surface");
        }

        Pos coolerPos = connectorEnd.offset(directions.expandSide);
        if (!isTieredBlock(world, coolerPos, tier, directions.expandSide, coolingControllers())) {
            return ECOFormationResult.failed("computation cooling controller");
        }

        List<Pos> tailCasings = new ArrayList<>();
        tailCasings.add(threadingEnd.offset(directions.expandSide));
        tailCasings.add(upperDriveEnd.offset(directions.expandSide));
        tailCasings.add(lowerDriveEnd.offset(directions.expandSide));
        tailCasings.add(upperParallelEnd.offset(directions.expandSide));
        tailCasings.add(lowerParallelEnd.offset(directions.expandSide));
        if (!validateBlocks(world, tailCasings, NEBlocks.computationCasing)) {
            return ECOFormationResult.failed("computation tail casing");
        }
        return ECOFormationResult.formed(
            directions.mirrored,
            this.hiddenBlocks(c, directions, tailCasings),
            this.formedMembers(
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
                directions));
    }

    private List<ECOFormationBlockPos> hiddenBlocks(Pos controller, FormationDirections directions,
        List<Pos> tailCasings) {
        List<ECOFormationBlockPos> hidden = new ArrayList<>();
        addControllerModelVolume(
            hidden,
            controller,
            directions.interfaceSide,
            directions.expandSide,
            directions.back,
            directions.top,
            directions.down);
        addColumn(hidden, controller.offset(directions.interfaceSide), directions.top, directions.down);
        addColumn(hidden, controller.offset(directions.expandSide), directions.top, directions.down);
        addColumn(hidden, controller.offset(directions.back), directions.top, directions.down);
        addColumn(
            hidden,
            controller.offset(directions.back)
                .offset(directions.expandSide),
            directions.top,
            directions.down);
        addColumn(
            hidden,
            controller.offset(directions.back)
                .offset(directions.interfaceSide),
            directions.top,
            directions.down);
        for (Pos tailCasing : tailCasings) {
            hidden.add(tailCasing.toPublicPos());
        }

        return hidden;
    }

    private List<ECOFormationBlockPos> formedMembers(Pos connectorStart, Pos connectorEnd, Pos threadingStart,
        Pos threadingEnd, Pos upperParallelEnd, Pos lowerParallelEnd, Pos upperDriveEnd, Pos lowerDriveEnd,
        Pos coolerPos, ECOControllerTier tier, FormationDirections directions) {
        List<ECOFormationBlockPos> formedMembers = new ArrayList<>();
        addLine(formedMembers, connectorStart, connectorEnd, tier);
        addLine(formedMembers, threadingStart, threadingEnd, tier);
        addLine(formedMembers, threadingStart.offset(directions.top), upperParallelEnd, tier);
        addLine(formedMembers, threadingStart.offset(directions.down), lowerParallelEnd, tier);
        addLine(formedMembers, connectorStart.offset(directions.top), upperDriveEnd, tier);
        addLine(formedMembers, connectorStart.offset(directions.down), lowerDriveEnd, tier);
        formedMembers.add(coolerPos.toPublicPos(tier));
        return formedMembers;
    }
}
