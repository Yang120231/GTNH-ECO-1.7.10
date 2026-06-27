package cn.dancingsnow.neoecoae;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEOreDictionary;
import cn.dancingsnow.neoecoae.all.NERecipes;
import cn.dancingsnow.neoecoae.world.NEOreWorldGenerator;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        NEBlocks.register();
        NEItems.register();
        NEOreDictionary.register();
        GameRegistry.registerWorldGenerator(NEOreWorldGenerator.INSTANCE, 0);

        NeoECOAE.LOG.info(Config.greeting);
        NeoECOAE.LOG.info("I am Neo ECO AE Extension at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        NERecipes.register();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
