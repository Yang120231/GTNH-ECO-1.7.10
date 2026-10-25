package cn.dancingsnow.neoecoae.crafting.planner.result;

import java.util.Map;

/** Semantic ownership transition used by the reference replay harness. */
@com.github.bsideup.jabel.Desugar
public record OwnershipEvent(Type type, Object resource, long amount) {

    public enum Type {
        DISPATCH_COMMITTED,
        OUTPUT_RETURNED,
        OWNERSHIP_RELEASED
    }

    public Map<?, Long> consumed() {
        if (type != Type.DISPATCH_COMMITTED || !(resource instanceof Map<?, ?>values))
            return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf();
        java.util.LinkedHashMap<Object, Long> result = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!(value instanceof Long amountValue)) throw new IllegalArgumentException("Invalid consumed event");
            result.put(key, amountValue);
        });
        return result;
    }
}
