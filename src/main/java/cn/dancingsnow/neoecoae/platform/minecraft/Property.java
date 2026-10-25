package cn.dancingsnow.neoecoae.platform.minecraft;

import java.util.Objects;
import java.util.function.Predicate;

/** Typed state property carried by native metadata and the tile description packet. */
public final class Property<T> {

    private final String name;
    private final Class<T> type;
    private final Predicate<T> allowed;

    public Property(String name, Class<T> type, Predicate<T> allowed) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.allowed = Objects.requireNonNull(allowed);
    }

    public String getName() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    public boolean accepts(T value) {
        return value != null && type.isInstance(value) && allowed.test(value);
    }

    public static Property<Boolean> bool(String name) {
        return new Property<>(name, Boolean.class, value -> true);
    }

    public static Property<Integer> integer(String name, int min, int max) {
        return new Property<>(name, Integer.class, value -> value >= min && value <= max);
    }
}
