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

    public synchronized T register(ResourceLocation key, T value) {
        if (values.containsKey(key) || indexed.contains(value))
            throw new IllegalArgumentException("Duplicate registry value " + key);
        values.put(key, value);
        indexed.add(value);
        return value;
    }

    public T get(ResourceLocation key) {
        return values.get(key);
    }

    public ResourceLocation getKey(T value) {
        for (var entry : values.entrySet()) if (entry.getValue() == value) return entry.getKey();
        return null;
    }

    public int getId(T value) {
        for (int i = 0; i < indexed.size(); i++) if (indexed.get(i) == value) return i;
        return -1;
    }

    public T byId(int id) {
        return id >= 0 && id < indexed.size() ? indexed.get(id) : null;
    }

    public Stream<T> stream() {
        return new ArrayList<>(indexed).stream();
    }
}
