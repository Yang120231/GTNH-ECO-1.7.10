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
        cn.dancingsnow.neoecoae.platform.registry.BasicBlocks.register();
        cn.dancingsnow.neoecoae.platform.registry.StorageMachines.register();
        cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.register();
        cn.dancingsnow.neoecoae.platform.registry.CraftingMachines.register();
        cn.dancingsnow.neoecoae.platform.registry.CoreBlocks.register();
        cn.dancingsnow.neoecoae.platform.registry.RecipeMaterials.register();
        PortingFoundationFixture.register();
        cn.dancingsnow.neoecoae.platform.gt.NativeWorkstationAcceptance.register();
        AcceptanceChunkTicket.configure();
        StorageLifecycle lifecycle = new StorageLifecycle();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(lifecycle);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(lifecycle);
    }

    public void init(FMLInitializationEvent event) {
        appeng.api.AEApi.instance()
            .registries()
            .gridCache()
            .registerGridCache(
                cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache.class,
                cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache.class);
        cn.dancingsnow.neoecoae.api.storage.ECOStorageCells
            .register(cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell.Handler.INSTANCE);
        cn.dancingsnow.neoecoae.platform.registry.NativeContent.connectAe2();
        cn.dancingsnow.neoecoae.compat.ae2.AE2PatternOptimizationIntegration.init();
        appeng.api.AEApi.instance().registries().interfaceTerminal()
            .register(cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.Interface.class);
        cn.dancingsnow.neoecoae.platform.recipe.StorageRecipes.register();
    }

    public void postInit(FMLPostInitializationEvent event) {
        cn.dancingsnow.neoecoae.platform.recipe.SourceRecipes.register();
        cn.dancingsnow.neoecoae.platform.gt.NativeGregTech.initialize();
    }
}
