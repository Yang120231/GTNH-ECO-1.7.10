package cn.dancingsnow.neoecoae.platform.forge;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import cn.dancingsnow.neoecoae.platform.gt.NativeGTMachineAcceptance;
import cn.dancingsnow.neoecoae.platform.gt.NativeWorkstationAcceptance;
import cn.dancingsnow.neoecoae.platform.recipe.NativeRecipeAcceptance;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Wires every harness at the same points production code used to call them directly. Each keeps its own gate. */
public final class AcceptanceHooks implements DevAcceptance.Hooks {

    @Override
    public void preInit() {
        PortingFoundationFixture.register();
        NativeWorkstationAcceptance.register();
        AcceptanceChunkTicket.configure();
    }

    @Override
    public void clientInit() {
        if (Boolean.getBoolean("neoecoae.launchAcceptance")) FMLCommonHandler.instance()
            .bus()
            .register(new cn.dancingsnow.neoecoae.platform.ui.NativeLaunchAcceptance());
        if (Boolean.getBoolean("neoecoae.contentAcceptance")) FMLCommonHandler.instance()
            .bus()
            .register(new NativeContentAcceptance());
        if (Boolean.getBoolean("neoecoae.clientAcceptance")) FMLCommonHandler.instance()
            .bus()
            .register(new NativeClientAcceptance());
        if (Boolean.getBoolean("neoecoae.storageUiAcceptance")
            || Boolean.getBoolean("neoecoae.storageUiNetworkAcceptance")) {
            var acceptance = new cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageUiAcceptance();
            FMLCommonHandler.instance()
                .bus()
                .register(acceptance);
            net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(acceptance);
        }
    }

    @Override
    public void gregTechInit() {
        NativeGTMachineAcceptance.register();
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        if (Boolean.getBoolean("neoecoae.portingAcceptance"))
            event.registerServerCommand(new PortingAcceptanceCommand());
    }

    @Override
    public void serverTick() {
        PortingFoundationFixture.tick();
        PortingStorageHostFixture.tick();
        PortingPlanningFixture.tick();
    }

    @Override
    public void recipesRegistered() {
        if (Boolean.getBoolean("neoecoae.recipeAcceptance")) NativeRecipeAcceptance.checkRegistered();
    }

    @Override
    public void worldTick(WorldServer world) {
        NativeRecipeAcceptance.worldTick(world);
    }

    @Override
    public void worldUnload(World world) {
        NativeRecipeAcceptance.unload(world);
    }
}
