package cn.dancingsnow.neoecoae.platform.network;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Forge 1.7.10 has no modern server.execute; all network/world writes return to this tick queue. */
public final class ServerTasks {

    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile Thread owner;

    private ServerTasks() {}

    public static boolean isRunning() {
        return owner != null;
    }

    public static void started() {
        owner = Thread.currentThread();
        QUEUE.clear();
    }

    public static void assertServerThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Operation requires the server thread");
    }

    public static void execute(Runnable task) {
        if (owner == null) throw new IllegalStateException("Server is not running");
        QUEUE.add(java.util.Objects.requireNonNull(task));
    }

    public static void tick() {
        assertServerThread();
        Runnable task;
        int count = 0;
        while (count++ < 1024 && (task = QUEUE.poll()) != null) task.run();
    }

    public static void stopped() {
        QUEUE.clear();
        owner = null;
    }
}
