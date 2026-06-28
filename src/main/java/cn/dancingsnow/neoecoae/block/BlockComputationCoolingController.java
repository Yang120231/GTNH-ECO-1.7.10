package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public class BlockComputationCoolingController extends BlockTieredModernModel {

    public BlockComputationCoolingController(String id, String modelName, String formedModelName,
        String mirroredFormedModelName, String[] textureNames, ModelFacing inventoryModelFacing,
        ECOControllerTier tier) {
        super(id, modelName, formedModelName, mirroredFormedModelName, textureNames, inventoryModelFacing, tier);
    }

    @Override
    public ModelFacing getFormedModelFacing(int meta, boolean mirrored) {
        return rotateClockwise(this.getModelFacing(meta));
    }

    private static ModelFacing rotateClockwise(ModelFacing facing) {
        switch (facing) {
            case NORTH:
                return ModelFacing.EAST;
            case EAST:
                return ModelFacing.SOUTH;
            case SOUTH:
                return ModelFacing.WEST;
            case WEST:
            default:
                return ModelFacing.NORTH;
        }
    }
}
