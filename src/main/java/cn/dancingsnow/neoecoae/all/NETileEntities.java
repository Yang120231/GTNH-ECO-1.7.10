package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.tile.TileComputationDrive;
import cn.dancingsnow.neoecoae.tile.TileECODrive;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NETileEntities {

    private NETileEntities() {}

    public static void register() {
        GameRegistry.registerTileEntity(TileECOController.class, NeoECOAE.MODID + ":eco_controller");
        GameRegistry.registerTileEntity(TileECODrive.class, NeoECOAE.MODID + ":eco_drive");
        GameRegistry.registerTileEntity(TileComputationDrive.class, NeoECOAE.MODID + ":computation_drive");
        GameRegistry.registerTileEntity(TileECOInterface.class, NeoECOAE.MODID + ":eco_interface");
    }
}
