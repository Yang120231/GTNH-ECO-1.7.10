package cn.dancingsnow.neoecoae.impl.storage;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Coalesces component serialization inside one controller tick; live amounts remain authoritative. */
public final class ECOCellMutationBatch implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(ECOCellMutationBatch.class);
    private static final ThreadLocal<ECOCellMutationBatch> ACTIVE = new ThreadLocal<>();
    // Persistence obligations must retain their owner until they succeed or the server-stop drain reports failure.
    private static final Set<ECOStorageCell> RETRY = new LinkedHashSet<>();
    // Cells whose host flushes them before the stack is read, moved, dropped, unloaded or saved; AE2 network
    // traffic outside a controller scope then costs one component write per cell per server tick, not per operation.
    private static final Set<ECOStorageCell> TICK_DEFERRED = new LinkedHashSet<>();
    private final ECOCellMutationBatch parent;
    private final Set<ECOStorageCell> changed = new LinkedHashSet<>();
    private boolean closed;

    private ECOCellMutationBatch() {
        parent = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static ECOCellMutationBatch open() {
        return new ECOCellMutationBatch();
    }

    static boolean defer(ECOStorageCell cell) {
        ECOCellMutationBatch batch = ACTIVE.get();
        if (batch == null) return false;
        batch.changed.add(cell);
        return true;
    }

    /** Server-thread only: worker threads keep immediate persistence instead of touching the shared set. */
    static boolean deferToTickEnd(ECOStorageCell cell) {
        if (!cn.dancingsnow.neoecoae.platform.network.ServerThread.isCurrent()) return false;
        TICK_DEFERRED.add(cell);
        return true;
    }

    /** Writes one host's pending cell before its stack leaves the host or is serialized. */
    public static boolean flushDeferred(Object inventory) {
        if (!(inventory instanceof ECOStorageCell cell)) return true;
        if (TICK_DEFERRED.contains(cell) || RETRY.contains(cell)) return flush(cell);
        for (var batch = ACTIVE.get(); batch != null; batch = batch.parent) {
            if (batch.changed.contains(cell)) return flush(cell);
        }
        return true;
    }

    public static boolean hasPendingWrites() {
        return !TICK_DEFERRED.isEmpty() || !RETRY.isEmpty() || ACTIVE.get() != null;
    }

    /** Server tick end, world save and shutdown. */
    public static void flushTickDeferred() {
        if (TICK_DEFERRED.isEmpty()) return;
        ECOStorageCell[] pending = TICK_DEFERRED.toArray(new ECOStorageCell[0]);
        for (ECOStorageCell cell : pending) flush(cell);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (parent != null) {
            ACTIVE.set(parent);
            parent.changed.addAll(changed);
        } else {
            ACTIVE.remove();
            for (ECOStorageCell cell : changed) flush(cell);
        }
    }

    public static void retry() {
        int remaining = 16;
        for (ECOStorageCell cell : new java.util.ArrayList<>(RETRY)) {
            if (remaining-- <= 0) break;
            flush(cell);
        }
    }

    public static void drainRetries() {
        for (ECOStorageCell cell : new java.util.ArrayList<>(RETRY)) flush(cell);
        if (!RETRY.isEmpty()) LOGGER.error("{} ECO cell writes remain pending at server shutdown", RETRY.size());
    }

    /** Drops an accidentally unclosed server-thread scope at the server lifecycle boundary. */
    public static void clearThreadState() {
        ECOCellMutationBatch batch = ACTIVE.get();
        ACTIVE.remove();
        Set<ECOStorageCell> pending = new LinkedHashSet<>();
        while (batch != null) {
            pending.addAll(batch.changed);
            batch.changed.clear();
            batch.closed = true;
            batch = batch.parent;
        }
        for (ECOStorageCell cell : pending) flush(cell);
    }

    public static void assertClean() {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("ECOCellMutationBatch scope leaked across a server tick");
        }
    }

    private static boolean flush(ECOStorageCell cell) {
        boolean written = false;
        try {
            cell.flushBatchedChanges();
            written = true;
        } catch (RuntimeException failure) {
            if (!RETRY.contains(cell))
                LOGGER.error("ECO cell persistence failed; retaining the pending write", failure);
        }
        TICK_DEFERRED.remove(cell);
        for (var batch = ACTIVE.get(); batch != null; batch = batch.parent) batch.changed.remove(cell);
        RETRY.remove(cell);
        if (!written) RETRY.add(cell);
        return written;
    }
}
