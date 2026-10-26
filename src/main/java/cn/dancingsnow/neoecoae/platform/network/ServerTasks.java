package cn.dancingsnow.neoecoae.platform.network;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Forge 1.7.10 has no modern server.execute; all network/world writes return to this tick queue. */
public final class ServerTasks {

    private static final ConcurrentLinkedQueue<PendingTask> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Session INACTIVE = new Session(null);
    private static volatile Session current;

    private ServerTasks() {}

    public static boolean isRunning() {
        return current != null;
    }

    public static synchronized void started() {
        QUEUE.clear();
        current = new Session(Thread.currentThread());
    }

    public static void assertServerThread() {
        Session session = current;
        if (session == null || Thread.currentThread() != session.owner)
            throw new IllegalStateException("Operation requires the server thread");
    }

    public static void execute(Runnable task) {
        Session session = current;
        if (session == null) throw new IllegalStateException("Server is not running");
        session.execute(task);
    }

    /** Capture before handing work to another thread, not after that work completes. */
    public static Session captureSession() {
        Session session = current;
        return session == null ? INACTIVE : session;
    }

    public static void tick() {
        assertServerThread();
        Session session = current;
        PendingTask task;
        int count = 0;
        while (current == session && count++ < 1024 && (task = QUEUE.poll()) != null)
            if (task.session == session) task.action.run();
    }

    public static synchronized void stopped() {
        current = null;
        QUEUE.clear();
    }

    public static final class Session {

        private final Thread owner;

        private Session(Thread owner) {
            this.owner = owner;
        }

        public boolean isRunning() {
            return current == this;
        }

        /** A late callback from an old world is discarded even after a new world has started. */
        public boolean execute(Runnable task) {
            java.util.Objects.requireNonNull(task);
            synchronized (ServerTasks.class) {
                if (current != this) return false;
                QUEUE.add(new PendingTask(this, task));
                return true;
            }
        }
    }

    private static final class PendingTask {

        final Session session;
        final Runnable action;

        PendingTask(Session session, Runnable action) {
            this.session = session;
            this.action = action;
        }
    }
}
