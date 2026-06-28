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
            return normal;
        }

        ECOFormationResult mirrored = patternFor(controller)
            .verify(controller, new FormationDirections(front, back, top, down, right, left, true));
        if (mirrored.isFormed()) {
            return mirrored;
        }
        return ECOFormationResult.failed(normal.getMessage());
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
