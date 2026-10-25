package cn.dancingsnow.neoecoae.platform.forge;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.Tags;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        NeoECOAE.LOG.info("Neo ECO AE Extension {} (GTNH business port)", Tags.VERSION);
        PlanningSettings.load(event.getSuggestedConfigurationFile());
        cn.dancingsnow.neoecoae.platform.registry.NativeContent.register();
        cn.dancingsnow.neoecoae.platform.registry.StorageMachines.register();
        PortingFoundationFixture.register();
        AcceptanceChunkTicket.configure();
        StorageLifecycle lifecycle = new StorageLifecycle();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(lifecycle);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(lifecycle);
    }

    public void init(FMLInitializationEvent event) {
        cn.dancingsnow.neoecoae.platform.registry.NativeContent.connectAe2();
        cn.dancingsnow.neoecoae.platform.recipe.StorageRecipes.register();
    }

    public void postInit(FMLPostInitializationEvent event) {}
}
