package cn.dancingsnow.neoecoae.crafting.planner.solve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Plans a solved cycle's boundary inputs, delegating selected cyclic suppliers to the component planner. */
final class ExternalDemandPlanner {

    @com.github.bsideup.jabel.Desugar
    record Outcome(CycleExternalDemandStatus status, KeyCounter directReservations, List<SolveState> states,
        Map<AEKey, Long> missingLeaves, Set<IPatternDetails> selectedPatterns, Map<AEKey, Long> delegatedCycleDemands,
        String diagnostic) {

        boolean solved() {
            return status == CycleExternalDemandStatus.SOLVED;
        }

        Outcome copy() {
            KeyCounter reservations = new KeyCounter();
            for (var entry : directReservations) reservations.add(entry.getKey(), entry.getLongValue());
            List<SolveState> copiedStates = new ArrayList<>(states.size());
            for (SolveState state : states) copiedStates.add(state.copy());
            return new Outcome(
                status,
                reservations,
                copiedStates,
                new LinkedHashMap<>(missingLeaves),
                new LinkedHashSet<>(selectedPatterns),
                new LinkedHashMap<>(delegatedCycleDemands),
                diagnostic);
        }
    }

    private final AcyclicCraftingSolver acyclicSolver;
    private final ActiveRouteSelector routeSelector = new ActiveRouteSelector();
    private final CraftingGraphBuilder graphBuilder = new CraftingGraphBuilder();
    /**
     * A single component plan can ask for the same external boundary several times while it checks startup
     * seeds, recovery witnesses and alternate routes. Keep this cache invocation-local: stock and pattern
     * structure are part of the key, so a later network calculation can never inherit an old proof.
     */
    private static final int MAX_DEMAND_CACHE_ENTRIES = 256;
    private static final int MAX_FILTERED_ROUTE_ENTRIES = 16;
    private static final int MAX_FILTERED_ROUTE_WEIGHT = 50_000;
    private CompiledNetwork cachedNetwork;

    @com.github.bsideup.jabel.Desugar
    private record FilteredRouteKey(Set<AEKey> members, Set<IPatternDetails> excluded, AEKey goal) {}

    @com.github.bsideup.jabel.Desugar
    private record FilteredRoute(CompiledNetwork network, ActiveRouteSelector.Selection selection, int weight) {}

