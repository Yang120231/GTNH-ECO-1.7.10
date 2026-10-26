package cn.dancingsnow.neoecoae.platform.minecraft;

public final class TickClock {

    private TickClock() {}

    public static long currentTick() {
        var server = net.minecraft.server.MinecraftServer.getServer();
        return server == null ? 0 : server.getTickCounter();
    }
}
