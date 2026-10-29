package cn.dancingsnow.neoecoae.platform.forge;

import cn.dancingsnow.neoecoae.platform.render.ModelRenderBridge;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        super.preInit(event);
        ModelRenderBridge.reset();
        cn.dancingsnow.neoecoae.platform.render.NativeMachineRendering.install();
        cn.dancingsnow.neoecoae.platform.render.NativeMaterialBlockRendering.install();
        cn.dancingsnow.neoecoae.platform.render.NativeModelItems.install();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new cn.dancingsnow.neoecoae.platform.render.NativeFluidRendering());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient());
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftConfirmClient());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        DevAcceptance.hooks()
            .clientInit();
    }
}
