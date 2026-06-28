package cn.dancingsnow.neoecoae.multiblock;

import static cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.Pos;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class StorageFormationPattern implements ECOFormationPattern {

    static final StorageFormationPattern INSTANCE = new StorageFormationPattern();

    private StorageFormationPattern() {}

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
            NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage side casing");
        }
        if (!validateCasing(
            world,
            c.offset(directions.back),
            directions.top,
            directions.down,
            NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage back casing");
        }
        if (!validateInterface(
            world,
            c.offset(directions.interfaceSide)
                .offset(directions.back),
            directions.top,
            directions.down,
            NEBlocks.storageInterface,
            NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage interface column");
        }
        if (!isBlock(world, c.offset(directions.top), NEBlocks.storageCasing)
            || !isBlock(world, c.offset(directions.down), NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage controller cap");
        }

        Pos storageStart = c.offset(directions.expandSide)
            .offset(directions.top);
        Pos storageEnd = expandTowards(
            world,
            c.offset(directions.expandSide)
                .offset(directions.down),
            directions.expandSide,
            NEBlocks.ecoDrive,
            directions.front);
        if (!validateBlocks(world, storageStart, storageEnd, NEBlocks.ecoDrive, directions.front)) {
            return ECOFormationResult.failed("storage drive line");
        }

        Pos ventStart = c.offset(directions.expandSide)
            .offset(directions.back);
        Pos ventEnd = validateBlockLine(world, directions.expandSide, ventStart, NEBlocks.storageVent, directions.back);
        if (ventEnd == null) {
            return ECOFormationResult.failed("storage vent line");
        }

        Pos upperEnergyStart = c.offset(directions.back)
            .offset(directions.top)
            .offset(directions.expandSide);
        Pos upperEnergyEnd = validateTieredLine(
            world,
            directions.expandSide,
            upperEnergyStart,
            tier,
            directions.back,
            energyCells());
        if (upperEnergyEnd == null) {
            return ECOFormationResult.failed("storage upper energy line");
        }

        Pos lowerEnergyStart = c.offset(directions.back)
            .offset(directions.down)
            .offset(directions.expandSide);
        Pos lowerEnergyEnd = validateTieredLine(
            world,
            directions.expandSide,
            lowerEnergyStart,
            tier,
            directions.back,
            energyCells());
        if (lowerEnergyEnd == null) {
            return ECOFormationResult.failed("storage lower energy line");
        }

        Pos tailCasing = storageEnd.offset(directions.expandSide)
            .offset(directions.top);
        List<Pos> tails = new ArrayList<Pos>();
        tails.add(upperEnergyEnd.offset(directions.expandSide));
        tails.add(lowerEnergyEnd.offset(directions.expandSide));
        tails.add(ventEnd.offset(directions.expandSide));
        tails.add(tailCasing);
        tails.add(tailCasing.offset(directions.top));
        tails.add(tailCasing.offset(directions.down));
        if (!ensureSameSurface(tails)) {
            return ECOFormationResult.failed("storage tail surface");
        }
        if (!validateBlocks(world, tails, NEBlocks.storageCasing)) {
            return ECOFormationResult.failed("storage tail casing");
        }
        return ECOFormationResult.formed(
            directions.mirrored,
            this.hiddenBlocks(c, directions),
            this.formedMembers(storageStart, storageEnd));
    }

    private List<ECOFormationBlockPos> hiddenBlocks(Pos controller, FormationDirections directions) {
        List<ECOFormationBlockPos> hidden = new ArrayList<ECOFormationBlockPos>();
        addColumn(hidden, controller.offset(directions.interfaceSide), directions.top, directions.down);
        addColumn(hidden, controller.offset(directions.back), directions.top, directions.down);
        addColumn(
            hidden,
            controller.offset(directions.interfaceSide)
                .offset(directions.back),
            directions.top,
            directions.down);
        hidden.add(
            controller.offset(directions.top)
                .toPublicPos());
        hidden.add(
            controller.offset(directions.down)
                .toPublicPos());
        return hidden;
    }

    private List<ECOFormationBlockPos> formedMembers(Pos storageStart, Pos storageEnd) {
        List<ECOFormationBlockPos> formedMembers = new ArrayList<ECOFormationBlockPos>();
        addLine(formedMembers, storageStart, storageEnd);
        return formedMembers;
    }
}
