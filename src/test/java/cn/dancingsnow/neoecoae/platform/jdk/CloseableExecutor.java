package cn.dancingsnow.neoecoae.platform.jdk;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** The used Java 21 executor close contract, delegated to Java 8 threads. */
public final class CloseableExecutor extends AbstractExecutorService implements AutoCloseable {

    private final ExecutorService delegate;

    private CloseableExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    public static CloseableExecutor fixed(int count) {
        return new CloseableExecutor(Executors.newFixedThreadPool(count));
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    @Override
    public void close() {
        shutdown();
        boolean interrupted = false;
        while (!isTerminated()) try {
            awaitTermination(1L, TimeUnit.DAYS);
        } catch (InterruptedException failure) {
            interrupted = true;
            shutdownNow();
        }
        if (interrupted) Thread.currentThread()
            .interrupt();
    }
}
