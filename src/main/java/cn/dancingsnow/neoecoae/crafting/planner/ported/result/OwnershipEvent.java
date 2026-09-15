package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.Map;

/** Semantic ownership transition used by the reference replay harness. */
public final class OwnershipEvent {

    private final Type type;

    public Type type() {
        return type;
    }

    private final Object resource;

    public Object resource() {
        return resource;
    }

    private final long amount;

    public long amount() {
        return amount;
    }

    public OwnershipEvent(Type type, Object resource, long amount) {
        this.type = type;
        this.resource = resource;
        this.amount = amount;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof OwnershipEvent)) return false;
        OwnershipEvent other = (OwnershipEvent) value;
        return java.util.Objects.equals(type, other.type) && java.util.Objects.equals(resource, other.resource)
            && java.util.Objects.equals(amount, other.amount);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, resource, amount);
    }

    public enum Type {
        DISPATCH_COMMITTED,
        OUTPUT_RETURNED,
        OWNERSHIP_RELEASED
    }

    public Map<?, Long> consumed() {
        if (type != Type.DISPATCH_COMMITTED || !(resource instanceof Map<?, ?>values))
            return com.google.common.collect.ImmutableMap.of();
        java.util.LinkedHashMap<Object, Long> result = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!(value instanceof Long amountValue)) throw new IllegalArgumentException("Invalid consumed event");
            result.put(key, amountValue);
        });
        return com.google.common.collect.ImmutableMap.copyOf(result);
    }
}
