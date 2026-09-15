package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.Map;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveResult;

public final class ComponentPlanningResult {

    private final int componentId;

    public int componentId() {
        return componentId;
    }

    private final Type type;

    public Type type() {
        return type;
    }

    private final Status status;

    public Status status() {
        return status;
    }

    private final Map<Object, Long> requiredOutputs;

    public Map<Object, Long> requiredOutputs() {
        return requiredOutputs;
    }

    private final Set<ICraftingPatternDetails> patterns;

    public Set<ICraftingPatternDetails> patterns() {
        return patterns;
    }

    private final Set<ICraftingPatternDetails> executionPatterns;

    public Set<ICraftingPatternDetails> executionPatterns() {
        return executionPatterns;
    }

    private final CyclePlanningStatus cycleStatus;

    public CyclePlanningStatus cycleStatus() {
        return cycleStatus;
    }

    private final CycleExternalDemandStatus externalDemandStatus;

    public CycleExternalDemandStatus externalDemandStatus() {
        return externalDemandStatus;
    }

    private final Map<Object, Long> externalMissingItems;

    public Map<Object, Long> externalMissingItems() {
        return externalMissingItems;
    }

    private final String diagnostic;

    public String diagnostic() {
        return diagnostic;
    }

    private final CycleSolveResult cycleResult;

    public CycleSolveResult cycleResult() {
        return cycleResult;
    }

    private final CycleExecutionDisposition cycleDisposition;

    public CycleExecutionDisposition cycleDisposition() {
        return cycleDisposition;
    }

    private final Map<Object, Long> stockReservations;

    public Map<Object, Long> stockReservations() {
        return stockReservations;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ComponentPlanningResult)) return false;
        ComponentPlanningResult other = (ComponentPlanningResult) value;
        return java.util.Objects.equals(componentId, other.componentId) && java.util.Objects.equals(type, other.type)
            && java.util.Objects.equals(status, other.status)
            && java.util.Objects.equals(requiredOutputs, other.requiredOutputs)
            && java.util.Objects.equals(patterns, other.patterns)
            && java.util.Objects.equals(executionPatterns, other.executionPatterns)
            && java.util.Objects.equals(cycleStatus, other.cycleStatus)
            && java.util.Objects.equals(externalDemandStatus, other.externalDemandStatus)
            && java.util.Objects.equals(externalMissingItems, other.externalMissingItems)
            && java.util.Objects.equals(diagnostic, other.diagnostic)
            && java.util.Objects.equals(cycleResult, other.cycleResult)
            && java.util.Objects.equals(cycleDisposition, other.cycleDisposition)
            && java.util.Objects.equals(stockReservations, other.stockReservations);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            componentId,
            type,
            status,
            requiredOutputs,
            patterns,
            executionPatterns,
            cycleStatus,
            externalDemandStatus,
            externalMissingItems,
            diagnostic,
            cycleResult,
            cycleDisposition,
            stockReservations);
    }

    public enum Type {
        ACYCLIC,
        CYCLIC
    }

    public enum Status {
        PLANNED,
        NOT_REQUIRED,
        UNRESOLVED,
        UNSUPPORTED,

        UNREPRESENTABLE,

        SOLVED_NOT_EMITTED
    }

    public ComponentPlanningResult(int componentId, Type type, Status status, Map<Object, Long> requiredOutputs,
        CyclePlanningStatus cycleStatus, String diagnostic) {
        this(
            componentId,
            type,
            status,
            requiredOutputs,
            com.google.common.collect.ImmutableSet.of(),
            com.google.common.collect.ImmutableSet.of(),
            cycleStatus,
            null,
            com.google.common.collect.ImmutableMap.of(),
            diagnostic,
            null,
            type == Type.CYCLIC ? CycleExecutionDisposition.BLOCKED : CycleExecutionDisposition.NOT_REQUIRED,
            com.google.common.collect.ImmutableMap.of());
    }

    public ComponentPlanningResult(int componentId, Type type, Status status, Map<Object, Long> requiredOutputs,
        Set<ICraftingPatternDetails> patterns, CyclePlanningStatus cycleStatus,
        CycleExternalDemandStatus externalDemandStatus, Map<Object, Long> externalMissingItems, String diagnostic,
        CycleSolveResult cycleResult) {
        this(
            componentId,
            type,
            status,
            requiredOutputs,
            patterns,
            patterns,
            cycleStatus,
            externalDemandStatus,
            externalMissingItems,
            diagnostic,
            cycleResult,
            type == Type.CYCLIC ? CycleExecutionDisposition.BLOCKED : CycleExecutionDisposition.NOT_REQUIRED,
            com.google.common.collect.ImmutableMap.of());
    }

    public ComponentPlanningResult(int componentId, Type type, Status status, Map<Object, Long> requiredOutputs,
        Set<ICraftingPatternDetails> patterns, Set<ICraftingPatternDetails> executionPatterns,
        CyclePlanningStatus cycleStatus, CycleExternalDemandStatus externalDemandStatus,
        Map<Object, Long> externalMissingItems, String diagnostic, CycleSolveResult cycleResult,
        CycleExecutionDisposition cycleDisposition, Map<Object, Long> stockReservations) {
        requiredOutputs = com.google.common.collect.ImmutableMap.copyOf(requiredOutputs);
        patterns = com.google.common.collect.ImmutableSet.copyOf(patterns);
        executionPatterns = com.google.common.collect.ImmutableSet.copyOf(executionPatterns);
        externalMissingItems = com.google.common.collect.ImmutableMap.copyOf(externalMissingItems);
        java.util.Objects.requireNonNull(cycleDisposition, "cycleDisposition");
        stockReservations = com.google.common.collect.ImmutableMap.copyOf(stockReservations);

        this.componentId = componentId;
        this.type = type;
        this.status = status;
        this.requiredOutputs = requiredOutputs;
        this.patterns = patterns;
        this.executionPatterns = executionPatterns;
        this.cycleStatus = cycleStatus;
        this.externalDemandStatus = externalDemandStatus;
        this.externalMissingItems = externalMissingItems;
        this.diagnostic = diagnostic;
        this.cycleResult = cycleResult;
        this.cycleDisposition = cycleDisposition;
        this.stockReservations = stockReservations;
    }
}
