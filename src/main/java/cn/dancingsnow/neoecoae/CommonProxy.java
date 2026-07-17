package cn.dancingsnow.neoecoae;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEOreDictionary;
import cn.dancingsnow.neoecoae.all.NERecipes;
import cn.dancingsnow.neoecoae.all.NETileEntities;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipes;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook;
import cn.dancingsnow.neoecoae.gui.NEGuiHandler;
import cn.dancingsnow.neoecoae.network.HostUiStatePacket;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.storage.ae2.NEAE2Storage;
import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cn.dancingsnow.neoecoae.world.NEOreWorldGenerator;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        NENetwork.register();
        NEFluids.register();
        NEBlocks.register();
        NETileEntities.register();
        NEItems.register();
        NEFluids.registerContainers();
        NEOreDictionary.register();
        GameRegistry.registerWorldGenerator(NEOreWorldGenerator.INSTANCE, 0);

        NeoECOAE.LOG.info("I am Neo ECO AE Extension at version " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(NeoECOAE.instance, NEGuiHandler.INSTANCE);
        NEAE2Storage.register();
        ECOCoolingRecipes.registerDefaults();
        NERecipes.register();
    }

    public void postInit(FMLPostInitializationEvent event) {
        NERecipes.registerPostInit();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        ECOFastPathPlannerHook.clearCaches();
    }

    public Object createHostControllerGui(int id, net.minecraft.entity.player.InventoryPlayer playerInventory,
        TileECOController controller) {
        return null;
    }

    public Object createStoragePriorityGui(net.minecraft.entity.player.InventoryPlayer playerInventory,
        TileECOController controller) {
        return null;
    }

    public Object createCraftingPatternBusGui(net.minecraft.entity.player.InventoryPlayer playerInventory,
        TileCraftingPatternBus bus) {
        return null;
    }

    public Object createCraftingHatchGui(net.minecraft.entity.player.InventoryPlayer playerInventory,
        TileCraftingHatch hatch) {
        return null;
    }

    public Object createStructureTerminalGui(net.minecraft.entity.player.EntityPlayer player) {
        return null;
    }

    public Object createStorageRecoveryTerminalGui(net.minecraft.entity.player.EntityPlayer player) {
        return null;
    }

    public Object createStorageInterfaceGui(TileECOInterface storageInterface) {
        return null;
    }

    public void handleHostUiStatePacket(HostUiStatePacket packet) {}
}
