package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public class BlockTieredModernModel extends BlockDirectionalModernModel {

    private final ECOControllerTier tier;
    private final String formedModelName;
    private final String mirroredFormedModelName;

    public BlockTieredModernModel(String id, String modelName, String[] textureNames, ECOControllerTier tier) {
        this(id, modelName, null, textureNames, tier);
    }

    public BlockTieredModernModel(String id, String modelName, String[] textureNames, ModelFacing inventoryModelFacing,
        ECOControllerTier tier) {
        this(id, modelName, null, textureNames, inventoryModelFacing, tier);
    }

    public BlockTieredModernModel(String id, String modelName, String formedModelName, String[] textureNames,
        ECOControllerTier tier) {
        this(id, modelName, formedModelName, null, textureNames, tier);
    }

    public BlockTieredModernModel(String id, String modelName, String formedModelName, String[] textureNames,
        ModelFacing inventoryModelFacing, ECOControllerTier tier) {
        this(id, modelName, formedModelName, null, textureNames, inventoryModelFacing, tier);
    }

    public BlockTieredModernModel(String id, String modelName, String formedModelName, String mirroredFormedModelName,
        String[] textureNames, ECOControllerTier tier) {
        super(id, modelName, textureNames);
        this.tier = tier;
        this.formedModelName = formedModelName;
        this.mirroredFormedModelName = mirroredFormedModelName;
    }

    public BlockTieredModernModel(String id, String modelName, String formedModelName, String mirroredFormedModelName,
        String[] textureNames, ModelFacing inventoryModelFacing, ECOControllerTier tier) {
        super(id, modelName, textureNames, inventoryModelFacing);
        this.tier = tier;
        this.formedModelName = formedModelName;
        this.mirroredFormedModelName = mirroredFormedModelName;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }

    @Override
    public String getFormedModelName() {
        return this.formedModelName;
    }

    @Override
    public String getMirroredFormedModelName() {
        return this.mirroredFormedModelName;
    }
}
