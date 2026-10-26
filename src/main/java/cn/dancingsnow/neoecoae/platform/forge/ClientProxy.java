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
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (Boolean.getBoolean("neoecoae.contentAcceptance")) cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new NativeContentAcceptance());
        if (Boolean.getBoolean("neoecoae.clientAcceptance")) cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new NativeClientAcceptance());
        if (Boolean.getBoolean("neoecoae.storageUiAcceptance")) {
            var acceptance = new cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageUiAcceptance();
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .bus()
                .register(acceptance);
            net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(acceptance);
        }
    }
}
