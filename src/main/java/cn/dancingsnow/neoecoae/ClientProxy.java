package cn.dancingsnow.neoecoae;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.block.BlockECOController;
import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.client.render.DriveModels;
import cn.dancingsnow.neoecoae.client.render.DriveRenderHandler;
import cn.dancingsnow.neoecoae.client.render.ModernBlockModels;
import cn.dancingsnow.neoecoae.client.render.ModernBlockRenderHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        registerRenderers();
    }

    private void registerRenderers() {
        int driveRenderId = RenderingRegistry.getNextAvailableRenderId();
        BlockModelDrive.setRenderId(driveRenderId);
        DriveModels.load((BlockModelDrive) NEBlocks.ecoDrive);
        DriveModels.load((BlockModelDrive) NEBlocks.computationDrive);
        RenderingRegistry.registerBlockHandler(new DriveRenderHandler(driveRenderId));

        int modernBlockRenderId = RenderingRegistry.getNextAvailableRenderId();
        BlockModernModel.setRenderId(modernBlockRenderId);
        for (BlockModernModel block : NEBlocks.getModernModelBlocks()) {
            ModernBlockModels.load(block.getModelName());
            if (block instanceof BlockECOController) {
                BlockECOController controller = (BlockECOController) block;
                ModernBlockModels.load(controller.getFormedModelName());
                ModernBlockModels.load(controller.getMirroredFormedModelName());
            }
        }
        RenderingRegistry.registerBlockHandler(new ModernBlockRenderHandler(modernBlockRenderId));
    }
}
