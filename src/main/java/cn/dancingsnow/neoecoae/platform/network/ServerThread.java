package cn.dancingsnow.neoecoae.platform.network;

/**
 * The thread that runs server ticks, captured at each tick start. Tick-scoped deferral (cell writes, cell-array
 * rescans) is only safe on it; worker threads, tests and the client fall back to immediate behaviour.
 */
public final class ServerThread {

    private static volatile Thread current;
    private static long tick;

    private ServerThread() {}

    public static void capture() {
        current = Thread.currentThread();
        tick++;
    }

    /** Server tick counter, advanced at each tick start; for once-per-tick caches on the server thread. */
    public static long tick() {
        return tick;
    }

    public static void clear() {
        current = null;
    }

    public static boolean isCurrent() {
        return Thread.currentThread() == current;
    }
}
