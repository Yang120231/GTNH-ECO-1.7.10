package cn.dancingsnow.neoecoae;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cn.dancingsnow.neoecoae.platform.forge.CommonProxy;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = NeoECOAE.MODID,
    version = Tags.VERSION,
    name = "Neo ECO AE Extension",
    dependencies = "required-after:modularui2@[2.3.49-1.7.10,);required-after:appliedenergistics2;after:gregtech;",
    acceptedMinecraftVersions = "[1.7.10]")
public class NeoECOAE {

    public static final String MODID = "neoecoae";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static NeoECOAE instance;

    @SidedProxy(
        clientSide = "cn.dancingsnow.neoecoae.platform.forge.ClientProxy",
        serverSide = "cn.dancingsnow.neoecoae.platform.forge.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void aboutToStart(cpw.mods.fml.common.event.FMLServerAboutToStartEvent event) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.started();
        cn.dancingsnow.neoecoae.platform.forge.PlanningLifecycle.started();
    }

    @Mod.EventHandler
    public void starting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        if (Boolean.getBoolean("neoecoae.portingAcceptance"))
            event.registerServerCommand(new cn.dancingsnow.neoecoae.platform.forge.PortingAcceptanceCommand());
    }

    @Mod.EventHandler
    public void stopped(cpw.mods.fml.common.event.FMLServerStoppedEvent event) {
        cn.dancingsnow.neoecoae.platform.forge.PlanningLifecycle.stopped();
        cn.dancingsnow.neoecoae.platform.forge.StorageLifecycle.stopped();
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.stopped();
    }
}
