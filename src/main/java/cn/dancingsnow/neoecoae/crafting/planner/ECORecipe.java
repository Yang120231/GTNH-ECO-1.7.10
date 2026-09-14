package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Frozen stoichiometry. The recipe token is retained for dispatch; it is never used as material identity. */
public final class ECORecipe<K, P> {

    public final P token;
    public final Map<K, Long> inputs;
    public final Map<K, Long> outputs;

    public ECORecipe(P token, Map<K, Long> inputs, Map<K, Long> outputs) {
        this.token = Objects.requireNonNull(token, "token");
        this.inputs = freeze(inputs);
        this.outputs = freeze(outputs);
        if (this.outputs.isEmpty()) throw new IllegalArgumentException("Recipe has no output");
    }

    static <K> Map<K, Long> freeze(Map<K, Long> values) {
        if (values == null) throw new IllegalArgumentException("Recipe map cannot be null");
        Map<K, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<K, Long> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0)
                throw new IllegalArgumentException("Invalid resource amount");
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
