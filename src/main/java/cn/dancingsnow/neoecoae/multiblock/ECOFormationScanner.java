package cn.dancingsnow.neoecoae.multiblock;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.tile.TileECOController;

public final class ECOFormationScanner {

    private ECOFormationScanner() {}

    public static ECOFormationResult scan(TileECOController controller) {
        World world = controller.getWorldObj();
        if (world == null) {
            return ECOFormationResult.failed("no world");
        }

        int x = controller.xCoord;
        int y = controller.yCoord;
        int z = controller.zCoord;
        if (FormationPatternHelper.hasAdjacentController(world, x, y, z)) {
            return ECOFormationResult.failed("adjacent controller");
        }

        ForgeDirection front = controller.getFacing()
            .getDirection();
        ForgeDirection back = front.getOpposite();
        ForgeDirection top = ForgeDirection.UP;
        ForgeDirection down = ForgeDirection.DOWN;
        ForgeDirection left = FormationPatternHelper.rotateClockwise(front);
        ForgeDirection right = left.getOpposite();

        ECOFormationResult normal = patternFor(controller)
            .verify(controller, new FormationDirections(front, back, top, down, left, right, false));
        if (normal.isFormed()) {
            return validateLengthAndTiers(controller, normal);
        }

        ECOFormationResult mirrored = patternFor(controller)
            .verify(controller, new FormationDirections(front, back, top, down, right, left, true));
        if (mirrored.isFormed()) {
            return validateLengthAndTiers(controller, mirrored);
        }
        return ECOFormationResult.failed(normal.getMessage());
    }

    private static ECOFormationResult validateLengthAndTiers(TileECOController controller, ECOFormationResult result) {
        java.util.List<ECOFormationBlockPos> members = new java.util.ArrayList<>();
        int repeated = 0;
        for (ECOFormationBlockPos pos : result.getFormedMemberBlocks()) {
            net.minecraft.block.Block block = controller.getWorldObj()
                .getBlock(pos.getX(), pos.getY(), pos.getZ());
            if (block == cn.dancingsnow.neoecoae.all.NEBlocks.craftingWorker
                || block == cn.dancingsnow.neoecoae.all.NEBlocks.computationTransmitter
                || block == cn.dancingsnow.neoecoae.all.NEBlocks.storageVent) repeated++;
            members.add(
                block instanceof cn.dancingsnow.neoecoae.block.BlockTieredModernModel tiered
                    ? new ECOFormationBlockPos(pos.getX(), pos.getY(), pos.getZ(), tiered.getTier())
                    : pos);
        }
        if (repeated > ECOStructureBuilder.MAX_LENGTH) return ECOFormationResult.failed("structure too long");
        return ECOFormationResult.formed(result.isMirrored(), result.getHiddenBlocks(), members);
    }

    private static ECOFormationPattern patternFor(TileECOController controller) {
        switch (controller.getSubsystem()) {
            case STORAGE:
                return StorageFormationPattern.INSTANCE;
            case CRAFTING:
                return CraftingFormationPattern.INSTANCE;
            case COMPUTATION:
                return ComputationFormationPattern.INSTANCE;
            default:
                return UnknownFormationPattern.INSTANCE;
        }
    }

    private static final class UnknownFormationPattern implements ECOFormationPattern {

        private static final UnknownFormationPattern INSTANCE = new UnknownFormationPattern();

        @Override
        public ECOFormationResult verify(TileECOController controller, FormationDirections directions) {
            return ECOFormationResult.failed("unknown subsystem");
        }
    }
}
