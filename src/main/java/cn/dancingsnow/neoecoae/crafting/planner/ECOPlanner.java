package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legacy item-only facade; all callers now use the same validated planning kernel. */
public final class ECOPlanner {

    private ECOPlanner() {}

    public static ECOPlan plan(ECOItemKey goal, long amount, Map<ECOItemKey, Long> stock,
        Map<ECOItemKey, ECOPlannerRecipe> recipes) {
        if (goal == null || amount <= 0) throw new IllegalArgumentException("Invalid crafting request");
        List<ECORecipe<ECOItemKey, ECOItemKey>> compiled = new ArrayList<>();
        if (recipes != null) {
            for (ECOPlannerRecipe recipe : recipes.values()) {
                compiled.add(
                    new ECORecipe<>(
                        recipe.output(),
                        recipe.inputs(),
                        Collections.singletonMap(recipe.output(), recipe.outputAmount())));
            }
        }
        ECOPlanningResult<ECOItemKey, ECOItemKey> result = new ECOPlanningEngine<>(compiled, () -> false, 32768)
            .plan(goal, amount, stock == null ? Collections.emptyMap() : stock);
        Map<ECOItemKey, Long> crafts = new LinkedHashMap<>();
        for (ECOPlanningResult.Step<ECOItemKey, ECOItemKey> step : result.schedule)
            crafts.put(step.recipe.token, Math.addExact(crafts.getOrDefault(step.recipe.token, 0L), step.crafts));
        return new ECOPlan(result.status == ECOPlanningResult.Status.SUCCESS, crafts, result.missing);
    }
}
