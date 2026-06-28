package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;

public class BlockFormedDirectionalModernModel extends BlockDirectionalModernModel {

    private final String formedModelName;

    public BlockFormedDirectionalModernModel(String id, String modelName, String formedModelName,
        String[] textureNames) {
        super(id, modelName, textureNames);
        this.formedModelName = formedModelName;
    }

    public BlockFormedDirectionalModernModel(String id, String modelName, String formedModelName, String[] textureNames,
        ModelFacing inventoryModelFacing) {
        super(id, modelName, textureNames, inventoryModelFacing);
        this.formedModelName = formedModelName;
    }

    @Override
    public String getFormedModelName() {
        return this.formedModelName;
    }
}
