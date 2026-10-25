package cn.dancingsnow.neoecoae.platform.minecraft;

public final class TickClock {

    private TickClock() {}

    public static long currentTick() {
        return net.minecraft.server.MinecraftServer.getServer()
            .getTickCounter();
    }
}
