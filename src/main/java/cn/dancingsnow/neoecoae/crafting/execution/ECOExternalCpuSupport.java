package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionContract;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;

/**
 * Ordinary ECO DAGs can use native AE2 CPUs; cycles and exact orders still require the ECO runtime.
 * Ported from the 1.21 baseline. On 1.7.10 a native CPU can only push native AE2 patterns, so every
 * planned pattern must also be a captured {@link NativePatternDetails}.
 */
public final class ECOExternalCpuSupport {

    private ECOExternalCpuSupport() {}

    public static boolean supportsPlan(ICraftingPlan plan) {
        if (plan == null || plan.simulation()) return false;
        if (plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan) return false;
        for (IPatternDetails pattern : plan.patternTimes()
            .keySet()) if (!(pattern instanceof NativePatternDetails)) return false;
        if (!ECOPlanningResultRegistry.isECOOwnedPlan(plan)) return true;
        var result = ECOPlanningResultRegistry.find(plan);
        if (result == null || !fitsLong(result)) return false;
        var contract = ECOPlanningResultRegistry.resolveContract(plan, null);
        if (!supportsContract(result, contract)) return false;
        // Native CPUs cannot execute the explicit item-ID override captured by an ECO processing plan.
        for (IPatternDetails pattern : plan.patternTimes()
            .keySet()) {
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible != null && possible.length > 0
                    && possible[0] != null
                    && ECOProcessingComponentMatching
                        .enabled(pattern, possible[0].what(), contract.fuzzyPlanningItemIds()))
                    return false;
            }
        }
        return true;
    }

    private static boolean fitsLong(ECOPlanningResult result) {
        for (var amounts : java.util.Arrays.asList(
            result.exactPatternTimes()
                .values(),
            result.exactUsedItems()
                .values(),
            result.exactEmittedItems()
                .values(),
            result.exactMissingItems()
                .values()))
            for (PlannerAmount amount : amounts) if (!amount.fitsLong()) return false;
        return true;
    }

    private static boolean supportsContract(ECOPlanningResult result, ECOExecutionContract contract) {
        if (contract == null) return false;
        if (contract.mode() == ExecutionMode.NATIVE) return true;
        if (contract.mode() != ExecutionMode.PHASED_DAG
            || result.executionRequirement() != ECOExecutionRequirement.NONE) return false;

        // ECO also builds phases for ordinary DAGs. Keep that metadata for ECO CPUs without
        // requiring a native CPU to run our scheduler when AE2's normal task vector suffices.
        var execution = contract.executionPlan();
        if (execution == null || execution.mode() != ExecutionMode.PHASED_DAG
            || execution.tasks()
                .isEmpty())
            return false;
        for (var phase : execution.phases()) if (phase.type() != ECOExecutionSchedule.Type.DAG) return false;
        for (var task : execution.tasks())
            if (task.kind() != ECOExecutionPlan.TaskKind.DAG || !usesNativeInputs(task)) return false;
        return true;
    }

    private static boolean usesNativeInputs(ECOExecutionPlan.TaskSpec task) {
        var inputs = task.pattern()
            .getInputs();
        if (inputs == null) return false;
        for (var allocation : task.inputAllocations()) {
            if (allocation.slot() >= inputs.length || inputs[allocation.slot()] == null
                || allocation.runs()
                    .size() != 1)
                return false;
            var input = inputs[allocation.slot()];
            var possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null) return false;
            var run = allocation.runs()
                .get(0);
            // Per-craft input choices or nonstandard amounts need ECO's input resolver.
            if (!run.key()
                .equals(possible[0].what()) || run.crafts() != task.totalCount()) return false;
            try {
                if (run.amount() != Math.multiplyExact(possible[0].amount(), input.getMultiplier())) return false;
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
        return true;
    }
}
