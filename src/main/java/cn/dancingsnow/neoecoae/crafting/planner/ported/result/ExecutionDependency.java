package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.Objects;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** A physical producer-to-consumer edge. Multiple edges for one key are intentional. */
public final class ExecutionDependency {

    private final ICraftingPatternDetails producer;

    public ICraftingPatternDetails producer() {
        return producer;
    }

    private final ICraftingPatternDetails consumer;

    public ICraftingPatternDetails consumer() {
        return consumer;
    }

    private final Object key;

    public Object key() {
        return key;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ExecutionDependency)) return false;
        ExecutionDependency other = (ExecutionDependency) value;
        return java.util.Objects.equals(producer, other.producer) && java.util.Objects.equals(consumer, other.consumer)
            && java.util.Objects.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(producer, consumer, key);
    }

    public ExecutionDependency(ICraftingPatternDetails producer, ICraftingPatternDetails consumer, Object key) {
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(key, "key");

        this.producer = producer;
        this.consumer = consumer;
        this.key = key;
    }
}
