package cn.dancingsnow.neoecoae.platform.forge;

import cn.dancingsnow.neoecoae.platform.render.ModelRenderBridge;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ModelRenderBridge.reset();
    }
}
