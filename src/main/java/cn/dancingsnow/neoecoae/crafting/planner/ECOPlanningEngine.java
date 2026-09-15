package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResult.Status;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResult.Step;

/**
 * Legacy planner kernel: exact amounts, alternate routes, byproducts and seeded cycles.
 * Search branches own their inventory and schedule. A failed branch cannot manufacture stock.
 */
public final class ECOPlanningEngine<K, P> {

    private final Map<K, List<ECORecipe<K, P>>> producers = new LinkedHashMap<>();
    private final BooleanSupplier cancelled;
    private final int maxSteps;
    private int visited;
    private final long maxNanos;
    private long started;
    private ECOCyclePlannerBridge<K, P> cyclePlanner;
    private boolean cyclePlanningEnabled = true;

    public ECOPlanningEngine<K, P> cyclePlanningEnabled(boolean enabled) {
        cyclePlanningEnabled = enabled;
        return this;
    }

    public ECOPlanningEngine(List<ECORecipe<K, P>> recipes, BooleanSupplier cancelled, int maxSteps) {
        this(recipes, cancelled, maxSteps, Long.MAX_VALUE);
    }

    public ECOPlanningEngine(List<ECORecipe<K, P>> recipes, BooleanSupplier cancelled, int maxSteps, long maxNanos) {
        this.cancelled = cancelled;
        this.maxNanos = Math.max(1, maxNanos);
        this.maxSteps = Math.max(1, maxSteps);
        for (ECORecipe<K, P> recipe : recipes) {
            for (K output : recipe.outputs.keySet()) {
                producers.computeIfAbsent(output, ignored -> new ArrayList<>())
                    .add(recipe);
            }
        }
    }

    public ECOPlanningResult<K, P> planAdditional(K goal, long amount, Map<K, Long> inventory) {
        if (goal == null || amount <= 0) throw new IllegalArgumentException("Invalid crafting request");
        long existing = get(inventory, goal);
        if (existing < 0) throw new IllegalArgumentException("Invalid inventory amount");
        if (amount > Long.MAX_VALUE - existing) {
            return new ECOPlanningResult<>(
                Status.AMOUNT_OVERFLOW,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                0);
        }
        return plan(goal, amount + existing, amount, inventory);
    }

    public ECOPlanningResult<K, P> plan(K goal, long amount, Map<K, Long> inventory) {
        return plan(goal, amount, amount, inventory);
    }

    private ECOPlanningResult<K, P> plan(K goal, long target, long amount, Map<K, Long> inventory) {
        if (goal == null || amount <= 0) throw new IllegalArgumentException("Invalid crafting request");
        visited = 0;
        started = System.nanoTime();
        State state = new State(inventory);
        try {
            try {
                cyclePlanner = cyclePlanningEnabled
                    ? new ECOCyclePlannerBridge<>(goal, relevantRecipes(goal), this::check)
                    : null;
                ensure(goal, target, state, new HashSet<K>());
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
                throw new Failure(Status.CANCELLED, null, 0);
            } catch (Failure failure) {
                if (!cyclePlanningEnabled || failure.status != Status.CYCLE_UNRESOLVED) throw failure;
                state = solveBounded(goal, target, new State(inventory));
            }
            consume(goal, amount, state);
            validate(goal, amount, state);
            long bytes = 8;
            for (long value : state.extracted.values()) bytes = Math.addExact(bytes, value);
            for (Step<K, P> step : state.steps) {
                bytes = Math.addExact(bytes, 8);
                bytes = Math.addExact(bytes, step.crafts);
            }
            return new ECOPlanningResult<>(Status.SUCCESS, state.extracted, Collections.emptyMap(), state.steps, bytes);
        } catch (Failure failure) {
            Map<K, Long> missing = new LinkedHashMap<>();
            if (failure.key != null && failure.amount > 0) missing.put((K) failure.key, failure.amount);
            return new ECOPlanningResult<>(failure.status, Collections.emptyMap(), missing, Collections.emptyList(), 0);
        } catch (ArithmeticException overflow) {
            return new ECOPlanningResult<>(
                Status.AMOUNT_OVERFLOW,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                0);
        }
    }

    private Set<ECORecipe<K, P>> relevantRecipes(K goal) {
        Set<K> relevant = new java.util.LinkedHashSet<>();
        List<K> pending = new ArrayList<>();
        Set<ECORecipe<K, P>> recipes = new java.util.LinkedHashSet<>();
        relevant.add(goal);
        pending.add(goal);
        for (int i = 0; i < pending.size(); i++) {
            check();
            for (ECORecipe<K, P> recipe : producers.getOrDefault(pending.get(i), Collections.emptyList())) {
                recipes.add(recipe);
                for (K input : recipe.inputs.keySet()) if (relevant.add(input)) pending.add(input);
            }
        }
        return recipes;
    }

