package cn.dancingsnow.neoecoae.platform.minecraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.block.Block;

/** Immutable property projection; no formation or storage decisions live in this class. */
public final class BlockState {

    private final Block block;
    private final Map<Property<?>, Object> values;

    public BlockState(Block block, Map<Property<?>, Object> values) {
        this.block = Objects.requireNonNull(block);
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.values.forEach((property, value) -> validate(property, value));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void validate(Property property, Object value) {
        if (!property.accepts(value))
            throw new IllegalArgumentException("Invalid state property: " + property.getName());
    }

    public static BlockState defaultState(Block block) {
        return block instanceof NativeStateBlock ? ((NativeStateBlock) block).defaultBlockState()
            : new BlockState(block, Collections.emptyMap());
    }

    public Block getBlock() {
        return block;
    }

    public boolean is(Block other) {
        return block == other;
    }

    public boolean is(Holder<Block> other) {
        return is(other.value());
    }

    public boolean hasProperty(Property<?> property) {
        return values.containsKey(property);
    }

    public Set<Property<?>> getProperties() {
        return values.keySet();
    }

    public Map<Property<?>, Object> getValues() {
        return values;
    }

    public <T> T getValue(Property<T> property) {
        Object value = values.get(property);
        if (value == null) throw new IllegalArgumentException("Missing state property: " + property.getName());
        return property.type()
            .cast(value);
    }

    public <T> Optional<T> getOptionalValue(Property<T> property) {
        return Optional.ofNullable(
            property.type()
                .cast(values.get(property)));
    }

    public <T> BlockState setValue(Property<T> property, T value) {
        if (!hasProperty(property)) throw new IllegalArgumentException("Missing state property: " + property.getName());
        validate(property, value);
        if (Objects.equals(values.get(property), value)) return this;
        Map<Property<?>, Object> changed = new LinkedHashMap<>(values);
        changed.put(property, value);
        return new BlockState(block, changed);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockState && block == ((BlockState) other).block
            && values.equals(((BlockState) other).values);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(block) + values.hashCode();
    }
}
