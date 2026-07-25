package cn.dancingsnow.neoecoae;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEOreDictionary;
import cn.dancingsnow.neoecoae.all.NERecipes;
import cn.dancingsnow.neoecoae.all.NETileEntities;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipes;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook;
import cn.dancingsnow.neoecoae.gui.mui.NeoEcoUiFactory;
import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;
import cn.dancingsnow.neoecoae.storage.ae2.NEAE2Storage;
import cn.dancingsnow.neoecoae.world.NEOreWorldGenerator;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        NEFluids.register();
        NEBlocks.register();
        NETileEntities.register();
        NEItems.register();
        NEFluids.registerContainers();
        NEOreDictionary.register();
        NEPatternUploadNetwork.register();
        GameRegistry.registerWorldGenerator(NEOreWorldGenerator.INSTANCE, 0);

        NeoECOAE.LOG.info("I am Neo ECO AE Extension at version " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        NeoEcoUiFactory.register();
        NEAE2Storage.register();
        ECOCoolingRecipes.registerDefaults();
        NEOreDictionary.registerSilicon();
        NERecipes.register();
    }

    public void postInit(FMLPostInitializationEvent event) {
        NERecipes.registerPostInit();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        ECOFastPathPlannerHook.clearCaches();
    }

}
