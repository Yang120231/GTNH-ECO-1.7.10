package cn.dancingsnow.neoecoae.platform.minecraft;

import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Executor/lifecycle boundary for the running integrated or dedicated server. */
public final class ServerRuntime {

    private final ServerTasks.Session session;
    private final net.minecraft.server.MinecraftServer server;

    ServerRuntime() {
        session = ServerTasks.captureSession();
        server = net.minecraft.server.MinecraftServer.getServer();
    }

    public boolean isStopped() {
        return !session.isRunning() || server == null || server.isServerStopped();
    }

    public void executeIfPossible(Runnable task) {
        session.execute(task);
    }

    public net.minecraft.server.MinecraftServer nativeServer() {
        return server;
    }
}
