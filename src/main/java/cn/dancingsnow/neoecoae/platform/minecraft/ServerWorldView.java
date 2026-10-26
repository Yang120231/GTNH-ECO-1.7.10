package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraft.world.WorldServer;

public final class ServerWorldView extends WorldView {

    private final ServerRuntime server;

    ServerWorldView(WorldServer world) {
        super(world);
        server = new ServerRuntime();
    }

    @Override
    public WorldServer nativeWorld() {
        return (WorldServer) world;
    }

    public ServerRuntime getServer() {
        return server;
    }
}
