package cn.dancingsnow.neoecoae.platform.forge;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Local config and connection lifecycle; old keys remain valid after every invalidation. */
public final class KeyReuseLifecycle {

    @SubscribeEvent
    public void configChanged(cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent event) {
        if (NeoECOAE.MODID.equals(event.modID)) KeyReuse.configure(PlanningSettings.KeyReuse.enabled);
    }

    @SubscribeEvent
    public void disconnected(cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        KeyReuse.invalidate();
    }
}
