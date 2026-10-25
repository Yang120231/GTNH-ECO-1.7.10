package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraft.world.WorldServer;

public final class ServerWorldView extends WorldView {

    ServerWorldView(WorldServer world) {
        super(world);
    }

    @Override
    public WorldServer nativeWorld() {
        return (WorldServer) world;
    }

    public ServerRuntime getServer() {
        return ServerRuntime.INSTANCE;
    }
}
