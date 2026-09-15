package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionPlan;

/** Submitted-task binding from ExecutingCraftingJob, with GTNH progress read at the API boundary. */
public final class ECOExecutionBinding {

    private final Map<Integer, ICraftingPatternDetails> patterns = new LinkedHashMap<>();
    private final ECOExecutionRuntime.TaskProgress[] progress;
    private final ECOExecutionPlan plan;
    private final ToLongFunction<ICraftingPatternDetails> liveRemaining;

    public ECOExecutionBinding(ECOExecutionPlan plan, Iterable<ICraftingPatternDetails> submitted,
        ToLongFunction<ICraftingPatternDetails> liveRemaining, boolean validateTotals,
        ECOExecutionPlanCodec.Codec codec) {
        this.plan = plan;
        this.liveRemaining = liveRemaining;
        this.progress = new ECOExecutionRuntime.TaskProgress[plan.tasks()
            .size()];
        Set<ICraftingPatternDetails> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var task : plan.tasks()) {
            ICraftingPatternDetails match = null;
            for (ICraftingPatternDetails candidate : submitted) {
                if (!used.contains(candidate) && codec.identity(candidate)
                    .equals(task.identity())) {
                    if (match != null) throw new IllegalArgumentException("Ambiguous submitted physical pattern");
                    match = candidate;
                }
            }
            if (match == null) throw new IllegalArgumentException("Execution plan task is absent: " + task.id());
            long remaining = liveRemaining.applyAsLong(match);
            if (remaining < 0 || remaining > task.totalCount() || (validateTotals && remaining != task.totalCount()))
                throw new IllegalArgumentException("Execution task count differs: " + task.id());
            patterns.put(task.id(), match);
            progress[task.id()] = new ECOExecutionRuntime.TaskProgress(remaining);
            used.add(match);
        }
    }

    public Map<Integer, ICraftingPatternDetails> patterns() {
        return Collections.unmodifiableMap(patterns);
    }

    public ECOExecutionRuntime.TaskProgress[] progress() {
        return progress.clone();
    }

    /** Call before candidate selection and after AE2 commits its accepted task decrement. */
    public void refresh() {
        for (var task : plan.tasks()) {
            long remaining = liveRemaining.applyAsLong(patterns.get(task.id()));
            if (remaining < 0 || remaining > task.totalCount())
                throw new IllegalArgumentException("Invalid live execution count");
            progress[task.id()].value = remaining;
        }
    }
}
