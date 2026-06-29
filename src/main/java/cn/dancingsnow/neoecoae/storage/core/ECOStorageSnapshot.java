package cn.dancingsnow.neoecoae.storage.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ECOStorageSnapshot {

    private final long revision;
    private final ECOAmount used;
    private final Map<ECOStorageKey, ECOAmount> entries;

    ECOStorageSnapshot(long revision, ECOAmount used, Map<ECOStorageKey, ECOAmount> entries) {
        this.revision = revision;
        this.used = used == null ? ECOAmount.ZERO : used;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<ECOStorageKey, ECOAmount>(entries));
    }

    public long getRevision() {
        return this.revision;
    }

    public ECOAmount getUsed() {
        return this.used;
    }

    public int getTypeCount() {
        return this.entries.size();
    }

    public Map<ECOStorageKey, ECOAmount> getEntries() {
        return this.entries;
    }
}
