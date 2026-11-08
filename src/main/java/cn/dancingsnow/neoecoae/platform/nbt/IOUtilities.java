package cn.dancingsnow.neoecoae.platform.nbt;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;

import org.apache.logging.log4j.LogManager;

/** Native background I/O participates in world/backup flushes; domain files still use atomic replacement. */
public final class IOUtilities {

    private static final Map<Path, Runnable> PENDING_WRITES = new LinkedHashMap<>();
    private static boolean writerQueued;

    private IOUtilities() {}

    public static void withIOWorker(Runnable action) {
        ThreadedFileIOBase.threadedIOInstance.queueIO(new IThreadedFileIO() {

            @Override
            public boolean writeNextIO() {
                runSafely(action);
                return false;
            }
        });
    }

    /** Keeps only the newest not-yet-started snapshot of each file, without replacing an in-flight write. */
    public static void withIOWorker(Path target, Runnable action) {
        synchronized (PENDING_WRITES) {
            PENDING_WRITES.put(
                target.toAbsolutePath()
                    .normalize(),
                action);
            if (writerQueued) return;
            writerQueued = true;
            // A fresh identity avoids losing a new batch while the native queue removes its previous drain.
            ThreadedFileIOBase.threadedIOInstance.queueIO(new IThreadedFileIO() {

                @Override
                public boolean writeNextIO() {
                    Runnable next;
                    synchronized (PENDING_WRITES) {
                        var iterator = PENDING_WRITES.entrySet()
                            .iterator();
                        if (!iterator.hasNext()) {
                            writerQueued = false;
                            return false;
                        }
                        next = iterator.next()
                            .getValue();
                        iterator.remove();
                    }
                    runSafely(next);
                    return true;
                }
            });
        }
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            // Like ExecutorService.submit, a failed callback must not kill the shared I/O thread. Domain saves
            // handle their own encode/write failures and retain the owning data for a later retry.
            LogManager.getLogger(IOUtilities.class)
                .error("Unexpected ECO background I/O failure", failure);
        }
    }

    public static void waitUntilIOWorkerComplete() {
        try {
            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            throw new IllegalStateException("Interrupted domain save", e);
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
