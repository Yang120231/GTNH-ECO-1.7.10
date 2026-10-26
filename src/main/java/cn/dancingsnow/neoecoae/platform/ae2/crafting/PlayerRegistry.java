package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import appeng.api.AEApi;

/** Use AE2's save-specific security IDs, retaining owner identity after reconnect and reload. */
public final class PlayerRegistry {

    private PlayerRegistry() {}

    public static int getPlayerId(EntityPlayerMP player) {
        return AEApi.instance()
            .registries()
            .players()
            .getID(player);
    }

    public static EntityPlayerMP getConnected(MinecraftServer server, int id) {
        if (server == null) return null;
        var player = AEApi.instance()
            .registries()
            .players()
            .findPlayer(id);
        return player instanceof EntityPlayerMP && server.getConfigurationManager().playerEntityList.contains(player)
            ? (EntityPlayerMP) player
            : null;
    }
}