    private State solveBounded(K goal, long target, State initial) {
        Set<ECORecipe<K, P>> recipes = relevantRecipes(goal);
        // As in the modern solver, probe maximal safe waves before exploring
        // alternate markings. A growing ring then needs logarithmically many steps.
        State probe = new State(initial);
        Set<Map<K, Long>> probeSeen = new HashSet<>();
        int probeBudget = Math.min(4096, Math.max(1, (maxSteps - visited) / 4));
        for (int wave = 0; wave < probeBudget; wave++) {
            check();
            if (get(probe.stock, goal) >= target) return probe;
            if (!probeSeen.add(new LinkedHashMap<>(probe.stock))) break;
            ECORecipe<K, P> selected = null;
            long count = 0;
            for (ECORecipe<K, P> recipe : recipes) {
                check();
                long maximum = maximumBatch(recipe, probe);
                if (maximum == 0) continue;
                long net = get(recipe.outputs, goal) - get(recipe.inputs, goal);
                if (selected == null || net > 0) {
                    selected = recipe;
                    count = maximum;
                    if (net > 0) {
                        long deficit = target - get(probe.stock, goal);
                        count = Math.min(count, deficit / net + (deficit % net == 0 ? 0 : 1));
                        break;
                    }
                }
            }
            if (selected == null) break;
            if (selected.inputs.isEmpty()) break;
            fire(selected, count, probe, new HashSet<K>());
        }
        java.util.ArrayDeque<State> queue = new java.util.ArrayDeque<>();
        Set<Map<K, Long>> seen = new HashSet<>();
        queue.add(initial);
        seen.add(initial.stock);
        while (!queue.isEmpty()) {
            check();
            State current = queue.remove();
            if (get(current.stock, goal) >= target) return current;
            for (ECORecipe<K, P> recipe : recipes) {
                check();
                long maximum = maximumBatch(recipe, current);
                if (maximum == 0) continue;
                Set<Long> batches = new java.util.LinkedHashSet<>();
                batches.add(1L);
                batches.add(maximum);
                for (Map.Entry<K, Long> output : recipe.outputs.entrySet()) {
                    K key = output.getKey();
                    long delta = output.getValue() - get(recipe.inputs, key);
                    if (delta <= 0) continue;
                    if (key.equals(goal)) addBoundary(batches, target, get(current.stock, key), delta, maximum);
                    for (ECORecipe<K, P> other : recipes)
                        addBoundary(batches, get(other.inputs, key), get(current.stock, key), delta, maximum);
                }
                for (long batch : batches) {
                    check();
                    State next = new State(current);
                    fire(recipe, batch, next, new HashSet<K>());
                    next.stock.entrySet()
                        .removeIf(entry -> entry.getValue() == 0L);
                    if (seen.add(next.stock)) queue.add(next);
                }
            }
        }
        throw new Failure(Status.CYCLE_UNRESOLVED, goal, target);
    }

    private static void addBoundary(Set<Long> batches, long target, long stock, long delta, long maximum) {
        if (target <= stock) return;
        long deficit = target - stock;
        long count = deficit / delta + (deficit % delta == 0 ? 0 : 1);
        if (count <= maximum) batches.add(count);
    }

    private long maximumBatch(ECORecipe<K, P> recipe, State state) {
        long maximum = Long.MAX_VALUE;
        for (Map.Entry<K, Long> input : recipe.inputs.entrySet())
            maximum = Math.min(maximum, get(state.stock, input.getKey()) / input.getValue());
        for (Map.Entry<K, Long> output : recipe.outputs.entrySet()) {
            maximum = Math.min(maximum, Long.MAX_VALUE / output.getValue());
            long delta = output.getValue() - get(recipe.inputs, output.getKey());
            if (delta > 0) maximum = Math.min(maximum, (Long.MAX_VALUE - get(state.stock, output.getKey())) / delta);
        }
        return maximum;
    }

    private void check() {
        if (cancelled.getAsBoolean() || Thread.currentThread()
            .isInterrupted()) throw new Failure(Status.CANCELLED, null, 0);
        if (++visited > maxSteps) throw new Failure(Status.LIMIT_EXCEEDED, null, 0);
        if (System.nanoTime() - started >= maxNanos) throw new Failure(Status.LIMIT_EXCEEDED, null, 0);
    }

