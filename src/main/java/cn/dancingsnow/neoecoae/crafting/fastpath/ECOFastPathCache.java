package cn.dancingsnow.neoecoae.crafting.fastpath;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ECOFastPathCache {

    static final long NEGATIVE_CACHE_TTL_TICKS = 1200L;

    private final LruMap<ECOFastPathPatternKey, ECOFastPathPatternProfile> profiles;
    private final LruMap<ECOFastPathPatternKey, NegativeEntry> negatives;

    public ECOFastPathCache() {
        this(ECOFastPathConfig.patternCacheSize(), ECOFastPathConfig.negativeCacheSize());
    }

    public ECOFastPathCache(int profileSize, int negativeSize) {
        this.profiles = new LruMap<ECOFastPathPatternKey, ECOFastPathPatternProfile>(Math.max(1, profileSize));
        this.negatives = new LruMap<ECOFastPathPatternKey, NegativeEntry>(Math.max(1, negativeSize));
    }

    public synchronized ECOFastPathPatternProfile getProfile(ECOFastPathPatternKey key) {
        return key == null ? null : this.profiles.get(key);
    }

    public synchronized String getNegativeReason(ECOFastPathPatternKey key, long tick) {
        if (key == null) {
            return null;
        }
        NegativeEntry entry = this.negatives.get(key);
        if (entry != null && negativeExpired(entry.createdTick, tick)) {
            this.negatives.remove(key);
            return null;
        }
        return entry == null ? null : entry.reason;
    }

    public synchronized void putProfile(ECOFastPathPatternProfile profile) {
        if (profile == null || profile.getKey() == null) {
            return;
        }
        this.negatives.remove(profile.getKey());
        this.profiles.put(profile.getKey(), profile);
    }

    public synchronized void putNegative(ECOFastPathPatternKey key, String reason, long tick) {
        if (key == null) {
            return;
        }
        this.profiles.remove(key);
        this.negatives.put(key, new NegativeEntry(reason, tick));
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

    static boolean negativeExpired(long createdTick, long tick) {
        long age = tick - createdTick;
        return age < 0L || age >= NEGATIVE_CACHE_TTL_TICKS;
    }

    private static final class NegativeEntry {

        private final String reason;
        private final long createdTick;

        private NegativeEntry(String reason, long createdTick) {
            this.reason = reason == null ? "" : reason;
            this.createdTick = tickOrZero(createdTick);
        }

        private static long tickOrZero(long tick) {
            return Math.max(0L, tick);
        }
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
