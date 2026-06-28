package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public class BlockTieredModernModel extends BlockDirectionalModernModel {

    private final ECOControllerTier tier;

    public BlockTieredModernModel(String id, String modelName, String[] textureNames, ECOControllerTier tier) {
        super(id, modelName, textureNames);
        this.tier = tier;
    }

    public BlockTieredModernModel(String id, String modelName, String[] textureNames, ModelFacing inventoryModelFacing,
        ECOControllerTier tier) {
        super(id, modelName, textureNames, inventoryModelFacing);
        this.tier = tier;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }
}
