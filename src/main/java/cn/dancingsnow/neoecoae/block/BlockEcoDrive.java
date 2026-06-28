package cn.dancingsnow.neoecoae.block;

import cn.dancingsnow.neoecoae.NeoECOAE;

public class BlockEcoDrive extends BlockModelDrive {

    private static final String[] TEXTURES = { "neoecoae:block/storage/casing", "neoecoae:block/storage/casing_side",
        "neoecoae:block/storage/casing_back", "neoecoae:block/storage/drive/drive_north",
        "neoecoae:block/storage/drive/drive_north_on", "neoecoae:block/storage/drive/drive_inside",
        "neoecoae:block/storage/drive/drive_inside_top_bottom" };

    public BlockEcoDrive() {
        super(
            "eco_drive",
            "eco_drive_empty",
            "eco_drive_full",
            TEXTURES,
            NeoECOAE.MODID + ":block/storage/casing_side");
    }
}
