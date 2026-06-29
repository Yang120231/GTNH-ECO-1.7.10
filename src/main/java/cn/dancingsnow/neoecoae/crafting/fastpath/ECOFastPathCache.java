package cn.dancingsnow.neoecoae.crafting.fastpath;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ECOFastPathCache {

    private final LruMap<ECOFastPathPatternKey, ECOFastPathPatternProfile> profiles;
    private final LruMap<ECOFastPathPatternKey, String> negatives;

    public ECOFastPathCache() {
        this(ECOFastPathConfig.patternCacheSize(), ECOFastPathConfig.negativeCacheSize());
    }

    public ECOFastPathCache(int profileSize, int negativeSize) {
        this.profiles = new LruMap<ECOFastPathPatternKey, ECOFastPathPatternProfile>(Math.max(1, profileSize));
        this.negatives = new LruMap<ECOFastPathPatternKey, String>(Math.max(1, negativeSize));
    }

    public synchronized ECOFastPathPatternProfile getProfile(ECOFastPathPatternKey key) {
        return key == null ? null : this.profiles.get(key);
    }

    public synchronized String getNegativeReason(ECOFastPathPatternKey key) {
        return key == null ? null : this.negatives.get(key);
    }

    public synchronized void putProfile(ECOFastPathPatternProfile profile) {
        if (profile == null || profile.getKey() == null) {
            return;
        }
        this.negatives.remove(profile.getKey());
        this.profiles.put(profile.getKey(), profile);
    }

    public synchronized void putNegative(ECOFastPathPatternKey key, String reason) {
        if (key == null) {
            return;
        }
        this.profiles.remove(key);
        this.negatives.put(key, reason == null ? "" : reason);
    }

    public synchronized void clear() {
        this.profiles.clear();
        this.negatives.clear();
    }

    public synchronized int profileSize() {
        return this.profiles.size();
    }

    public synchronized int negativeSize() {
        return this.negatives.size();
    }

    private static final class LruMap<K, V> extends LinkedHashMap<K, V> {

        private final int maxEntries;

        private LruMap(int maxEntries) {
            super(maxEntries, 0.75F, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return this.size() > this.maxEntries;
        }
    }
}
