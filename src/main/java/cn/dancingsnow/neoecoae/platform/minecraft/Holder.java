package cn.dancingsnow.neoecoae.platform.minecraft;

import java.util.Objects;

/** Direct, immutable registry handle. Target registration happens through Forge's GameRegistry. */
public final class Holder<T> {

    private final T value;

    private Holder(T value) {
        this.value = Objects.requireNonNull(value);
    }

    public static <T> Holder<T> direct(T value) {
        return new Holder<>(value);
    }

    public T value() {
        return value;
    }
}
