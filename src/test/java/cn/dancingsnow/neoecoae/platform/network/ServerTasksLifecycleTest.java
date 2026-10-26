package cn.dancingsnow.neoecoae.platform.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServerTasksLifecycleTest {

    @AfterEach
    void stop() {
        ServerTasks.stopped();
    }

    @Test
    void inactiveClientSessionCannotBecomeAHandleToALaterServer() {
        ServerTasks.stopped();
        ServerTasks.Session client = ServerTasks.captureSession();
        assertFalse(client.isRunning());
        assertFalse(client.execute(() -> fail("No server is running")));
        assertThrows(IllegalStateException.class, () -> ServerTasks.execute(() -> {}));
        ServerTasks.started();
        assertFalse(client.execute(() -> fail("An inactive client handle entered the server")));
        ServerTasks.tick();
    }

    @Test
    void oldWorldBackgroundCompletionCannotQueueIntoSecondWorld() throws Exception {
        ServerTasks.started();
        ServerTasks.Session oldWorld = ServerTasks.captureSession();
        AtomicInteger writes = new AtomicInteger();
        CountDownLatch complete = new CountDownLatch(1);
        var background = Executors.newSingleThreadExecutor();
        try {
            var late = background.submit(() -> {
                complete.await();
                return oldWorld.execute(writes::incrementAndGet);
            });
            assertTrue(oldWorld.execute(writes::incrementAndGet));
            ServerTasks.stopped();
            ServerTasks.started();
            complete.countDown();
            assertFalse(late.get(2, TimeUnit.SECONDS));
            ServerTasks.execute(() -> writes.addAndGet(10));
            ServerTasks.tick();
            assertEquals(10, writes.get());
            assertFalse(oldWorld.isRunning());
            assertTrue(
                ServerTasks.captureSession()
                    .isRunning());
        } finally {
            complete.countDown();
            background.shutdownNow();
        }
    }

    @Test
    void aRestartDuringDrainDoesNotExecuteAnotherWorldsQueueInTheOldTick() {
        ServerTasks.started();
        AtomicInteger writes = new AtomicInteger();
        ServerTasks.execute(() -> {
            ServerTasks.stopped();
            ServerTasks.started();
            ServerTasks.execute(writes::incrementAndGet);
        });
        ServerTasks.tick();
        assertEquals(0, writes.get());
        ServerTasks.tick();
        assertEquals(1, writes.get());
    }
}