    private void ensure(K key, long amount, State state, Set<K> active) {
        check();
        if (get(state.stock, key) >= amount) return;
        if (active.size() >= 128) throw new Failure(Status.LIMIT_EXCEEDED, key, amount);
        if (!active.add(key)) throw new Failure(Status.CYCLE_UNRESOLVED, key, amount - get(state.stock, key));
        try {
            if (solveComponent(key, amount, state, active)) return;
            List<ECORecipe<K, P>> candidates = producers.get(key);
            if (candidates == null) throw new Failure(Status.MISSING_ITEMS, key, amount - get(state.stock, key));
            Failure last = new Failure(Status.CYCLE_UNRESOLVED, key, amount - get(state.stock, key));
            for (ECORecipe<K, P> recipe : candidates) {
                State branch = new State(state);
                try {
                    int circulations = 0;
                    while (get(branch.stock, key) < amount) {
                        if (++circulations > 64) throw new Failure(Status.CYCLE_UNRESOLVED, key, amount);
                        check();
                        long before = get(branch.stock, key);
                        long produced = recipe.outputs.get(key);
                        long retained = get(recipe.inputs, key);
                        long net = produced - retained;
                        if (net <= 0) throw new Failure(Status.CYCLE_UNRESOLVED, key, amount - before);
                        long needed = amount - before;
                        long batches = needed / net + (needed % net == 0 ? 0 : 1);
                        if (retained > 0 && before >= retained && singleFeedback(recipe, key)) {
                            fireGrowth(recipe, key, batches, branch, active);
                            continue;
                        }
                        if (retained > 0) {
                            // The first firing must have a real seed. Later firings may use its returned seed.
                            long ready = before / retained;
                            if (ready == 0) throw new Failure(Status.CYCLE_UNRESOLVED, key, retained - before);
                            batches = Math.min(batches, ready);
                        }
                        State trial = new State(branch);
                        try {
                            fire(recipe, batches, trial, active);
                        } catch (Failure failure) {
                            if (failure.status != Status.CYCLE_UNRESOLVED || batches == 1) throw failure;
                            // A multi-pattern cycle may only have enough seed for one circulation.
                            trial = new State(branch);
                            fire(recipe, 1, trial, active);
                        }
                        if (get(trial.stock, key) <= before) throw new Failure(Status.CYCLE_UNRESOLVED, key, needed);
                        branch.take(trial);
                    }
                    state.take(branch);
                    return;
                } catch (Failure failure) {
                    if (failure.status == Status.CANCELLED || failure.status == Status.LIMIT_EXCEEDED) throw failure;
                    last = failure;
                }
            }
            throw last;
        } finally {
            active.remove(key);
        }
    }

    private boolean solveComponent(K key, long amount, State state, Set<K> active) {
        if (cyclePlanner == null) return false;
        cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveResult result;
        try {
            result = cyclePlanner.solve(key, amount, state.stock, this::check);
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new Failure(Status.CANCELLED, null, 0);
        }
        if (result == null) return false;
        if (!result.status()
            .solved()) {
            switch (result.status()) {
                case UNREPRESENTABLE:
                    throw new ArithmeticException("Cycle execution amount overflow");
                case UNKNOWN_BUDGET:
                case TOO_COMPLEX:
                    throw new Failure(Status.LIMIT_EXCEEDED, key, amount);
                case CANCELLED:
                    throw new Failure(Status.CANCELLED, null, 0);
                default:
                    throw new Failure(Status.CYCLE_UNRESOLVED, key, amount);
            }
        }
        State branch = new State(state);
        for (Map.Entry<Object, Long> external : result.externalDemand()
            .entrySet()) {
            if (external.getValue() > 0) ensure((K) external.getKey(), external.getValue(), branch, active);
        }
        // Replay imports and seed from the actual snapshot, never the solver's hypothetical marking.
        for (cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.PatternRun run : result.executionPlan()) {
            check();
            ECORecipe<K, P> recipe = (ECORecipe<K, P>) run.pattern()
                .details();
            fire(recipe, run.count(), branch, active);
        }
        if (get(branch.stock, key) < amount) throw new Failure(Status.CYCLE_UNRESOLVED, key, amount);
        state.take(branch);
        return true;
    }

    private void fire(ECORecipe<K, P> recipe, long batches, State state, Set<K> active) {
        for (Map.Entry<K, Long> input : recipe.inputs.entrySet()) {
            long required = Math.multiplyExact(input.getValue(), batches);
            ensure(input.getKey(), required, state, active);
            consume(input.getKey(), required, state);
        }
        for (Map.Entry<K, Long> output : recipe.outputs.entrySet()) {
            add(state.stock, output.getKey(), Math.multiplyExact(output.getValue(), batches));
        }
        state.steps.add(new Step<>(recipe, batches));
    }

    private boolean singleFeedback(ECORecipe<K, P> recipe, K feedback) {
        for (K input : recipe.inputs.keySet()) {
            if (!input.equals(feedback) && recipe.outputs.containsKey(input)) return false;
        }
        return true;
    }

