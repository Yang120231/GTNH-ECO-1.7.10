package cn.dancingsnow.neoecoae.multiblock;

import static cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.Pos;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class CraftingFormationPattern implements ECOFormationPattern {

    static final CraftingFormationPattern INSTANCE = new CraftingFormationPattern();

    private CraftingFormationPattern() {}

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
            NEBlocks.craftingCasing)
            || !validateCasing(
                world,
                c.offset(directions.expandSide),
                directions.top,
                directions.down,
                NEBlocks.craftingCasing)
            || !validateCasing(
                world,
                c.offset(directions.back),
                directions.top,
                directions.down,
                NEBlocks.craftingCasing)
            || !validateCasing(
                world,
                c.offset(directions.back)
                    .offset(directions.expandSide),
                directions.top,
                directions.down,
                NEBlocks.craftingCasing)) {
            return ECOFormationResult.failed("crafting casing frame");
        }
        Pos interfacePos = c.offset(directions.back)
            .offset(directions.interfaceSide);
        if (!isBlock(world, interfacePos, NEBlocks.craftingInterface)
            || !isBlock(world, interfacePos.offset(directions.top), NEBlocks.inputHatch)
            || !isBlock(world, interfacePos.offset(directions.down), NEBlocks.outputHatch)) {
            return ECOFormationResult.failed("crafting interface/hatches");
        }
        if (!isBlock(world, c.offset(directions.top), NEBlocks.craftingCasing)
            || !isBlock(world, c.offset(directions.down), NEBlocks.craftingCasing)) {
            return ECOFormationResult.failed("crafting controller cap");
        }

        Pos workerStart = c.offset(directions.expandSide)
            .offset(directions.expandSide);
        Pos workerEnd = validateBlockLine(
            world,
            directions.expandSide,
            workerStart,
            NEBlocks.craftingWorker,
            directions.front);
        if (workerEnd == null) {
            return ECOFormationResult.failed("crafting worker line");
        }
        Pos upperParallelEnd = validateTieredLine(
            world,
            directions.expandSide,
            workerStart.offset(directions.top),
            tier,
            directions.front,
            craftingParallelCores());
        Pos lowerParallelEnd = validateTieredLine(
            world,
            directions.expandSide,
            workerStart.offset(directions.down),
            tier,
            directions.front,
            craftingParallelCores());
        Pos ventEnd = validateBlockLine(
            world,
            directions.expandSide,
            workerStart.offset(directions.back),
            NEBlocks.craftingVent,
            directions.back);
        Pos upperPatternEnd = validateBlockLine(
            world,
            directions.expandSide,
            workerStart.offset(directions.back)
                .offset(directions.top),
            NEBlocks.craftingPatternBus,
            directions.back);
        Pos lowerPatternEnd = validateBlockLine(
            world,
            directions.expandSide,
            workerStart.offset(directions.back)
                .offset(directions.down),
            NEBlocks.craftingPatternBus,
            directions.back);
        if (upperParallelEnd == null || lowerParallelEnd == null
            || ventEnd == null
            || upperPatternEnd == null
            || lowerPatternEnd == null) {
            return ECOFormationResult.failed("crafting repeat lines");
        }

        List<Pos> endCasings = new ArrayList<>();
        endCasings.add(workerEnd.offset(directions.expandSide));
        endCasings.add(upperParallelEnd.offset(directions.expandSide));
        endCasings.add(lowerParallelEnd.offset(directions.expandSide));
        endCasings.add(upperPatternEnd.offset(directions.expandSide));
        endCasings.add(lowerPatternEnd.offset(directions.expandSide));
        endCasings.add(ventEnd.offset(directions.expandSide));
        if (!ensureSameSurface(endCasings)) {
            return ECOFormationResult.failed("crafting tail surface");
        }
        if (!validateBlocks(world, endCasings, NEBlocks.craftingCasing)) {
            return ECOFormationResult.failed("crafting tail casing");
        }
        return ECOFormationResult.formed(
            directions.mirrored,
            this.hiddenBlocks(c, directions),
            this.formedMembers(
                workerStart,
                upperParallelEnd,
                lowerParallelEnd,
                workerStart.offset(directions.back)
                    .offset(directions.top),
                upperPatternEnd,
                workerStart.offset(directions.back)
                    .offset(directions.down),
                lowerPatternEnd,
                workerStart.offset(directions.back),
                ventEnd,
                endCasings,
                tier,
                directions));
    }

    private List<ECOFormationBlockPos> hiddenBlocks(Pos controller, FormationDirections directions) {
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
        return hidden;
    }

    List<ECOFormationBlockPos> formedMembers(Pos workerStart, Pos upperParallelEnd, Pos lowerParallelEnd,
        Pos upperPatternStart, Pos upperPatternEnd, Pos lowerPatternStart, Pos lowerPatternEnd, Pos ventStart,
        Pos ventEnd, List<Pos> tailCasings, ECOControllerTier tier, FormationDirections directions) {
        List<ECOFormationBlockPos> formedMembers = new ArrayList<>();
        addLine(formedMembers, workerStart, upperParallelEnd.offset(directions.down));
        addLine(formedMembers, workerStart.offset(directions.top), upperParallelEnd, tier);
        addLine(formedMembers, workerStart.offset(directions.down), lowerParallelEnd, tier);
        addLine(formedMembers, ventStart, ventEnd);
        addLine(formedMembers, upperPatternStart, upperPatternEnd);
        addLine(formedMembers, lowerPatternStart, lowerPatternEnd);
        for (Pos tailCasing : tailCasings) {
            formedMembers.add(tailCasing.toPublicPos());
        }
        return formedMembers;
    }
}
