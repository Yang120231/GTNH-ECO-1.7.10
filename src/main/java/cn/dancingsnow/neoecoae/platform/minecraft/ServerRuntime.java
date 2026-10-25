package cn.dancingsnow.neoecoae.platform.minecraft;

import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Executor/lifecycle boundary for the running integrated or dedicated server. */
public final class ServerRuntime {

    public static final ServerRuntime INSTANCE = new ServerRuntime();

    private ServerRuntime() {}

    public boolean isStopped() {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        return server == null || server.isServerStopped();
    }

    public void executeIfPossible(Runnable task) {
        ServerTasks.execute(task);
    }

    public net.minecraft.server.MinecraftServer nativeServer() {
        return net.minecraft.server.MinecraftServer.getServer();
    }
}
