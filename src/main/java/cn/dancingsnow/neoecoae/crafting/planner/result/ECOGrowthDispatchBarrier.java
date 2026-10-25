package cn.dancingsnow.neoecoae.crafting.planner.result;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Keeps downstream consumers behind compact growth waves, without blocking the growth recipe's suppliers. */
public final class ECOGrowthDispatchBarrier {

    private final Map<IPatternDetails, Set<IPatternDetails>> blockers = new HashMap<>();

    public ECOGrowthDispatchBarrier(Collection<IPatternDetails> patterns) {
        // A lone recipe has no downstream competitor; preserve its provider-first fast path unchanged.
        if (patterns.size() < 2) return;
        Map<IPatternDetails, Set<AEKey>> inputs = new HashMap<>();
        Map<AEKey, Set<IPatternDetails>> producers = new HashMap<>();
        for (var pattern : patterns) {
            var keys = new HashSet<AEKey>();
            for (var input : pattern.getInputs()) {
                for (var stack : input.getPossibleInputs()) keys.add(stack.what());
            }
            inputs.put(pattern, keys);
            for (var output : pattern.getOutputs()) {
                producers.computeIfAbsent(output.what(), ignored -> new HashSet<>())
                    .add(pattern);
            }
            for (var input : pattern.getInputs()) {
                for (var stack : input.getPossibleInputs()) {
                    var remainder = input.getRemainingKey(stack.what());
                    if (remainder != null) {
                        producers.computeIfAbsent(remainder, ignored -> new HashSet<>())
                            .add(pattern);
                    }
                }
            }
        }
        for (var grow : patterns) {
            var feedback = new HashSet<AEKey>();
            for (var key : inputs.get(grow)) {
                if (ECOPhaseScheduler.growingPatternFeedbackReserveExact(grow, 1L, key)
                    .signum() > 0) {
                    feedback.add(key);
                }
            }
            if (feedback.isEmpty()) continue;

            // Walk upstream once per growth pattern. A supplier may itself consume feedback; imposing a
            // completion barrier on that path would turn a runnable dependency cycle into a deadlock.
            var suppliers = new HashSet<IPatternDetails>();
            var pending = new ArrayDeque<IPatternDetails>();
            suppliers.add(grow);
            pending.add(grow);
            while (!pending.isEmpty()) {
                for (var key : inputs.get(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.removeFirst(pending))) {
                    for (var producer : producers
                        .getOrDefault(key, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf())) {
                        if (suppliers.add(producer)) pending.addLast(producer);
                    }
                }
            }
            for (var consumer : patterns) {
                if (suppliers.contains(consumer)) continue;
                if (inputs.get(consumer)
                    .stream()
                    .anyMatch(feedback::contains)) {
                    blockers.computeIfAbsent(consumer, ignored -> new HashSet<>())
                        .add(grow);
                }
            }
        }
    }

    public boolean canDispatch(IPatternDetails pattern, ToLongFunction<IPatternDetails> remainingTasks) {
        for (var grow : blockers.getOrDefault(pattern, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf())) {
            if (remainingTasks.applyAsLong(grow) > 0L) return false;
        }
        // Once every growth firing is accepted, ordinary physical input extraction handles delayed returns.
        return true;
    }
}
