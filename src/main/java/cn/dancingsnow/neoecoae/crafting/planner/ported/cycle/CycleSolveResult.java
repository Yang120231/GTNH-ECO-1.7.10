package cn.dancingsnow.neoecoae.crafting.planner.ported.cycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

/**
 * Complete answer of one cycle solve. Every numeric field is expressed in the SCC's own terms:
 *
 * <ul>
 * <li>{@code patternTimes} — how many times each SCC pattern fires.</li>
 * <li>{@code externalDemand} — material the SCC boundary must deliver, net of the SCC's own byproducts.</li>
 * <li>{@code requiredSeed} — start-up stock the witness needs before the loop is self-sustaining.</li>
 * <li>{@code seedShortfall} — the part of {@code requiredSeed} the stock snapshot cannot cover.</li>
 * <li>{@code producedOutputs} — gross production of the witness, byproducts included.</li>
 * <li>{@code deliverableOutputs} — what is on hand for the required keys once the witness has run.</li>
 * <li>{@code executionWitness} — a concrete, replayable, never-negative firing order.</li>
 * <li>{@code executionPlan} — the same work in compact {@code pattern × count} form.</li>
 * </ul>
 *
 * <p>
 * {@code requiredSeed} together with {@code externalDemand} is exactly what the witness needs:
 * replaying the witness from {@code requiredSeed}, importing {@code externalDemand} across the
 * boundary, keeps every stock level at or above zero.
 *
 * <p>
 * {@code executionPlan} is metadata, never a second source of truth. For an expanded witness it is the
 * order-preserving run-length encoding of {@code executionWitness}. The bounded solver may also keep a verified
 * multi-pattern witness in compact batch form when expanding it would be proportional to millions of firings; in
 * that case the witness list is empty and the ordered {@code executionPlan} is the available execution trace.
 */
public final class CycleSolveResult {

    private final CycleSolveStatus status;

    public CycleSolveStatus status() {
        return status;
    }

    private final ExecutionCountKnowledge executionCountKnowledge;

    public ExecutionCountKnowledge executionCountKnowledge() {
        return executionCountKnowledge;
    }

    private final Map<Object, PlannerAmount> exactPatternTimes;

    public Map<Object, PlannerAmount> exactPatternTimes() {
        return exactPatternTimes;
    }

    private final Map<Object, Long> patternTimes;

    public Map<Object, Long> patternTimes() {
        return patternTimes;
    }

    private final Map<Object, Long> externalDemand;

    public Map<Object, Long> externalDemand() {
        return externalDemand;
    }

    private final Map<Object, Long> requiredSeed;

    public Map<Object, Long> requiredSeed() {
        return requiredSeed;
    }

    private final Map<Object, Long> seedShortfall;

    public Map<Object, Long> seedShortfall() {
        return seedShortfall;
    }

    private final Map<Object, Long> producedOutputs;

    public Map<Object, Long> producedOutputs() {
        return producedOutputs;
    }

    private final Map<Object, Long> deliverableOutputs;

    public Map<Object, Long> deliverableOutputs() {
        return deliverableOutputs;
    }

    private final List<CycleFiring> executionWitness;

    public List<CycleFiring> executionWitness() {
        return executionWitness;
    }

    private final List<PatternRun> executionPlan;

    public List<PatternRun> executionPlan() {
        return executionPlan;
    }

    private final List<CycleSolveDiagnostic> diagnostics;

    public List<CycleSolveDiagnostic> diagnostics() {
        return diagnostics;
    }

    private final CycleSolveMetrics metrics;

