package cn.dancingsnow.neoecoae.platform.nbt;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One ordered writer. Atomic replacement never exposes a partially compressed authoritative snapshot. */
public final class IOUtilities {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "NeoECOAE domain save");
        thread.setDaemon(true);
        return thread;
    });

    private IOUtilities() {}

    public static void withIOWorker(Runnable action) {
        WORKER.submit(action);
    }

    public static void waitUntilIOWorkerComplete() {
        try {
            WORKER.submit(() -> {})
                .get();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            throw new IllegalStateException("Interrupted domain save", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Domain save failed", e.getCause());
        }
    }

    public static void writeNbtCompressed(CompoundTag root, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(
            absolute.getParent(),
            absolute.getFileName()
                .toString(),
            ".tmp");
        try {
            NbtIo.writeCompressed(root, temporary);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic snapshot replacement unavailable", e);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
