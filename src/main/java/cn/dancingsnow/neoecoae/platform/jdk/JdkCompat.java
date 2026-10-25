package cn.dancingsnow.neoecoae.platform.jdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Java 8 implementations of the immutable collection and stream contracts used by the input. */
public final class JdkCompat {

    public static java.nio.file.Path writeString(java.nio.file.Path path, CharSequence value,
        java.nio.charset.Charset charset, java.nio.file.OpenOption... options) throws IOException {
        java.nio.ByteBuffer encoded = charset.newEncoder()
            .encode(java.nio.CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return java.nio.file.Files.write(path, bytes, options);
    }

    public static java.nio.file.Path writeString(java.nio.file.Path path, CharSequence value,
        java.nio.file.OpenOption... options) throws IOException {
        return writeString(path, value, java.nio.charset.StandardCharsets.UTF_8, options);
    }

    public static int ceilDiv(int x, int y) {
        int quotient = x / y;
        return (x ^ y) >= 0 && quotient * y != x ? quotient + 1 : quotient;
    }

    public static long ceilDiv(long x, long y) {
        long quotient = x / y;
        return (x ^ y) >= 0 && quotient * y != x ? quotient + 1 : quotient;
    }

    public static int clamp(int value, int minimum, int maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("Invalid clamp bounds");
        return Math.min(maximum, Math.max(minimum, value));
    }

    public static int clamp(long value, int minimum, int maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("Invalid clamp bounds");
        return (int) Math.min(maximum, Math.max(minimum, value));
    }

    public static float clamp(float value, float minimum, float maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("Minimum exceeds maximum");
        return Math.min(Math.max(value, minimum), maximum);
    }

    public static double clamp(double value, double minimum, double maximum) {
        if (minimum > maximum || Double.isNaN(minimum) || Double.isNaN(maximum))
            throw new IllegalArgumentException("Invalid clamp bounds");
        return Math.min(maximum, Math.max(minimum, value));
    }

    private JdkCompat() {}

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        return listCopyOf(Arrays.asList(values));
    }

    public static <T> List<T> listCopyOf(Collection<? extends T> values) {
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) copy.add(Objects.requireNonNull(value));
        return Collections.unmodifiableList(copy);
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        Set<T> result = new LinkedHashSet<>();
        for (T value : values)
            if (!result.add(Objects.requireNonNull(value))) throw new IllegalArgumentException("Duplicate element");
        return Collections.unmodifiableSet(result);
    }

    public static <T> Set<T> setCopyOf(Collection<? extends T> values) {
        Set<T> result = new LinkedHashSet<>();
        for (T value : values) result.add(Objects.requireNonNull(value));
        return Collections.unmodifiableSet(result);
    }

    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4), entry(k5, v5));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4), entry(k5, v5), entry(k6, v6));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
        V v7) {
        return mapOfEntries(
            entry(k1, v1),
            entry(k2, v2),
            entry(k3, v3),
            entry(k4, v4),
            entry(k5, v5),
            entry(k6, v6),
            entry(k7, v7));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
        V v7, K k8, V v8) {
        return mapOfEntries(
            entry(k1, v1),
            entry(k2, v2),
            entry(k3, v3),
            entry(k4, v4),
            entry(k5, v5),
            entry(k6, v6),
            entry(k7, v7),
            entry(k8, v8));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
        V v7, K k8, V v8, K k9, V v9) {
        return mapOfEntries(
            entry(k1, v1),
            entry(k2, v2),
            entry(k3, v3),
            entry(k4, v4),
            entry(k5, v5),
            entry(k6, v6),
            entry(k7, v7),
            entry(k8, v8),
            entry(k9, v9));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
        V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        return mapOfEntries(
            entry(k1, v1),
            entry(k2, v2),
            entry(k3, v3),
            entry(k4, v4),
            entry(k5, v5),
            entry(k6, v6),
            entry(k7, v7),
            entry(k8, v8),
            entry(k9, v9),
            entry(k10, v10));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        return mapOfEntries(entry(k1, v1));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3));
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(Objects.requireNonNull(key), Objects.requireNonNull(value));
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... values) {
        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<? extends K, ? extends V> value : values) {
            K key = Objects.requireNonNull(value.getKey());
            V item = Objects.requireNonNull(value.getValue());
            if (result.put(key, item) != null) throw new IllegalArgumentException("Duplicate key: " + key);
        }
        return Collections.unmodifiableMap(result);
    }

    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> values) {
        Map<K, V> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
        return Collections.unmodifiableMap(result);
    }

    /** Stream.toList accepts nulls and returns an immutable list. */
    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
    }

    public static <T> T first(List<T> values) {
        if (values.isEmpty()) throw new NoSuchElementException();
        return values.get(0);
    }

    public static <T> T last(List<T> values) {
        if (values.isEmpty()) throw new NoSuchElementException();
        return values.get(values.size() - 1);
    }

    public static <T> T first(Deque<T> values) {
        return values.getFirst();
    }

    public static <T> T last(Deque<T> values) {
        return values.getLast();
    }

    public static <T> T removeFirst(List<T> values) {
        if (values.isEmpty()) throw new NoSuchElementException();
        return values.remove(0);
    }

    public static <T> T removeLast(List<T> values) {
        if (values.isEmpty()) throw new NoSuchElementException();
        return values.remove(values.size() - 1);
    }

    public static <T> T removeFirst(Deque<T> values) {
        return values.removeFirst();
    }

    public static <T> T removeLast(Deque<T> values) {
        return values.removeLast();
    }

    public static boolean isBlank(String value) {
        for (int i = 0; i < value.length();) {
            int point = value.codePointAt(i);
            if (!Character.isWhitespace(point)) return false;
            i += Character.charCount(point);
        }
        return true;
    }

    public static <T> List<T> reversed(List<T> values) {
        return new AbstractList<T>() {

            @Override
            public int size() {
                return values.size();
            }

            @Override
            public T get(int index) {
                if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();
                return values.get(size() - 1 - index);
            }

            @Override
            public T set(int index, T item) {
                if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();
                return values.set(size() - 1 - index, item);
            }

            @Override
            public T remove(int index) {
                if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();
                return values.remove(size() - 1 - index);
            }

            @Override
            public void add(int index, T item) {
                if (index < 0 || index > size()) throw new IndexOutOfBoundsException();
                values.add(size() - index, item);
            }
        };
    }

    public static String repeat(String value, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative repeat count");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    public static byte[] readNBytes(InputStream input, int count) throws IOException {
        if (count < 0) throw new IllegalArgumentException("Negative length");
        ByteArrayOutputStream result = new ByteArrayOutputStream(Math.min(count, 8192));
        byte[] buffer = new byte[Math.min(Math.max(1, count), 8192)];
        int remaining = count;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(remaining, buffer.length));
            if (read < 0) break;
            if (read == 0) {
                int b = input.read();
                if (b < 0) break;
                result.write(b);
                remaining--;
            } else {
                result.write(buffer, 0, read);
                remaining -= read;
            }
        }
        return result.toByteArray();
    }
}
