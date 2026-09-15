package cn.dancingsnow.neoecoae.crafting.planner.ported.provenance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

/** Immutable material attribution passed from numeric planning to phase construction. */
public final class ExecutionProvenance {

    private final Map<Object, Map<MaterialSource, PlannerAmount>> suppliers;

    public Map<Object, Map<MaterialSource, PlannerAmount>> suppliers() {
        return suppliers;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ExecutionProvenance)) return false;
        ExecutionProvenance other = (ExecutionProvenance) value;
        return java.util.Objects.equals(this.suppliers, other.suppliers);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(suppliers);
    }

    public static final ExecutionProvenance ABSENT = new ExecutionProvenance(
        com.google.common.collect.ImmutableMap.of());

    public ExecutionProvenance(Map<Object, Map<MaterialSource, PlannerAmount>> suppliers) {
        Map<Object, Map<MaterialSource, PlannerAmount>> frozen = new LinkedHashMap<>();
        suppliers.forEach((key, sources) -> frozen.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(sources))));
        suppliers = Collections.unmodifiableMap(frozen);

        this.suppliers = suppliers;
    }

    public Set<MaterialSource> suppliersOf(Object key) {
        return suppliers.getOrDefault(key, com.google.common.collect.ImmutableMap.of())
            .keySet();
    }

    public Map<MaterialSource, PlannerAmount> supplierAmountsOf(Object key) {
        return suppliers.getOrDefault(key, com.google.common.collect.ImmutableMap.of());
    }

    public boolean covers(Object key) {
        return !suppliersOf(key).isEmpty();
    }
}