    /** Pure structure only: a different quantity or stock still requires a fresh numeric solve. */
    private final Map<FilteredRouteKey, FilteredRoute> filteredRoutes = new LinkedHashMap<>(16, 0.75F, true);
    private int filteredRouteWeight;
    private final Map<CacheKey, Outcome> demandCache = new LinkedHashMap<>(64, 0.75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Outcome> eldest) {
            return size() > MAX_DEMAND_CACHE_ENTRIES;
        }
    };

    ExternalDemandPlanner(AcyclicCraftingSolver acyclicSolver) {
        this.acyclicSolver = acyclicSolver;
    }

    Outcome solve(CompiledNetwork network, CycleComponent cycle, CycleSolveResult cycleResult,
        PlannerInventorySnapshot inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
        Set<AEKey> delegatedCycleInputs, ECOCancellation cancellation) throws InterruptedException {
        return solve(
            network,
            cycle,
            cycleResult,
            inventory,
            base,
            additionalCycleReservations,
            delegatedCycleInputs,
            false,
            cancellation);
    }

    Outcome solve(CompiledNetwork network, CycleComponent cycle, CycleSolveResult cycleResult,
        PlannerInventorySnapshot inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
        Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions, ECOCancellation cancellation)
        throws InterruptedException {
        return solveDemands(
            network,
            cycle,
            cycleResult.positiveExternalDemand(),
            inventory,
            base,
            additionalCycleReservations,
            delegatedCycleInputs,
            ignorePatternSubstitutions,
            cancellation);
    }

    Outcome solveDemands(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
        PlannerInventorySnapshot inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
        Set<AEKey> delegatedCycleInputs, ECOCancellation cancellation) throws InterruptedException {
        return solveDemands(
            network,
            cycle,
            demands,
            inventory,
            base,
            additionalCycleReservations,
            delegatedCycleInputs,
            false,
            cancellation);
    }

    Outcome solveDemands(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
        PlannerInventorySnapshot inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
        Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions, ECOCancellation cancellation)
        throws InterruptedException {
        cancellation.checkpoint();
        // CompiledNetwork is immutable and shared by all route attempts in this invocation. Comparing its
        // identity both invalidates changed recipes and avoids hashing the complete recipe graph on each hit.
        if (cachedNetwork != network) {
            demandCache.clear();
            filteredRoutes.clear();
            filteredRouteWeight = 0;
            cachedNetwork = network;
        }
        PlannerInventorySnapshot available = inventory.withoutReserved(base.usedAmounts());
        CacheKey cacheKey = new CacheKey(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(cycle.members()),
            cycle.patterns()
                .stream()
                .map(pattern -> pattern.details())
                .collect(java.util.stream.Collectors.toSet()),
            demands.entrySet()
                .stream()
                .map(entry -> new BoundaryDemand(entry.getKey(), entry.getValue()))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            available.amounts(),
            available.unboundedKeys(),
            copyLongMap(additionalCycleReservations),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(delegatedCycleInputs),
            ignorePatternSubstitutions);
        Outcome cached = demandCache.get(cacheKey);
        if (cached != null) return cached.copy();

        Outcome result = solveDemandsUncached(
            network,
            cycle,
            demands,
            available,
            additionalCycleReservations,
            delegatedCycleInputs,
            ignorePatternSubstitutions,
            cancellation);
        if (cacheable(result.status())) demandCache.put(cacheKey, result.copy());
        return result;
    }

    private static boolean cacheable(CycleExternalDemandStatus status) {
        // UNSUPPORTED also represents an exhausted/unknown lower-level search. Never turn that transient result
        // into a proof for a later route attempt; the explicit statuses below are deterministic for this key.
        return status != CycleExternalDemandStatus.UNSUPPORTED;
    }

    private Outcome solveDemandsUncached(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
        PlannerInventorySnapshot stock, Map<AEKey, Long> additionalCycleReservations, Set<AEKey> delegatedCycleInputs,
        boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        PlannerCounter available = new PlannerCounter();
        stock.initialize(available);
        for (var reservation : additionalCycleReservations.entrySet()) {
            PlannerAmount amount = PlannerAmount.of(reservation.getValue());
            PlannerAmount held = available.available(reservation.getKey(), amount);
            if (amount.signum() < 0 || held.compareTo(amount) < 0) {
                return failure(
                    CycleExternalDemandStatus.MISSING,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                        reservation.getKey(),
                        amount.subtract(held)
                            .max(PlannerAmount.ZERO)
                            .longValueExact()),
                    "Cycle-owned stock is unavailable before external-demand planning");
            }
            if (amount.signum() > 0) available.remove(reservation.getKey(), amount);
        }

        KeyCounter direct = new KeyCounter();
        List<SolveState> states = new ArrayList<>();
        Set<IPatternDetails> selected = new LinkedHashSet<>();
        Map<AEKey, Long> delegated = new LinkedHashMap<>();
        Map<AEKey, Long> deficits = new LinkedHashMap<>();
        for (var demand : demands.entrySet()) {
            cancellation.checkpoint();
            // A cyclic supplier owns both the requested output and the stock that can bootstrap its feedback
            // loop. Delegate the complete demand before consuming inventory here; otherwise the downstream
            // component reserves the seed as an ordinary boundary input and the supplier later observes no
            // remaining stock with which to start the cycle.
            if (delegatedCycleInputs.contains(demand.getKey()) && !available.isUnbounded(demand.getKey())) {
                if (demand.getValue() > 0L) {
                    delegated.merge(demand.getKey(), demand.getValue(), Math::addExact);
                }
                continue;
            }
            PlannerAmount fromStock = available.available(demand.getKey(), PlannerAmount.of(demand.getValue()));
            if (fromStock.signum() > 0) {
                available.remove(demand.getKey(), fromStock);
                direct.add(demand.getKey(), fromStock.longValueExact());
            }
            long deficit = demand.getValue() - fromStock.longValueExact();
            if (deficit > 0) deficits.put(demand.getKey(), deficit);
        }
        if (!deficits.isEmpty()) {
            // All roots are in the same solve: full outputs and finite raw stock are credited exactly once.
            Outcome one = solveDeficits(
                network,
                cycle,
                deficits,
                PlannerInventorySnapshot.exact(available.asMap(), available.unboundedKeys()),
                ignorePatternSubstitutions,
                cancellation);
            if (!one.solved()) return one;
            SolveState state = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(one.states());
            states.add(state);
            selected.addAll(one.selectedPatterns());
        }
        return new Outcome(
            CycleExternalDemandStatus.SOLVED,
            direct,
            states,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            selected,
            delegated,
            delegated.isEmpty() ? "External demand solved through inventory and acyclic routes"
                : "External demand solved through inventory, acyclic routes, and delegated cycle components");
    }

    private static Map<AEKey, Long> copyLongMap(Map<AEKey, Long> source) {
        if (source.isEmpty()) return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf();
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapCopyOf(source);
    }

    @com.github.bsideup.jabel.Desugar
    private record BoundaryDemand(AEKey key, long amount) {}

    /** Component numbering/dependency metadata is irrelevant to the filtered external DAG; root order is not. */
    @com.github.bsideup.jabel.Desugar
    private record CacheKey(Set<AEKey> members, Set<IPatternDetails> forbiddenPatterns, List<BoundaryDemand> demands,
        Map<AEKey, PlannerAmount> available, Set<AEKey> unbounded, Map<AEKey, Long> additionalReservations,
        Set<AEKey> delegatedInputs, boolean ignorePatternSubstitutions) {}

    private Outcome solveDeficits(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
        PlannerInventorySnapshot inventory, boolean ignorePatternSubstitutions, ECOCancellation cancellation)
        throws InterruptedException {
        Set<IPatternDetails> forbiddenPatterns = cycle.patterns()
            .stream()
            .map(pattern -> pattern.details())
            .collect(java.util.stream.Collectors.toSet());
        List<AEKey> forbiddenMembers = cycle.members();
        FilteredRoute route = filteredRoute(
            network,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(forbiddenMembers),
            forbiddenPatterns,
            demands.keySet()
                .iterator()
                .next(),
            cancellation);
        CompiledNetwork filtered = route.network();
        var selection = route.selection();
        Set<IPatternDetails> deferredCyclePatterns = selection.cyclicComponents()
            .stream()
            .flatMap(
                component -> component.patterns()
                    .stream())
            .map(pattern -> pattern.details())
            .collect(java.util.stream.Collectors.toSet());
        var solved = acyclicSolver.solveDemands(
            filtered,
            inventory,
            demands,
            selection.choices(),
            deferredCyclePatterns,
            ignorePatternSubstitutions,
            cancellation);
        // A complete exact DAG is usable internally even when its cumulative totals do not fit AE2's fields.
        // Keep genuine shortages/unsupported routes out of the cycle transaction.
        if (solved.status() == PlanningStatus.SUCCESS
            || solved.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE && solved.state()
                .missingAmounts()
                .isEmpty() && solved.state().unsupported.isEmpty()) {
            return new Outcome(
                CycleExternalDemandStatus.SOLVED,
                new KeyCounter(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(solved.state()),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                solved.state().selected.values()
                    .stream()
                    .map(p -> p.details())
                    .collect(java.util.stream.Collectors.toSet()),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                deferredCyclePatterns.isEmpty() ? "External DAG solved"
                    : "External DAG solved with cyclic suppliers deferred to component planning");
        }
        if (solved.status() == PlanningStatus.MISSING_ITEMS) {
            Map<AEKey, Long> missing = positive(
                solved.state()
                    .missingItems());
            if (missing.keySet()
                .stream()
                .anyMatch(forbiddenMembers::contains)) {
                return failure(
                    CycleExternalDemandStatus.FORBIDDEN_ROUTE,
                    missing,
                    "All usable external routes re-enter the current cycle component");
            }
            return new Outcome(
                CycleExternalDemandStatus.MISSING,
                new KeyCounter(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(solved.state()),
                missing,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                "External DAG leaf material is missing");
        }
        if (solved.status() == PlanningStatus.AMOUNT_OVERFLOW) {
            return failure(
                CycleExternalDemandStatus.OVERFLOW,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                "External DAG arithmetic overflow");
        }
        if (solved.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) {
            return failure(
                CycleExternalDemandStatus.UNREPRESENTABLE,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                solved.trace()
                    .diagnostics()
                    .stream()
                    .filter(
                        diagnostic -> diagnostic.code()
                            == cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE)
                    .map(cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic::message)
                    .findFirst()
                    .orElse("External DAG plan exceeds AE2 long range"));
        }
        return failure(
            CycleExternalDemandStatus.UNSUPPORTED,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            "External demands=" + demands
                + " status="
                + solved.status()
                + ": "
                + solved.trace()
                    .diagnostics()
                    .stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .collect(java.util.stream.Collectors.joining("; ")));
    }

    private FilteredRoute filteredRoute(CompiledNetwork network, Set<AEKey> forbiddenMembers,
        Set<IPatternDetails> forbiddenPatterns, AEKey goal, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        FilteredRouteKey key = new FilteredRouteKey(
            forbiddenMembers,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(forbiddenPatterns),
            goal);
        FilteredRoute cached = filteredRoutes.get(key);
        if (cached != null) return cached;
        Map<AEKey, List<cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern>> producers = new LinkedHashMap<>();
        // Exclude only physical patterns owned by this component, retaining independent seed producers.
        network.producers()
            .forEach(
                (material, candidates) -> producers.put(
                    material,
                    candidates.stream()
                        .filter(pattern -> !forbiddenPatterns.contains(pattern.details()))
                        .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList())));
        int patterns = producers.values()
            .stream()
            .mapToInt(List::size)
            .sum();
        int edges = producers.values()
            .stream()
            .flatMap(List::stream)
            .mapToInt(
                p -> p.inputs()
                    .size())
            .sum();
        CompiledNetwork filtered = new CompiledNetwork(
            goal,
            producers,
            network.emittable()
                .stream()
                .filter(material -> !forbiddenMembers.contains(material))
                .collect(java.util.stream.Collectors.toSet()),
            patterns,
            edges);
        var graph = graphBuilder.build(filtered, cancellation);
        var selection = routeSelector.select(graph, cancellation);
        long weight = (long) patterns + edges + producers.size();
        FilteredRoute result = new FilteredRoute(filtered, selection, (int) Math.min(Integer.MAX_VALUE, weight));
        if (!selection.budgetExhausted() && weight <= MAX_FILTERED_ROUTE_WEIGHT) {
            while (!filteredRoutes.isEmpty() && (filteredRoutes.size() >= MAX_FILTERED_ROUTE_ENTRIES
                || filteredRouteWeight + weight > MAX_FILTERED_ROUTE_WEIGHT)) {
                var iterator = filteredRoutes.entrySet()
                    .iterator();
                filteredRouteWeight -= iterator.next()
                    .getValue()
                    .weight();
                iterator.remove();
            }
            filteredRoutes.put(key, result);
            filteredRouteWeight += result.weight();
        }
        return result;
    }

    private static Outcome failure(CycleExternalDemandStatus status, Map<AEKey, Long> missing, String diagnostic) {
        return new Outcome(
            status,
            new KeyCounter(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            missing,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            diagnostic);
    }

    private static Map<AEKey, Long> positive(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }
}