    public CycleSolveMetrics metrics() {
        return metrics;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CycleSolveResult)) return false;
        CycleSolveResult other = (CycleSolveResult) value;
        return java.util.Objects.equals(status, other.status)
            && java.util.Objects.equals(executionCountKnowledge, other.executionCountKnowledge)
            && java.util.Objects.equals(exactPatternTimes, other.exactPatternTimes)
            && java.util.Objects.equals(patternTimes, other.patternTimes)
            && java.util.Objects.equals(externalDemand, other.externalDemand)
            && java.util.Objects.equals(requiredSeed, other.requiredSeed)
            && java.util.Objects.equals(seedShortfall, other.seedShortfall)
            && java.util.Objects.equals(producedOutputs, other.producedOutputs)
            && java.util.Objects.equals(deliverableOutputs, other.deliverableOutputs)
            && java.util.Objects.equals(executionWitness, other.executionWitness)
            && java.util.Objects.equals(executionPlan, other.executionPlan)
            && java.util.Objects.equals(diagnostics, other.diagnostics)
            && java.util.Objects.equals(metrics, other.metrics);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            status,
            executionCountKnowledge,
            exactPatternTimes,
            patternTimes,
            externalDemand,
            requiredSeed,
            seedShortfall,
            producedOutputs,
            deliverableOutputs,
            executionWitness,
            executionPlan,
            diagnostics,
            metrics);
    }

    public CycleSolveResult(CycleSolveStatus status, ExecutionCountKnowledge executionCountKnowledge,
        Map<Object, PlannerAmount> exactPatternTimes, Map<Object, Long> patternTimes, Map<Object, Long> externalDemand,
        Map<Object, Long> requiredSeed, Map<Object, Long> seedShortfall, Map<Object, Long> producedOutputs,
        Map<Object, Long> deliverableOutputs, List<CycleFiring> executionWitness, List<PatternRun> executionPlan,
        List<CycleSolveDiagnostic> diagnostics, CycleSolveMetrics metrics) {
        executionCountKnowledge = executionCountKnowledge == null ? ExecutionCountKnowledge.UNKNOWN
            : executionCountKnowledge;
        exactPatternTimes = com.google.common.collect.ImmutableMap.copyOf(exactPatternTimes);
        patternTimes = com.google.common.collect.ImmutableMap.copyOf(patternTimes);
        externalDemand = com.google.common.collect.ImmutableMap.copyOf(externalDemand);
        requiredSeed = com.google.common.collect.ImmutableMap.copyOf(requiredSeed);
        seedShortfall = com.google.common.collect.ImmutableMap.copyOf(seedShortfall);
        producedOutputs = com.google.common.collect.ImmutableMap.copyOf(producedOutputs);
        deliverableOutputs = com.google.common.collect.ImmutableMap.copyOf(deliverableOutputs);
        executionWitness = com.google.common.collect.ImmutableList.copyOf(executionWitness);
        executionPlan = com.google.common.collect.ImmutableList.copyOf(executionPlan);
        diagnostics = com.google.common.collect.ImmutableList.copyOf(diagnostics);
        if (status == CycleSolveStatus.SUCCESS && !seedShortfall.isEmpty()) {
            throw new IllegalArgumentException("A successful cycle solve cannot report a seed shortfall");
        }
        if (!executionWitness.isEmpty() && totalRunCount(executionPlan) != executionWitness.size()) {
            throw new IllegalArgumentException("A compact execution plan must account for every witness step");
        }

        this.status = status;
        this.executionCountKnowledge = executionCountKnowledge;
        this.exactPatternTimes = exactPatternTimes;
        this.patternTimes = patternTimes;
        this.externalDemand = externalDemand;
        this.requiredSeed = requiredSeed;
        this.seedShortfall = seedShortfall;
        this.producedOutputs = producedOutputs;
        this.deliverableOutputs = deliverableOutputs;
        this.executionWitness = executionWitness;
        this.executionPlan = executionPlan;
        this.diagnostics = diagnostics;
        this.metrics = metrics;
    }

    /** Runtime-compatible constructor. Exact counts are derived before any diagnostic arithmetic. */
    public CycleSolveResult(CycleSolveStatus status, Map<Object, Long> patternTimes, Map<Object, Long> externalDemand,
        Map<Object, Long> requiredSeed, Map<Object, Long> seedShortfall, Map<Object, Long> producedOutputs,
        Map<Object, Long> deliverableOutputs, List<CycleFiring> executionWitness, List<PatternRun> executionPlan,
        List<CycleSolveDiagnostic> diagnostics, CycleSolveMetrics metrics) {
        this(
            status,
            inferredKnowledge(status, patternTimes),
            exact(patternTimes),
            patternTimes,
            externalDemand,
            requiredSeed,
            seedShortfall,
            producedOutputs,
            deliverableOutputs,
            executionWitness,
            executionPlan,
            diagnostics,
            metrics);
    }

    /** Legacy shape: the compact plan is the run-length encoding of the per-firing witness. */
    public CycleSolveResult(CycleSolveStatus status, Map<Object, Long> patternTimes, Map<Object, Long> externalDemand,
        Map<Object, Long> requiredSeed, Map<Object, Long> seedShortfall, Map<Object, Long> producedOutputs,
        Map<Object, Long> deliverableOutputs, List<CycleFiring> executionWitness,
        List<CycleSolveDiagnostic> diagnostics, CycleSolveMetrics metrics) {
        this(
            status,
            patternTimes,
            externalDemand,
            requiredSeed,
            seedShortfall,
            producedOutputs,
            deliverableOutputs,
            executionWitness,
            compress(executionWitness),
            diagnostics,
            metrics);
    }

    /** Order-preserving run-length encoding; it never merges non-adjacent runs. */
    private static List<PatternRun> compress(List<CycleFiring> witness) {
        List<PatternRun> runs = new ArrayList<>();
        CompiledPattern current = null;
        long count = 0;
        for (CycleFiring firing : witness) {
            if (current != null && current.details() == firing.pattern()
                .details()) {
                count++;
                continue;
            }
            if (current != null) runs.add(new PatternRun(current, count));
            current = firing.pattern();
            count = 1;
        }
        if (current != null) runs.add(new PatternRun(current, count));
        return com.google.common.collect.ImmutableList.copyOf(runs);
    }

    private static long totalRunCount(List<PatternRun> runs) {
        long total = 0;
        for (PatternRun run : runs) total = Math.addExact(total, run.count());
        return total;
    }

    public static CycleSolveResult failure(CycleSolveStatus status, List<CycleSolveDiagnostic> diagnostics,
        CycleSolveMetrics metrics) {
        return new CycleSolveResult(
            status,
            ExecutionCountKnowledge.UNKNOWN,
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableList.of(),
            com.google.common.collect.ImmutableList.of(),
            diagnostics,
            metrics);
    }

    public static CycleSolveResult failure(CycleSolveStatus status, CycleSolveDiagnostic.Code code, String message) {
        return failure(
            status,
            com.google.common.collect.ImmutableList.of(new CycleSolveDiagnostic(code, message)),
            CycleSolveMetrics.NONE);
    }

    public static CycleSolveResult cancelled() {
        return failure(CycleSolveStatus.CANCELLED, CycleSolveDiagnostic.Code.CANCELLED, "Cycle solving was cancelled");
    }

    public static CycleSolveResult notImplemented(String message) {
        return failure(CycleSolveStatus.NOT_IMPLEMENTED, CycleSolveDiagnostic.Code.NOT_IMPLEMENTED, message);
    }

    /** Pure annotation: same answer, extra explanation. Used to record why a fast path stepped aside. */
    public CycleSolveResult withAdditionalDiagnostics(List<CycleSolveDiagnostic> extra) {
        if (extra.isEmpty()) return this;
        List<CycleSolveDiagnostic> merged = new ArrayList<>(extra);
        merged.addAll(diagnostics);
        return new CycleSolveResult(
            status,
            executionCountKnowledge,
            exactPatternTimes,
            patternTimes,
            externalDemand,
            requiredSeed,
            seedShortfall,
            producedOutputs,
            deliverableOutputs,
            executionWitness,
            executionPlan,
            merged,
            metrics);
    }

    /** Exact total firings in the plan; it may exceed the legacy long projection. */
    public PlannerAmount plannerTotalFirings() {
        PlannerAmount total = PlannerAmount.ZERO;
        for (PlannerAmount count : exactPatternTimes.values()) total = total.add(count);
        return total;
    }

    /**
     * Legacy long accessor. Callers that cannot prove representability must use
     * {@link #plannerTotalFirings()} instead; this conversion intentionally never saturates or truncates.
     */
    public long totalFirings() {
        return plannerTotalFirings().longValueExact();
    }

    public String summary() {
        String detail = diagnostics.stream()
            .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
            .collect(Collectors.joining("; "));
        return detail.isEmpty() ? status.name() : status.name() + " (" + detail + ")";
    }

    /** Positive-only view, convenient for merging into planner reporting. */
    public Map<Object, Long> positiveExternalDemand() {
        Map<Object, Long> result = new LinkedHashMap<>();
        externalDemand.forEach((key, amount) -> { if (amount != null && amount > 0) result.put(key, amount); });
        return com.google.common.collect.ImmutableMap.copyOf(result);
    }

    public boolean hasExactExecutionCounts() {
        return executionCountKnowledge == ExecutionCountKnowledge.EXACT;
    }

    private static Map<Object, PlannerAmount> exact(Map<Object, Long> values) {
        Map<Object, PlannerAmount> result = new LinkedHashMap<>();
        values.forEach((pattern, count) -> result.put(pattern, PlannerAmount.of(count == null ? 0L : count)));
        return com.google.common.collect.ImmutableMap.copyOf(result);
    }

    private static ExecutionCountKnowledge inferredKnowledge(CycleSolveStatus status, Map<Object, Long> patternTimes) {
        return status == CycleSolveStatus.SUCCESS || !patternTimes.isEmpty() ? ExecutionCountKnowledge.EXACT
            : ExecutionCountKnowledge.UNKNOWN;
    }
}
