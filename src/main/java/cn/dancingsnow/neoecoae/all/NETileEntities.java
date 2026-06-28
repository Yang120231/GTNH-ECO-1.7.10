package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NETileEntities {

    private NETileEntities() {}

    public static void register() {
        GameRegistry.registerTileEntity(TileECOController.class, NeoECOAE.MODID + ":eco_controller");
    }
}
