package cn.dancingsnow.neoecoae;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = NeoECOAE.MODID,
    version = Tags.VERSION,
    name = "Neo ECO AE Extension",
    dependencies = "required-after:modularui2@[2.3.49-1.7.10,);required-after:appliedenergistics2;",
    acceptedMinecraftVersions = "[1.7.10]")
public class NeoECOAE {

    public static final String MODID = "neoecoae";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static NeoECOAE instance;

    @SidedProxy(clientSide = "cn.dancingsnow.neoecoae.ClientProxy", serverSide = "cn.dancingsnow.neoecoae.CommonProxy")
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
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
