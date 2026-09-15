package cn.dancingsnow.neoecoae.crafting.planner.ported.cycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.crafting.planner.ported.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.ported.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

/** Minimal stable boundary for the cycle implementation. Amounts are request state, not graph state. */
public final class CycleSolveRequest {

    private final CycleComponent component;

    public CycleComponent component() {
        return component;
    }

    private final Map<Object, Long> requiredOutputs;

    public Map<Object, Long> requiredOutputs() {
        return requiredOutputs;
    }

    private final Map<Object, PlannerAmount> plannerRequiredOutputs;

    public Map<Object, PlannerAmount> plannerRequiredOutputs() {
        return plannerRequiredOutputs;
    }

    private final Map<Object, Long> availableRelevantStock;

    public Map<Object, Long> availableRelevantStock() {
        return availableRelevantStock;
    }

    private final List<ComponentDependency> externalResourceBoundary;

    public List<ComponentDependency> externalResourceBoundary() {
        return externalResourceBoundary;
    }

    private final PlannerOptions options;

    public PlannerOptions options() {
        return options;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CycleSolveRequest)) return false;
        CycleSolveRequest other = (CycleSolveRequest) value;
        return java.util.Objects.equals(component, other.component)
            && java.util.Objects.equals(requiredOutputs, other.requiredOutputs)
            && java.util.Objects.equals(plannerRequiredOutputs, other.plannerRequiredOutputs)
            && java.util.Objects.equals(availableRelevantStock, other.availableRelevantStock)
            && java.util.Objects.equals(externalResourceBoundary, other.externalResourceBoundary)
            && java.util.Objects.equals(options, other.options);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            component,
            requiredOutputs,
            plannerRequiredOutputs,
            availableRelevantStock,
            externalResourceBoundary,
            options);
    }

    /** Source-compatible constructor for callers that only have AE2-sized request amounts. */
    public CycleSolveRequest(CycleComponent component, Map<Object, Long> requiredOutputs,
        Map<Object, Long> availableRelevantStock, List<ComponentDependency> externalResourceBoundary,
        PlannerOptions options) {
        this(
            component,
            requiredOutputs,
            exact(requiredOutputs),
            availableRelevantStock,
            externalResourceBoundary,
            options);
    }

    /** Exact planner request constructor; the legacy map is only a representable compatibility projection. */
    public CycleSolveRequest(CycleComponent component, Map<Object, Long> requiredOutputs,
        Map<Object, PlannerAmount> plannerRequiredOutputs, Map<Object, Long> availableRelevantStock,
        List<ComponentDependency> externalResourceBoundary, PlannerOptions options) {
        this.component = component;
        this.requiredOutputs = com.google.common.collect.ImmutableMap.copyOf(requiredOutputs);
        this.plannerRequiredOutputs = com.google.common.collect.ImmutableMap.copyOf(plannerRequiredOutputs);
        this.availableRelevantStock = com.google.common.collect.ImmutableMap.copyOf(availableRelevantStock);
        this.externalResourceBoundary = com.google.common.collect.ImmutableList.copyOf(externalResourceBoundary);
        this.options = options == null ? new PlannerOptions() : options;
    }

    public static final class PlannerOptions {

        private final CycleSolveLimits limits;

        public CycleSolveLimits limits() {
            return limits;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PlannerOptions)) return false;
            PlannerOptions other = (PlannerOptions) value;
            return java.util.Objects.equals(limits, other.limits);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(limits);
        }

        public PlannerOptions() {
            this(CycleSolveLimits.DEFAULT);
        }

        /** Source-compatible bridge for callers compiled against the former enable flag. */
        public PlannerOptions(boolean ignoredCyclePlanningEnabled, CycleSolveLimits limits) {
            this(limits);
        }

        public PlannerOptions(CycleSolveLimits limits) {
            if (limits == null) limits = CycleSolveLimits.DEFAULT;

            this.limits = limits;
        }
    }

    public long requiredOutput(Object key) {
        return Math.max(0L, requiredOutputs.getOrDefault(key, 0L));
    }

    public PlannerAmount requiredOutputAmount(Object key) {
        return plannerRequiredOutputs.getOrDefault(key, PlannerAmount.ZERO)
            .max(PlannerAmount.ZERO);
    }

    public long stockOf(Object key) {
        return Math.max(0L, availableRelevantStock.getOrDefault(key, 0L));
    }

    private static Map<Object, PlannerAmount> exact(Map<Object, Long> amounts) {
        Map<Object, PlannerAmount> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, PlannerAmount.of(amount == null ? 0L : amount)));
        return result;
    }
}
