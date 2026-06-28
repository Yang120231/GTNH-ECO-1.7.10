package cn.dancingsnow.neoecoae.client;

import net.minecraftforge.event.world.WorldEvent;

import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class ClientEventHandler {

    public static final ClientEventHandler INSTANCE = new ClientEventHandler();

    private ClientEventHandler() {}

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            ECOFormationVisibility.clearClient();
        }
    }
}