    private void fireGrowth(ECORecipe<K, P> recipe, K feedback, long batches, State state, Set<K> active) {
        long seed = recipe.inputs.get(feedback);
        // Keep the startup seed out of dependency planning until this phase can run.
        state.stock.put(feedback, get(state.stock, feedback) - seed);
        long retainedOriginal = Math.min(seed, get(state.original, feedback));
        state.original.put(feedback, get(state.original, feedback) - retainedOriginal);
        add(state.extracted, feedback, retainedOriginal);
        for (Map.Entry<K, Long> input : recipe.inputs.entrySet()) {
            if (input.getKey()
                .equals(feedback)) continue;
            long required = Math.multiplyExact(input.getValue(), batches);
            ensure(input.getKey(), required, state, active);
            consume(input.getKey(), required, state);
        }
        for (Map.Entry<K, Long> output : recipe.outputs.entrySet()) {
            // AE2 must represent gross production, not just the net growth.
            Math.multiplyExact(output.getValue(), batches);
            long delta = output.getValue() - (output.getKey()
                .equals(feedback) ? seed : 0L);
            add(state.stock, output.getKey(), Math.multiplyExact(delta, batches));
        }
        add(state.stock, feedback, seed);
        state.steps.add(new Step<>(recipe, batches, true));
    }

    private void consume(K key, long amount, State state) {
        long available = get(state.stock, key);
        if (available < amount) throw new Failure(Status.MISSING_ITEMS, key, amount - available);
        state.stock.put(key, available - amount);
        long fromNetwork = Math.min(amount, get(state.original, key));
        if (fromNetwork > 0) {
            state.original.put(key, get(state.original, key) - fromNetwork);
            add(state.extracted, key, fromNetwork);
        }
    }

    private void validate(K goal, long amount, State state) {
        Map<K, Long> material = new LinkedHashMap<>(state.extracted);
        for (Step<K, P> step : state.steps) {
            check();
            if (step.sequential) {
                for (Map.Entry<K, Long> input : step.recipe.inputs.entrySet()) {
                    long produced = get(step.recipe.outputs, input.getKey());
                    long required = produced >= input.getValue() ? input.getValue()
                        : Math.addExact(
                            input.getValue(),
                            Math.multiplyExact(input.getValue() - produced, step.crafts - 1));
                    if (get(material, input.getKey()) < required)
                        throw new Failure(Status.CYCLE_UNRESOLVED, input.getKey(), required);
                }
                Set<K> keys = new HashSet<>(step.recipe.inputs.keySet());
                keys.addAll(step.recipe.outputs.keySet());
                for (K key : keys) add(
                    material,
                    key,
                    Math.multiplyExact(get(step.recipe.outputs, key) - get(step.recipe.inputs, key), step.crafts));
                continue;
            }
            for (Map.Entry<K, Long> input : step.recipe.inputs.entrySet()) {
                long required = Math.multiplyExact(input.getValue(), step.crafts);
                long present = get(material, input.getKey());
                if (present < required) throw new Failure(Status.CYCLE_UNRESOLVED, input.getKey(), required - present);
                material.put(input.getKey(), present - required);
            }
            for (Map.Entry<K, Long> output : step.recipe.outputs.entrySet()) {
                add(material, output.getKey(), Math.multiplyExact(output.getValue(), step.crafts));
            }
        }
        if (get(material, goal) < amount) throw new Failure(Status.MISSING_ITEMS, goal, amount - get(material, goal));
    }

    private static <K> long get(Map<K, Long> values, K key) {
        Long value = values.get(key);
        return value == null ? 0 : value;
    }

    private static <K> void add(Map<K, Long> values, K key, long amount) {
        values.put(key, Math.addExact(get(values, key), amount));
    }

    private final class State {

        private Map<K, Long> stock;
        private Map<K, Long> original;
        private Map<K, Long> extracted;
        private List<Step<K, P>> steps;

        State(Map<K, Long> inventory) {
            stock = new LinkedHashMap<>();
            inventory.forEach((key, value) -> {
                if (value == null || value < 0) throw new IllegalArgumentException("Invalid inventory amount");
                if (value > 0) stock.put(key, value);
            });
            original = new LinkedHashMap<>(stock);
            extracted = new LinkedHashMap<>();
            steps = new ArrayList<>();
        }

        State(State other) {
            stock = new LinkedHashMap<>(other.stock);
            original = new LinkedHashMap<>(other.original);
            extracted = new LinkedHashMap<>(other.extracted);
            steps = new ArrayList<>(other.steps);
        }

        void take(State other) {
            stock = other.stock;
            original = other.original;
            extracted = other.extracted;
            steps = other.steps;
        }
    }

    private static final class Failure extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final Status status;
        private final Object key;
        private final long amount;

        Failure(Status status, Object key, long amount) {
            super(status.name(), null, false, false);
            this.status = status;
            this.key = key;
            this.amount = amount;
        }
    }
}
