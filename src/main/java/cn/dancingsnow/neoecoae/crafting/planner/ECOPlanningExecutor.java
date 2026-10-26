package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

/** Bounded daemon executor for explicit ECO planning requests. */
public final class ECOPlanningExecutor {

    private static final int MAX_WORKERS = Math.max(
        1,
        Math.min(
            4,
            Runtime.getRuntime()
                .availableProcessors()));
    private static final Set<Future<?>> TASKS = new HashSet<>();
    private static ThreadPoolExecutor executor;
    private static ThreadPoolExecutor improvements;
    private static boolean accepting = true;

    private static ThreadPoolExecutor foregroundExecutor() {
        return new ThreadPoolExecutor(0, MAX_WORKERS, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "ECO Planning Worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    // Optional work has its own queue and worker so it cannot occupy a foreground calculation slot.
    private static ThreadPoolExecutor improvementExecutor() {
        return new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), runnable -> {
            Thread thread = new Thread(runnable, "ECO Plan Improvement Worker");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    private ECOPlanningExecutor() {}

    public static synchronized <T> Future<T> submit(Callable<T> task) {
        if (!accepting) throw new RejectedExecutionException("ECO planning server has stopped");
        if (executor == null) executor = foregroundExecutor();
        return submit(executor, task);
    }

    public static synchronized <T> @Nullable Future<T> tryImprove(Callable<T> task) {
        if (!accepting) return null;
        if (improvements == null) improvements = improvementExecutor();
        improvements.purge();
        try {
            return submit(improvements, task);
        } catch (RejectedExecutionException full) {
            return null;
        }
    }

    private static <T> Future<T> submit(ThreadPoolExecutor pool, Callable<T> task) {
        FutureTask<T> future = new FutureTask<T>(task) {

            @Override
            protected void done() {
                synchronized (ECOPlanningExecutor.class) {
                    TASKS.remove(this);
                }
            }
        };
        TASKS.add(future);
        try {
            pool.execute(future);
            return future;
        } catch (RejectedExecutionException full) {
            future.cancel(false);
            throw full;
        }
    }

    /** Starts a new server lifetime, including integrated-server world changes in the same JVM. */
    public static synchronized void started() {
        stopped();
        accepting = true;
    }

    /** Cancel running and queued futures; shutdownNow alone leaves queued FutureTasks unfinished. */
    public static synchronized void stopped() {
        accepting = false;
        for (Future<?> task : new HashSet<>(TASKS)) task.cancel(true);
        TASKS.clear();
        if (executor != null) executor.shutdownNow();
        if (improvements != null) improvements.shutdownNow();
        executor = null;
        improvements = null;
    }
}
