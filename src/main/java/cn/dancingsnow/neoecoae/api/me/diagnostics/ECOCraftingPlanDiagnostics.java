package cn.dancingsnow.neoecoae.api.me.diagnostics;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;

/** Duck interface mixed into AE2 CraftingPlan so structured planner results survive the Future boundary. */
public interface ECOCraftingPlanDiagnostics {

    @Nullable
    ECOPlanningResult neoecoae$getPlanningResult();

    void neoecoae$setPlanningResult(@Nullable ECOPlanningResult result);
}
