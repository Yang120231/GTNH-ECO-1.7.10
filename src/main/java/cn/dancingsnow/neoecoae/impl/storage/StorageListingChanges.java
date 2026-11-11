package cn.dancingsnow.neoecoae.impl.storage;

import java.util.function.Consumer;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/** Server-thread, bounded latest-change log shared by native network and infinite storage listings. */
public final class StorageListingChanges {

    private static final int CAPACITY = 4096;
    private final Object2ObjectOpenHashMap<AEKey, Change> changes = new Object2ObjectOpenHashMap<>();
    private Change oldest;
    private Change newest;
    private long version;
    private long floor;

    public long version() {
        return version;
    }

    public void invalidate() {
        floor = ++version;
        changes.clear();
        oldest = newest = null;
    }

    public void record(AEKey key) {
        Change change = changes.get(key);
        if (change == null) {
            if (changes.size() == CAPACITY) {
                floor = Math.max(floor, oldest.version);
                changes.remove(oldest.key);
                unlink(oldest);
            }
            change = new Change(key);
            changes.put(key, change);
        } else if (change != newest) {
            unlink(change);
        }
        change.version = ++version;
        if (change == newest) return;
        change.previous = newest;
        if (newest == null) oldest = change;
        else newest.next = change;
        newest = change;
    }

    public boolean visitSince(long since, long current, Consumer<AEKey> visitor) {
        if (since < floor || since > current || current != version) return false;
        for (Change change = newest; change != null && change.version > since; change = change.previous) {
            visitor.accept(change.key);
        }
        return true;
    }

    private void unlink(Change change) {
        if (change.previous == null) oldest = change.next;
        else change.previous.next = change.next;
        if (change.next == null) newest = change.previous;
        else change.next.previous = change.previous;
        change.previous = change.next = null;
    }

    private static final class Change {

        private final AEKey key;
        private long version;
        private Change previous;
        private Change next;

        private Change(AEKey key) {
            this.key = key;
        }
    }
}
