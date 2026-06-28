package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.NeoECOAE;

public class BlockComputationDrive extends BlockModelDrive {

    private static final String[] TEXTURES = { "neoecoae:block/compute/casing_side_east",
        "neoecoae:block/compute/casing_side_west", "neoecoae:block/compute/casing",
        "neoecoae:block/compute/casing_back", "neoecoae:block/compute/drive/drive_inside",
        "neoecoae:block/compute/drive/drive_inside_top", "neoecoae:block/compute/drive/drive_north",
        "neoecoae:block/compute/drive/drive_north_on" };

    public BlockComputationDrive() {
        super(
            "computation_drive",
            "computation_drive_empty",
            "computation_drive_full",
            TEXTURES,
            NeoECOAE.MODID + ":block/compute/casing_side_east");
    }

    @Override
    public boolean useFullModelWhenFormed() {
        return true;
    }
}
