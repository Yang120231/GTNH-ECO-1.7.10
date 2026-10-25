package cn.dancingsnow.neoecoae.platform.minecraft;

public final class ServerTaskUtil {

    private ServerTaskUtil() {}

    public static void executeIfServerRunning(ServerWorldView world, Runnable action) {
        world.getServer()
            .executeIfPossible(
                () -> {
                    if (!world.getServer()
                        .isStopped()) action.run();
                });
    }
}
