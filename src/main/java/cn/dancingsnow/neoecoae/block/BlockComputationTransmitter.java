package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public class BlockComputationTransmitter extends BlockFormedDirectionalModernModel {

    public BlockComputationTransmitter(String id, String modelName, String formedModelName, String[] textureNames) {
        super(id, modelName, formedModelName, textureNames);
    }

    public String getFormedModelName(ECOControllerTier tier) {
        if (tier == null) {
            return this.getFormedModelName();
        }
        return this.getFormedModelName() + "_" + tier.getId();
    }

    @Override
    public String[] getAdditionalFormedModelNames() {
        return new String[] { this.getFormedModelName(ECOControllerTier.L4),
            this.getFormedModelName(ECOControllerTier.L6), this.getFormedModelName(ECOControllerTier.L9) };
    }
}
