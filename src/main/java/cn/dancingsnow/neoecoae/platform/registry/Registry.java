package cn.dancingsnow.neoecoae.platform.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Stable registration order for the upstream public cell-type registry. */
public final class Registry<T> {

    private final Map<ResourceLocation, T> values = new LinkedHashMap<>();
    private final List<T> indexed = new ArrayList<>();
    // Registered values are compared by identity, matching the original linear scans.
    private final Map<T, ResourceLocation> keys = new java.util.IdentityHashMap<>();
    private final Map<T, Integer> ids = new java.util.IdentityHashMap<>();

    public synchronized T register(ResourceLocation key, T value) {
        if (values.containsKey(key) || keys.containsKey(value) || indexed.contains(value))
            throw new IllegalArgumentException("Duplicate registry value " + key);
        values.put(key, value);
        keys.put(value, key);
        ids.put(value, indexed.size());
        indexed.add(value);
        return value;
    }

    public T get(ResourceLocation key) {
        return values.get(key);
    }

    public ResourceLocation getKey(T value) {
        return keys.get(value);
    }

    public int getId(T value) {
        Integer id = ids.get(value);
        return id == null ? -1 : id;
    }

    public T byId(int id) {
        return id >= 0 && id < indexed.size() ? indexed.get(id) : null;
    }

    public Stream<T> stream() {
        return new ArrayList<>(indexed).stream();
    }
}
