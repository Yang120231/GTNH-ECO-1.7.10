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

    public ECOPlanningResult<K, P> plan(K goal, long amount, Map<K, Long> inventory) {
        if (goal == null || amount <= 0) throw new IllegalArgumentException("Invalid crafting request");
        visited = 0;
        started = System.nanoTime();
        State state = new State(inventory);
        try {
            ensure(goal, amount, state, new HashSet<K>());
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
            List<ECORecipe<K, P>> candidates = producers.get(key);
            if (candidates == null) throw new Failure(Status.MISSING_ITEMS, key, amount - get(state.stock, key));
            Failure last = new Failure(Status.CYCLE_UNRESOLVED, key, amount - get(state.stock, key));
            for (ECORecipe<K, P> recipe : candidates) {
                State branch = new State(state);
                try {
                    while (get(branch.stock, key) < amount) {
                        check();
                        long before = get(branch.stock, key);
                        long produced = recipe.outputs.get(key);
                        long retained = get(recipe.inputs, key);
                        long net = produced - retained;
                        if (net <= 0) throw new Failure(Status.CYCLE_UNRESOLVED, key, amount - before);
                        long needed = amount - before;
                        long batches = needed / net + (needed % net == 0 ? 0 : 1);
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
