package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class NENetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(NeoECOAE.MODID);
    private static boolean registered;

    private NENetwork() {}

    public static void register() {
        if (registered) {
            return;
        }
        ServerMainThreadScheduler.register();
        int id = 0;
        CHANNEL.registerMessage(HostUiStatePacket.Handler.class, HostUiStatePacket.class, id++, Side.CLIENT);
        CHANNEL
            .registerMessage(PacketStorageHostAction.Handler.class, PacketStorageHostAction.class, id++, Side.SERVER);
        CHANNEL.registerMessage(
            PacketComputationHostAction.Handler.class,
            PacketComputationHostAction.class,
            id++,
            Side.SERVER);
        CHANNEL
            .registerMessage(PacketCraftingHostAction.Handler.class, PacketCraftingHostAction.class, id++, Side.SERVER);
        registered = true;
    }
}
