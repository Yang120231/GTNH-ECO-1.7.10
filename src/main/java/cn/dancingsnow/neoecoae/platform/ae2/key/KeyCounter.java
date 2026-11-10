package cn.dancingsnow.neoecoae.platform.ae2.key;

import java.util.Iterator;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/** Signed counter for planner deltas. Storage aggregation explicitly applies its saturation policy. */
public final class KeyCounter implements Iterable<Object2LongMap.Entry<AEKey>> {

    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private boolean frozen;
    private java.util.List<Object2LongMap.Entry<AEKey>> frozenEntries;

    /** Shared snapshots expose neither setters nor mutable map entries. Copies remain writable. */
    public KeyCounter freeze() {
        if (!frozen) {
            java.util.List<Object2LongMap.Entry<AEKey>> entries = new java.util.ArrayList<>(amounts.size());
            for (var entry : amounts.object2LongEntrySet()) entries.add(
                new it.unimi.dsi.fastutil.objects.AbstractObject2LongMap.BasicEntry<>(
                    entry.getKey(),
                    entry.getLongValue()));
            frozenEntries = java.util.Collections.unmodifiableList(entries);
        }
        frozen = true;
        return this;
    }

    private void writable() {
        if (frozen) throw new UnsupportedOperationException("Immutable inventory snapshot");
    }

    public long get(AEKey key) {
        return amounts.getLong(key);
    }

    public void set(AEKey key, long amount) {
        writable();
        java.util.Objects.requireNonNull(key);
        if (amount == 0) amounts.removeLong(key);
        else amounts.put(key, amount);
    }

    public void add(AEKey key, long amount) {
        set(key, get(key) + amount);
    }

    public void addAll(KeyCounter other) {
        for (Object2LongMap.Entry<AEKey> entry : other) add(entry.getKey(), entry.getLongValue());
    }

    public void remove(AEKey key, long amount) {
        add(key, -amount);
    }

    public void remove(AEKey key) {
        writable();
        amounts.removeLong(key);
    }

    public java.util.Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, appeng.api.config.FuzzyMode mode) {
        java.util.Objects.requireNonNull(key);
        if (mode != appeng.api.config.FuzzyMode.IGNORE_ALL)
            throw new IllegalArgumentException("Inventory template matching expects IGNORE_ALL");
        var matches = new java.util.ArrayList<Object2LongMap.Entry<AEKey>>();
        for (var entry : amounts.object2LongEntrySet()) if (entry.getKey()
            .getType() == key.getType()
            && entry.getKey()
                .getPrimaryKey() == key.getPrimaryKey())
            matches
                .add(
                    frozen
                        ? new it.unimi.dsi.fastutil.objects.AbstractObject2LongMap.BasicEntry<>(
                            entry.getKey(),
                            entry.getLongValue())
                        : entry);
        return matches;
    }

    public void clear() {
        writable();
        amounts.clear();
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public void removeZeros() {
        writable();
        amounts.object2LongEntrySet()
            .removeIf(entry -> entry.getLongValue() == 0);
    }

    public int size() {
        return amounts.size();
    }

    public Set<AEKey> keySet() {
        return java.util.Collections.unmodifiableSet(amounts.keySet());
    }

    public KeyCounter copy() {
        KeyCounter copy = new KeyCounter();
        copy.amounts.putAll(amounts);
        return copy;
    }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        if (frozen) {
            return frozenEntries.iterator();
        }
        return amounts.object2LongEntrySet()
            .iterator();
    }
}
